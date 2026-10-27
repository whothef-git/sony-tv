package tv.widgetv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Ambient Backlight Glow behind apps & Light-Up Underline below app name:
 * - A continuous soft backlight, drawn below the opaque app artwork.
 * - No transparent rectangular cutout: the light stays filled while crossing gaps between apps.
 * - A crisp underline below the active app name.
 */
public final class GlassHighlight extends View {
    static final long FOCUS_DURATION_MS = 220;
    private int ambientColor = 0xFFFFD56B;
    private int targetAmbientColor = ambientColor;
    private final float[] fromHsv = new float[3];
    private final float[] toHsv = new float[3];
    private final float[] curHsv = new float[3];
    private final ValueAnimator colorAnim = ValueAnimator.ofFloat(0f, 1f);
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private final RectF cur = new RectF();
    private final RectF edge = new RectF();
    private final RectF glowDst = new RectF();

    private final RectF fromLine = new RectF();
    private final RectF toLine = new RectF();
    private final RectF curLine = new RectF();

    private float fromR, toR, curR;
    private boolean shown;
    private boolean isDockTarget = false;

    // Smooth Backlight Glow Bitmap & Paint
    private Bitmap glowBitmap = null;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float glowPad = 0f;

    // Sharp Underline Paint (No shadow, no glow)
    private final Paint lineCore = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Timeline Ring Paint
    private final Paint goldRing = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean visible = true;
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);

    public GlassHighlight(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        glowPad = 48f * d; // Broad ambient spill beyond all four sides of the app.

        // A filled glow is safe here because this view is behind the app artwork.
        int cardW = Math.round(102f * d); // ~205px
        int cardH = Math.round(58f * d);  // ~115px
        glowBitmap = createGlowBitmap(cardW, cardH, Math.round(glowPad), 0xD0FFD56B);

        // Sharp Underline (Solid gold, crisp 90-degree edges, zero shadow, zero glow)
        lineCore.setStyle(Paint.Style.FILL);
        lineCore.setColor(0xFFFFD56B);

        // Timeline Ring
        goldRing.setStyle(Paint.Style.STROKE);
        goldRing.setStrokeWidth(2f * d);
        goldRing.setColor(0xFFD8B46A);

        colorAnim.setDuration(FOCUS_DURATION_MS);
        colorAnim.setInterpolator(new DecelerateInterpolator());
        colorAnim.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            float hueDelta = (toHsv[0] - fromHsv[0] + 540f) % 360f - 180f;
            curHsv[0] = (fromHsv[0] + hueDelta * t + 360f) % 360f;
            curHsv[1] = lerp(fromHsv[1], toHsv[1], t);
            curHsv[2] = lerp(fromHsv[2], toHsv[2], t);
            ambientColor = Color.HSVToColor(curHsv);
            applyAmbientColor();
            invalidate();
        });
        applyAmbientColor();
        anim.setDuration(FOCUS_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            cur.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            curR = Math.max(0f, lerp(fromR, toR, t));

            curLine.set(lerp(fromLine.left, toLine.left, t), lerp(fromLine.top, toLine.top, t),
                    lerp(fromLine.right, toLine.right, t), lerp(fromLine.bottom, toLine.bottom, t));

            invalidate();
        });
    }

    /** Soft elliptical light with no rectangular hole or sharp inner boundary. */
    private static Bitmap createGlowBitmap(int cardW, int cardH, int pad, int glowColor) {
        int totalW = cardW + pad * 2;
        int totalH = cardH + pad * 2;
        int[] pixels = new int[totalW * totalH];

        int r = (glowColor >> 16) & 0xFF;
        int g = (glowColor >> 8) & 0xFF;
        int b = glowColor & 0xFF;
        int maxAlpha = (glowColor >>> 24) & 0xFF;

        for (int y = 0; y < totalH; y++) {
            for (int x = 0; x < totalW; x++) {
                float nx = (2f * x - (totalW - 1)) / (totalW - 1);
                float ny = (2f * y - (totalH - 1)) / (totalH - 1);
                float distance = (float) Math.sqrt(nx * nx + ny * ny);
                if (distance >= 1f) continue;
                // Zero slope at the center and outer edge; no dark center is exposed in transit.
                // Broad bright core with a feathered outer edge. Squaring distance keeps
                // light visible beyond the artwork without adding a rectangular outline.
                float factor = 0.5f * (1f + (float) Math.cos(Math.PI * distance * distance));
                int alpha = Math.round(maxAlpha * factor);
                pixels[y * totalW + x] = (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return Bitmap.createBitmap(pixels, totalW, totalH, Bitmap.Config.ARGB_8888);
    }

    /** Move highlight to target card or control. */
    void moveTo(RectF target, float radius, boolean isDock, boolean animate) {
        float d = getContext().getResources().getDisplayMetrics().density;
        float lineW = 44f * d;  // ~88px wide
        float lineH = 3f * d;   // 6px height crisp sharp bar
        float lineX = target.centerX() - lineW / 2f;
        float lineY = target.bottom + 34f * d; // Directly beneath app name text
        RectF newLine = new RectF(lineX, lineY, lineX + lineW, lineY + lineH);

        if (shown && target.equals(to) && radius == toR && isDockTarget == isDock) return;
        anim.cancel();
        isDockTarget = isDock;
        to.set(target);
        toR = radius;
        toLine.set(newLine);

        if (!shown || !animate) {
            cur.set(target);
            curR = radius;
            curLine.set(newLine);
            shown = true;
            invalidate();
            return;
        }
        from.set(cur);
        fromR = curR;
        fromLine.set(curLine);
        anim.start();
    }

    void moveTo(RectF target, float radius, boolean animate) {
        moveTo(target, radius, false, animate);
    }

    void setAmbientColor(int color, boolean animate) {
        if (color == targetAmbientColor) return;
        colorAnim.cancel();
        targetAmbientColor = color;
        if (!animate || !isAttachedToWindow()) {
            ambientColor = color;
            applyAmbientColor();
            invalidate();
            return;
        }
        // RGB blends between complementary app colors pass through gray/white.
        // HSV preserves saturation and brightness while following the shortest hue arc.
        Color.colorToHSV(ambientColor, fromHsv);
        Color.colorToHSV(color, toHsv);
        if (fromHsv[1] < 0.01f) fromHsv[0] = toHsv[0];
        if (toHsv[1] < 0.01f) toHsv[0] = fromHsv[0];
        colorAnim.start();
    }

    private void applyAmbientColor() {
        bitmapPaint.setColorFilter(new PorterDuffColorFilter(ambientColor, PorterDuff.Mode.SRC_IN));
        lineCore.setColor(ambientColor);
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        colorAnim.cancel();
        ambientColor = targetAmbientColor;
        applyAmbientColor();
        super.onDetachedFromWindow();
    }

    void setRingVisible(boolean on) {
        if (on == visible) return;
        visible = on;
        animate().cancel();
        animate().alpha(on ? 1f : 0f).setDuration(160).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!shown) return;
        float d = getContext().getResources().getDisplayMetrics().density;

        if (isDockTarget) {
            // Continuous light below the artwork, including between adjacent cards.
            if (glowBitmap != null) {
                glowDst.set(cur.left - glowPad, cur.top - glowPad, cur.right + glowPad, cur.bottom + glowPad);
                canvas.drawBitmap(glowBitmap, null, glowDst, bitmapPaint);
            }

            // Sharp rectangular underline directly below app name (no shadow, no glow)
            if (curLine.width() > 0) {
                canvas.drawRect(curLine, lineCore);
            }
        } else if (curR <= 0.1f) {
            // Grid cards: subtle ambient glow
            if (glowBitmap != null) {
                glowDst.set(cur.left - glowPad * 0.7f, cur.top - glowPad * 0.7f,
                            cur.right + glowPad * 0.7f, cur.bottom + glowPad * 0.7f);
                canvas.drawBitmap(glowBitmap, null, glowDst, bitmapPaint);
            }
        } else {
            // Timeline: rounded gold ring
            edge.set(cur);
            edge.inset(-d, -d);
            canvas.drawRoundRect(edge, curR + d, curR + d, goldRing);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
