package tv.widgetv.home;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** MainActivity owns D-pad navigation; changing focus changes the page. */
public final class ScreenTabs extends View {
    static final String[] LABELS = {"Home", "Prayer Time", "Weather"};
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private int selected;
    private boolean focused;
    private float indicatorLeft, indicatorRight, fromLeft, fromRight, toLeft, toRight;
    private boolean placed;
    private final ValueAnimator indicator = ValueAnimator.ofFloat(0f, 1f);
    public ScreenTabs(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        text.setTypeface(android.graphics.Typeface.create(getResources().getFont(R.font.outfit), 400, false));
        text.setFontVariationSettings("'wght' 400");
        text.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        indicator.setDuration(220);
        indicator.setInterpolator(new DecelerateInterpolator());
        indicator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            indicatorLeft = fromLeft + (toLeft-fromLeft)*t;
            indicatorRight = fromRight + (toRight-fromRight)*t;
            invalidate();
        });
        setState(0, false);
    }
    void setState(int selected, boolean focused) {
        boolean changed = this.selected != selected;
        this.selected = selected; this.focused = focused;
        if (changed || !placed) {
            float left = 0;
            for (int i=0;i<selected;i++) left += text.measureText(LABELS[i])+30*d;
            indicator.cancel(); toLeft=left; toRight=left+text.measureText(LABELS[selected]);
            if (!placed || !isAttachedToWindow()) { indicatorLeft=toLeft; indicatorRight=toRight; placed=true; }
            else { fromLeft=indicatorLeft; fromRight=indicatorRight; indicator.start(); }
        }
        setContentDescription("Screens. " + LABELS[selected] + " selected. " +
                (selected == 1 ? "SHAH ALAM · SGR01. " : "") + "Left or right to switch; down for apps.");
        invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        float x = 0;
        line.setColor(0x303A4B64);
        c.drawRect(0, getHeight() - d, getWidth(), getHeight(), line);
        for (int i = 0; i < LABELS.length; i++) {
            float width = text.measureText(LABELS[i]);
            text.setColor(i == selected ? Lapis.PLASTER_100 : Lapis.MIST_400);
            c.drawText(LABELS[i], x, 23*d, text);
            x += width + 30*d;
        }
        line.setColor(focused ? Lapis.PLASTER_100 : 0xFF9EADC3);
        c.drawRoundRect(indicatorLeft,getHeight()-2*d,indicatorRight,getHeight(),d,d,line);
        text.setColor(Lapis.MIST_400); text.setTextSize(10*d);
        if (selected == 1) {
            String label = "SHAH ALAM · SGR01";
            c.drawText(label, getWidth() - text.measureText(label), 23*d, text);
        }
        text.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
    }
    @Override protected void onDetachedFromWindow() { indicator.cancel(); super.onDetachedFromWindow(); }
}
