package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

/** Gently moves/fades changed digits without cutting them against a rectangular roll window. */
public final class RollingTimeView extends TextView {
    private String previous="",current="";
    private long changedAt;
    private boolean motionEnabled;
    private boolean alternatingDirection;
    public RollingTimeView(Context context,AttributeSet attrs){super(context,attrs);}
    void setAlternatingDirection(boolean enabled){alternatingDirection=enabled;}
    void setMotionEnabled(boolean enabled){
        motionEnabled=enabled;
        if(!enabled){previous=current;invalidate();}
    }
    @Override protected void onTextChanged(CharSequence text,int start,int before,int count){
        super.onTextChanged(text,start,before,count);
        String next=text.toString();
        if(next.equals(current))return;
        previous=current==null?next:current;current=next;
        if(!motionEnabled||previous.length()!=current.length())previous=current;
        changedAt=SystemClock.uptimeMillis();
    }
    @Override protected void onDraw(Canvas canvas){
        Layout layout=getLayout();
        float fraction=Math.min(1,(SystemClock.uptimeMillis()-changedAt)/240f);
        if(layout==null||layout.getLineCount()!=1){super.onDraw(canvas);return;}
        if(!motionEnabled||fraction>=1)previous=current;
        float progress=1-(float)Math.pow(1-fraction,3);
        getPaint().setColor(getCurrentTextColor());
        Paint.FontMetrics metrics=getPaint().getFontMetrics();
        float baseline=getBaseline();
        float travel=Math.min((metrics.descent-metrics.ascent)*.12f,3*getResources().getDisplayMetrics().density);
        if(alternatingDirection){
            // Last time digit, including clocks with weekday or am/pm suffixes.
            for(int i=current.length()-1;i>=0;i--){
                char digit=current.charAt(i);
                if(digit>='0'&&digit<='9'){if((digit-'0')%2==0)travel=-travel;break;}
            }
        }
        int alpha=getPaint().getAlpha();
        for(int i=0;i<current.length();i++){
            float left=getCompoundPaddingLeft()+layout.getPrimaryHorizontal(i);
            if(previous.charAt(i)==current.charAt(i)){
                canvas.drawText(current,i,i+1,left,baseline,getPaint());
            }else{
                getPaint().setAlpha(Math.round(alpha*(1-progress)));
                canvas.drawText(previous,i,i+1,left,baseline-progress*travel,getPaint());
                getPaint().setAlpha(Math.round(alpha*progress));
                canvas.drawText(current,i,i+1,left,baseline+(1-progress)*travel,getPaint());
                getPaint().setAlpha(alpha);
            }
        }
        if(!previous.equals(current)&&fraction<1&&isShown())postInvalidateOnAnimation();
    }
    @Override protected void onDetachedFromWindow(){
        previous=current;super.onDetachedFromWindow();
    }
}
