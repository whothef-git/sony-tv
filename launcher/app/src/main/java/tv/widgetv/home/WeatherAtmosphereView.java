package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Condition-driven atmosphere behind the UI. Never infers current rain from a forecast probability. */
public final class WeatherAtmosphereView extends View {
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader cloud = new RadialGradient(0,0,1,0x24CDDDEC,0x00CDDDEC,Shader.TileMode.CLAMP);
    private final Shader sun = new RadialGradient(0,0,1,0x30F1CB88,0x00F1CB88,Shader.TileMode.CLAMP);
    private final Shader moon = new RadialGradient(0,0,1,0x183F87C5,0x003F87C5,Shader.TileMode.CLAMP);
    private final float d;
    private Weather.City current;
    private boolean running, screenActive;
    private int previewCode = -1;
    private final Runnable expirePreview = () -> { previewCode=-1; invalidate(); };

    public WeatherAtmosphereView(Context c, AttributeSet a) {
        super(c,a);
        d=getResources().getDisplayMetrics().density;
        rain.setStrokeCap(Paint.Cap.ROUND);
        previewText.setTypeface(android.graphics.Typeface.create(getResources().getFont(R.font.outfit),400,false));
        previewText.setFontVariationSettings("'wght' 400");
        previewText.setTextSize(10*d); previewText.setColor(0xFFC2CFDF); previewText.setTextAlign(Paint.Align.CENTER);
    }
    void setWeather(Weather.City city) { current=city; invalidate(); }
    void setRunning(boolean on) { running=on; if(on) invalidate(); }
    void setScreenActive(boolean on, boolean animate) {
        if (on==screenActive && (on || getVisibility()==GONE)) return;
        screenActive=on; animate().cancel();
        if (!animate) { setAlpha(on?1f:0f); setVisibility(on?VISIBLE:GONE); return; }
        if(on) {
            if(getVisibility()!=VISIBLE) { setAlpha(0f); setVisibility(VISIBLE); }
            animate().alpha(1f).setDuration(300).start();
        } else animate().alpha(0f).setDuration(300).withEndAction(()->{if(!screenActive)setVisibility(GONE);}).start();
    }
    void setPreviewCode(int code) {
        if(!BuildConfig.DEBUG) return;
        removeCallbacks(expirePreview); previewCode=code;
        if(code>=0) postDelayed(expirePreview,15000);
        invalidate();
    }
    static boolean rainCode(int code) { return code>=51&&code<=67 || code>=80&&code<=82 || code>=95; }
    static boolean snowCode(int code) { return code>=71&&code<=77 || code==85 || code==86; }
    private float seed(int i,int salt) {
        long n=(i+1)*0x9E3779B9L ^ salt*0x85EBCA6BL;
        n=(n^(n>>>16))*0x7FEB352DL; n^=n>>>15;
        return (n&0xffffffL)/16777216f;
    }
    private void glow(Canvas c, Shader shader, float x,float y,float width,float height) {
        c.save(); c.translate(x,y); c.scale(width,height); light.setShader(shader); c.drawCircle(0,0,1,light); c.restore();
    }
    @Override protected void onDraw(Canvas c) {
        int code=previewCode>=0?previewCode:current==null?-1:current.code;
        if(code<0) return;
        float w=getWidth(), h=getHeight(), seconds=SystemClock.uptimeMillis()/1000f;
        boolean wet=rainCode(code), snowy=snowCode(code), cloudy=code>=1;
        if(cloudy) {
            for(int i=0;i<7;i++) {
                float x=((seed(i,17)*w+seconds*(4+i*.4f)*d)%(w*1.6f))-w*.3f;
                float y=h*(.12f+seed(i,51)*.45f);
                glow(c,cloud,x,y,w*.35f,h*.15f);
            }
        } else glow(c,current!=null&&current.day?sun:moon,w*.75f,h*.2f,w*.33f,h*.4f);
        if(wet) {
            int count=code>=63||code>=95?84:48;
            for(int i=0;i<count;i++) {
                float speed=(190+seed(i,113)*240)*d;
                float y=(seed(i,71)*h+seconds*speed)%(h+60*d)-30*d;
                float x=(seed(i,31)*w+seconds*12*d)%w;
                float length=(9+seed(i,43)*15)*d;
                rain.setStyle(Paint.Style.STROKE); rain.setStrokeWidth((.45f+seed(i,13)*.4f)*d);
                rain.setColor(((30+(int)(seed(i,29)*45))<<24)|0xB3D2E9);
                c.drawLine(x,y,x-length*.23f,y+length,rain);
            }
            // Gentle droplets on the glass, without covering or tinting app artwork.
            for(int i=0;i<12;i++) {
                float x=seed(i,239)*w,y=seed(i,311)*h;
                rain.setColor(0x1BBEDCF0);rain.setStrokeWidth(.5f*d);
                c.drawOval(x,y,x+5*d,y+8*d,rain);
            }
        } else if(snowy) {
            rain.setStyle(Paint.Style.FILL); rain.setColor(0x65E7EFF5);
            for(int i=0;i<45;i++) {
                float y=(seed(i,91)*h+seconds*(12+seed(i,51)*16)*d)%h;
                float x=(seed(i,79)*w+(float)Math.sin(seconds*.4f+i)*12*d+w)%w;
                c.drawCircle(x,y,(1+seed(i,3))*d,rain);
            }
        }
        if(previewCode>=0) c.drawText("Weather effect preview",w/2,h-16*d,previewText);
        if(running && isShown() && (cloudy||wet||snowy)) postInvalidateDelayed(40);
    }
    @Override protected void onDetachedFromWindow() {
        running=false; removeCallbacks(expirePreview); animate().cancel(); super.onDetachedFromWindow();
    }
}
