package tv.widgetv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * Full-screen wallpaper: a vertical sky gradient that follows the prayer period, stars at night, a gold
 * geometric pattern that fades out from the top, a lapis scrim over the header, and a dim layer that
 * fades in as the page scrolls to the grid. It only redraws when told to (minute tick, scroll, debug
 * hook): no animation loop. The colours crossfade over 20 minutes after each boundary, one step per minute.
 */
public final class SkyView extends View {
    private static final String TAG = Config.TAG;
    static final String[] PERIODS = {"dawn", "morning", "midday", "golden", "sunset", "night"};
    /** Index of the period each boundary (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) starts. */
    private static final int[][] SKY = {
            {0xFF0E1A3D, 0xFF3B3A6B, 0xFFC98A8A}, // dawn: Fajr → Sunrise
            {0xFF0F2A52, 0xFF1F5585, 0xFF6FA0C4}, // morning: Sunrise → Dhuhr
            {0xFF123A6B, 0xFF2C6EA3, 0xFF9CC3DD}, // midday: Dhuhr → Asr
            {0xFF1C3557, 0xFF7A6A4A, 0xFFD8B46A}, // golden: Asr → Maghrib
            {0xFF2A1838, 0xFF8A2F3A, 0xFFE08A4A}, // sunset: Maghrib → Isha
            {0xFF05070F, 0xFF08101F, 0xFF0C1B33}, // night: Isha → Fajr
    };
    private static final int NIGHT = 5;
    private static final long FADE_MS = 20 * 60_000L;
    private static final float PATTERN_ALPHA = 0.13f, PATTERN_FADE_END = 0.58f;
    /** Scrim stops (fraction of height → lapis-900 alpha); keeps header text ≥ 4.5:1 on every sky. */
    private static final float[] SCRIM_POS = {0f, 0.30f, 0.50f, 0.68f};
    private static final float[] SCRIM_ALPHA = {0.90f, 0.82f, 0.66f, 0f};

    private final Paint sky = new Paint(), stars = new Paint(Paint.ANTI_ALIAS_FLAG), pattern = new Paint(),
            scrim = new Paint(), dim = new Paint();
    private final float[] starXY = new float[40 * 3];
    private int[] colors = SKY[NIGHT].clone();
    private float nightWeight = 1f, dimFraction;
    private int patternIndex = -1;
    private Bitmap tile;
    private boolean rotate;

    /** Debug overrides (adb broadcast in debug builds); -1 means follow the clock / the config. */
    int forcedPeriod = -1, forcedPattern = -1;

    public SkyView(Context c, AttributeSet a) {
        super(c, a);
        rotate = "rotate".equals(c.getString(R.string.pattern_mode));
        Random r = new Random(1448);
        for (int i = 0; i < 40; i++) {
            starXY[i * 3] = r.nextFloat();
            starXY[i * 3 + 1] = r.nextFloat() * 0.7f;
            starXY[i * 3 + 2] = 0.6f + r.nextFloat() * 0.9f; // radius in dp
        }
        stars.setColor(Lapis.PLASTER_100);
        dim.setColor(Lapis.LAPIS_900);
    }

    /** Called on every minute tick with the day's prayer times. */
    void update(PrayerDay day) {
        long now = day.now;
        int period, prev;
        float t;
        if (forcedPeriod >= 0) {
            period = prev = forcedPeriod;
            t = 1f;
        } else {
            long start = day.yesterdayIsha;
            period = NIGHT;
            for (int i = 0; i < 6; i++) {
                if (day.times[i] <= now) {
                    period = i;
                    start = day.times[i];
                }
            }
            prev = (period + 5) % 6;
            t = Math.min(1f, (now - start) / (float) FADE_MS);
        }
        int[] next = new int[3];
        for (int i = 0; i < 3; i++) next[i] = lerpColor(SKY[prev][i], SKY[period][i], t);
        nightWeight = (period == NIGHT ? t : 0f) + (prev == NIGHT && period != NIGHT ? 1f - t : 0f);

        int p = forcedPattern >= 0 ? forcedPattern : rotate ? dayOfYear() % 4 : Patterns.ZELLIGE;
        boolean changed = !java.util.Arrays.equals(next, colors) || p != patternIndex;
        colors = next;
        if (p != patternIndex || tile == null) {
            patternIndex = p;
            if (tile != null) tile.recycle();
            tile = Patterns.tile(p, getResources().getDisplayMetrics().density);
            pattern.setShader(null);
        }
        if (changed) {
            Log.i(TAG, "sky: period=" + PERIODS[period] + (t < 1f ? " (from " + PERIODS[prev] + ", " + Math.round(t * 100) + "%)" : "")
                    + " pattern=" + Patterns.NAMES[p] + " tile=" + tile.getWidth() + "x" + tile.getHeight());
            buildShaders();
            invalidate();
        }
    }

    /** 0 at the top of the page, 1 once the grid fills the screen. */
    void setDim(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f == dimFraction) return;
        dimFraction = f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        buildShaders();
    }

    private void buildShaders() {
        int h = getHeight();
        if (h == 0) return;
        sky.setShader(new LinearGradient(0, 0, 0, h, colors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        int[] scrimColors = new int[SCRIM_ALPHA.length];
        float[] scrimPos = new float[SCRIM_POS.length];
        for (int i = 0; i < scrimColors.length; i++) {
            scrimColors[i] = Lapis.alpha(Lapis.LAPIS_900, SCRIM_ALPHA[i]);
            scrimPos[i] = SCRIM_POS[i];
        }
        scrim.setShader(new LinearGradient(0, 0, 0, h, scrimColors, scrimPos, Shader.TileMode.CLAMP));
        if (tile != null) {
            // Pattern masked by a top-down alpha ramp (DST_IN keeps the tile where the ramp is opaque).
            Shader tiles = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Shader fade = new LinearGradient(0, 0, 0, h * PATTERN_FADE_END, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            pattern.setShader(new ComposeShader(tiles, fade, PorterDuff.Mode.DST_IN));
            pattern.setAlpha(Math.round(PATTERN_ALPHA * 255));
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, sky);
        if (nightWeight > 0f) {
            float d = getResources().getDisplayMetrics().density;
            for (int i = 0; i < 40; i++) {
                stars.setAlpha(Math.round(255 * nightWeight * (0.35f + 0.4f * ((i * 7) % 10) / 10f)));
                c.drawCircle(starXY[i * 3] * w, starXY[i * 3 + 1] * h, starXY[i * 3 + 2] * d, stars);
            }
        }
        c.drawRect(0, 0, w, h, scrim);
        // Pattern above the scrim, so it is at full strength at the top edge and fades out by ~58%.
        if (pattern.getShader() != null) c.drawRect(0, 0, w, h * PATTERN_FADE_END, pattern);
        if (dimFraction > 0f) {
            dim.setAlpha(Math.round(255 * 0.78f * dimFraction));
            c.drawRect(0, 0, w, h, dim);
        }
    }

    private static int dayOfYear() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
