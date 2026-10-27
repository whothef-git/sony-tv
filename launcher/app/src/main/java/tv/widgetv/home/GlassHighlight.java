package tv.widgetv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Ambient Backlight Glow behind apps & Light-Up Underline below app name:
 * - Generates a mathematically smooth cosine/Gaussian backlight glow bitmap.
 * - Inside the card boundary: 100% transparent (0 alpha, never inside the app).
 * - Outside the card: ultra-smooth radiant amber-gold light falloff into the background (zero box, zero hard lines).
 * - Illuminated gold underline bar with multi-layer soft bloom directly below the active app name.
 */
public final class GlassHighlight extends View {
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
        glowPad = 26f * d; // 52px halo spread

        // Generate smooth continuous ambient glow (zero hard lines, 0 alpha inside card)
        int cardW = Math.round(102f * d); // ~205px
        int cardH = Math.round(58f * d);  // ~115px
        glowBitmap = createGlowBitmap(cardW, cardH, Math.round(glowPad), 0x95FFD56B);

        // Sharp Underline (Solid gold, crisp 90-degree edges, zero shadow, zero glow)
        lineCore.setStyle(Paint.Style.FILL);
        lineCore.setColor(0xFFFFD56B);

        // Timeline Ring
        goldRing.setStyle(Paint.Style.STROKE);
        goldRing.setStrokeWidth(2f * d);
        goldRing.setColor(0xFFD8B46A);

        anim.setDuration(260);
        anim.setInterpolator(new OvershootInterpolator(1.1f));
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

    /** Pre-computes smooth cosine light decay behind the card boundary. */
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
                int dx = 0;
                if (x < pad) dx = pad - x;
                else if (x >= pad + cardW) dx = x - (pad + cardW);

                int dy = 0;
                if (y < pad) dy = pad - y;
                else if (y >= pad + cardH) dy = y - (pad + cardH);

                if (dx == 0 && dy == 0) {
                    // Inside the card -> completely transparent (0 alpha, never inside the app!)
                    pixels[y * totalW + x] = 0;
                } else {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist >= pad) {
                        pixels[y * totalW + x] = 0;
                    } else {
                        // Smooth S-curve cosine falloff from 1.0 down to 0.0
                        float t = dist / pad;
                        float factor = 0.5f * (1.0f + (float) Math.cos(Math.PI * t));
                        int a = (int) (maxAlpha * factor);
                        pixels[y * totalW + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, totalW, totalH, Bitmap.Config.ARGB_8888);
    }

    /** Move highlight to target card or control. */
    void moveTo(RectF target, float radius, boolean isDock, boolean animate) {
        isDockTarget = isDock;
        float d = getContext().getResources().getDisplayMetrics().density;
        float lineW = 44f * d;  // ~88px wide
        float lineH = 3f * d;   // 6px height crisp sharp bar
        float lineX = target.centerX() - lineW / 2f;
        float lineY = target.bottom + 34f * d; // Directly beneath app name text
        RectF newLine = new RectF(lineX, lineY, lineX + lineW, lineY + lineH);

        if (shown && target.equals(to) && radius == toR && isDockTarget == isDock) return;
        anim.cancel();
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
            // Ambient Backlight Glow strictly BEHIND the apps (smooth Gaussian falloff, 0 alpha inside card)
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
