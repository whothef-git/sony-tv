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
    static final String[] LABELS = {"Home", "Prayer Time", "Weather", "Smart Home"};
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int FOCUS_GOLD = Lapis.GOLD_400;
    private final float d;
    private int selected;
    private boolean focused;
    private float indicatorLeft, indicatorRight, fromLeft, fromRight, toLeft, toRight;
    private boolean placed;
    private final ValueAnimator indicator = ValueAnimator.ofFloat(0f, 1f);
    private final ValueAnimator focusFade = new ValueAnimator();
    private float focusAmount;
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
        focusFade.setDuration(220);
        focusFade.setInterpolator(new DecelerateInterpolator());
        focusFade.addUpdateListener(animation -> { focusAmount=(float)animation.getAnimatedValue();invalidate(); });
        setState(0, false);
    }
    void setState(int selected, boolean focused) {
        boolean changed = this.selected != selected;
        if(this.focused!=focused){
            focusFade.cancel();
            if(!isAttachedToWindow())focusAmount=focused?1f:0f;
            else {focusFade.setFloatValues(focusAmount,focused?1f:0f);focusFade.start();}
        }
        this.selected = selected; this.focused = focused;
        if (changed || !placed) {
            float left = 0;
            for (int i=0;i<selected;i++) left += text.measureText(LABELS[i])+30*d;
            indicator.cancel(); toLeft=left; toRight=left+text.measureText(LABELS[selected]);
            if (!placed || !isAttachedToWindow()) { indicatorLeft=toLeft; indicatorRight=toRight; placed=true; }
            else { fromLeft=indicatorLeft; fromRight=indicatorRight; indicator.start(); }
        }
        setContentDescription("Screens. " + LABELS[selected] + " selected. " +
                (selected == 1 ? "SHAH ALAM · SGR01. " : "") +
                (selected == 1 ? "Daily hadith fits on screen. Left or right to switch; Back for Home."
                        : selected == 2 ? "Read-only forecast. Left or right to switch; Back for Home."
                        : "Left or right to switch; down for " + (selected==3?"devices.":"apps.")));
        invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        float x = 0;
        for (int i = 0; i < LABELS.length; i++) {
            float width = text.measureText(LABELS[i]);
            text.setColor(i == selected ? SkyView.lerpColor(Lapis.PLASTER_100,FOCUS_GOLD,focusAmount) : Lapis.MIST_400);
            c.drawText(LABELS[i], x, 23*d, text);
            x += width + 30*d;
        }
        line.setColor(SkyView.lerpColor(0xFF9EADC3,FOCUS_GOLD,focusAmount));
        c.drawRoundRect(indicatorLeft,getHeight()-4.5f*d,indicatorRight,getHeight()-2.5f*d,d,d,line);
        text.setColor(Lapis.MIST_400); text.setTextSize(10*d);
        if (selected == 1) {
            String label = "SHAH ALAM · SGR01";
            c.drawText(label, getWidth() - text.measureText(label), 23*d, text);
        }
        text.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
    }
    @Override protected void onDetachedFromWindow() { indicator.cancel(); focusFade.cancel(); super.onDetachedFromWindow(); }
}
