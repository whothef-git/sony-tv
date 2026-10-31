package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
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
    private final long animationStart=SystemClock.uptimeMillis();
    private final Bitmap streak=Bitmap.createBitmap(12,96,Bitmap.Config.ARGB_8888);
    private final RectF dropRect=new RectF();
    private final float[] dropX=new float[180],dropPhase=new float[180],dropDepth=new float[180];
    private Bitmap cloudTexture;
    private Thread cloudWorker;
    private volatile boolean closed;
    private LinearGradient rainBackdrop;
    private Weather.City current;
    private boolean running, screenActive;
    private int previewCode = -1;
    private final Runnable expirePreview = () -> { previewCode=-1; invalidate(); };

    public WeatherAtmosphereView(Context c, AttributeSet a) {
        super(c,a);
        d=getResources().getDisplayMetrics().density;
        rain.setStrokeCap(Paint.Cap.ROUND);
        rain.setFilterBitmap(true);
        for(int i=0;i<dropX.length;i++){
            dropX[i]=seed(i,31);dropPhase[i]=seed(i,71);dropDepth[i]=seed(i,113);
        }
        Paint sprite=new Paint(Paint.ANTI_ALIAS_FLAG);
        sprite.setShader(new LinearGradient(0,0,0,96,new int[]{0x00CEE1F0,0x20CEE1F0,0xC0E3EFF8,0x00E3EFF8},
                new float[]{0,.4f,.9f,1},Shader.TileMode.CLAMP));
        new Canvas(streak).drawOval(3,0,9,96,sprite);
        previewText.setTypeface(android.graphics.Typeface.create(getResources().getFont(R.font.outfit),400,false));
        previewText.setFontVariationSettings("'wght' 400");
        previewText.setTextSize(10*d); previewText.setColor(0xFFC2CFDF); previewText.setTextAlign(Paint.Align.CENTER);
    }
    void setWeather(Weather.City city) { current=city; buildBackdrop(); if(screenActive)prepareClouds(); invalidate(); }
    void setRunning(boolean on) { running=on; if(on) invalidate(); }
    void setScreenActive(boolean on, boolean animate) {
        if (on==screenActive && (on || getVisibility()==GONE)) return;
        screenActive=on; animate().cancel();
        if(on)prepareClouds();
        if (!animate) { setAlpha(on?1f:0f); setVisibility(on?VISIBLE:GONE); return; }
        if(on) {
            if(getVisibility()!=VISIBLE) { setAlpha(0f); setVisibility(VISIBLE); }
            animate().alpha(1f).setDuration(300).start();
        } else animate().alpha(0f).setDuration(300).withEndAction(()->{if(!screenActive)setVisibility(GONE);}).start();
    }
    void setPreviewCode(int code) {
        if(!BuildConfig.DEBUG) return;
        removeCallbacks(expirePreview); previewCode=code;
        if(code>=0)prepareClouds();
        if(code>=0) postDelayed(expirePreview,15000);
        invalidate();
    }
    static boolean rainCode(int code) { return code>=51&&code<=67 || code>=80&&code<=82 || code>=95; }
    static boolean snowCode(int code) { return code>=71&&code<=77 || code==85 || code==86; }
    static int rainDrops(int code){
        if(!rainCode(code))return 0;
        if(code==65||code==67||code==82||code>=96)return 180;
        if(code==63||code==81||code==95)return 120;
        if(code==55||code==57)return 88;
        if(code==53)return 76;
        if(code>=51&&code<=57)return 64;
        return 88;
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){buildBackdrop();}
    private void buildBackdrop(){
        boolean day=current!=null&&current.day;
        rainBackdrop=new LinearGradient(0,0,0,Math.max(1,getHeight()),day
                ?new int[]{0x70404D60,0x60333F52,0x50303C50}
                :new int[]{0x950C1524,0x75111C2C,0x60152233},null,Shader.TileMode.CLAMP);
    }
    private static float hash(int x,int y){
        int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;
        return ((n^(n>>>16))&0xffffff)/16777216f;
    }
    private static float noise(float x,float y,int period){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        float u=x-ix,v=y-iy;u=u*u*(3-2*u);v=v*v*(3-2*v);
        int x0=Math.floorMod(ix,period),x1=Math.floorMod(ix+1,period);
        float a=hash(x0,iy),b=hash(x1,iy),c=hash(x0,iy+1),e=hash(x1,iy+1);
        return a+(b-a)*u+(c-a)*v+(a-b-c+e)*u*v;
    }
    /** A small, seamless cloud texture is generated once off the UI thread. */
    private void prepareClouds(){
        if(closed||cloudTexture!=null||cloudWorker!=null)return;
        cloudWorker=new Thread(()->{
            int width=256,height=128;int[] pixels=new int[width*height];
            for(int y=0;y<height;y++){
                if(Thread.currentThread().isInterrupted())return;
                float fade=(float)Math.sin(Math.PI*(y+.5f)/height);
                for(int x=0;x<width;x++){
                    float px=x*8f/width,py=y*3f/height;
                    float n=noise(px,py,8)*.55f+noise(px*2,py*2,16)*.27f
                            +noise(px*4,py*4,32)*.13f+noise(px*8,py*8,64)*.05f;
                    float mass=Math.max(0,Math.min(1,(n-.3f)*2.5f));
                    int alpha=(int)(mass*fade*190),shade=(int)(75+n*85);
                    pixels[y*width+x]=Color.argb(alpha,shade,shade+8,Math.min(255,shade+20));
                }
            }
            Bitmap generated=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
            post(()->{if(closed){generated.recycle();return;}cloudTexture=generated;invalidate();});
        },"Weather cloud texture");
        cloudWorker.start();
    }
    private void drawRain(Canvas c,float w,float h,double seconds,int code){
        if(rainBackdrop==null)buildBackdrop();
        light.setShader(rainBackdrop);c.drawRect(0,0,w,h,light);light.setShader(null);
        if(cloudTexture!=null){
            rain.setStyle(Paint.Style.FILL);rain.setColor(Color.WHITE);rain.setAlpha(125);
            float width=w*1.25f;
            for(int layer=0;layer<2;layer++){
                float y=-h*.1f+layer*h*.1f,height=h*(layer==0?.61f:.44f);
                float x=-(float)((seconds*(layer==0?3:2)*d)%width);
                for(int copy=-1;copy<2;copy++){
                    dropRect.set(x+copy*width,y,x+(copy+1)*width,y+height);
                    c.drawBitmap(cloudTexture,null,dropRect,rain);
                }
                rain.setAlpha(80);
            }
        }
        int count=rainDrops(code);
        for(int i=0;i<count;i++){
            float depth=dropDepth[i],length=(6+depth*19)*d;
            double speed=(95+depth*300)*d;
            float y=(float)((dropPhase[i]*(h+2*length)+seconds*speed)%(h+2*length))-length;
            float x=(float)((dropX[i]*(w+24*d)+seconds*(2+depth*3)*d)%(w+24*d))-12*d;
            float width=(.65f+depth*1.2f)*d;
            rain.setAlpha((int)(68+depth*105));
            dropRect.set(x-width/2,y-length,x+width/2,y);
            c.drawBitmap(streak,null,dropRect,rain);
        }
        drawSplashes(c,w,h,seconds,code);
        rain.setAlpha(255);
    }
    /** Short ballistic droplets at the screen edge, timed to each falling drop's impact. */
    void drawSplashes(Canvas c,float w,float h,double seconds,int code){
        rain.setShader(null);rain.setColor(0xFFD5E8F5);rain.setStyle(Paint.Style.STROKE);
        for(int i=0;i<rainDrops(code);i++){
            float depth=dropDepth[i],length=(6+depth*19)*d;
            double speed=(95+depth*300)*d,span=h+2*length,period=span/speed;
            double impact=(h+length-dropPhase[i]*span)/speed;
            double age=((seconds-impact)%period+period)%period;
            float life=.30f+depth*.16f;
            if(age>life)continue;
            float t=(float)(age/life),fade=(1-t)*(1-t);
            float x=(float)((dropX[i]*(w+24*d)+(seconds-age)*(2+depth*3)*d)%(w+24*d))-12*d;
            float ground=h-2*d;
            rain.setStrokeWidth((.55f+depth*.45f)*d);
            rain.setAlpha((int)((95+depth*105)*fade));
            float radius=(1+t*(5+depth*8))*d;
            dropRect.set(x-radius,ground-(.5f+t)*d,x+radius,ground+(.5f+t)*d);
            c.drawArc(dropRect,180,180,false,rain);
            for(int jet=0;jet<4;jet++){
                float direction=jet<2?-1:1;
                float reach=(5+depth*10+(jet%2)*5)*d;
                float height=(4+depth*9+(jet%2)*3)*d;
                float px=x+direction*reach*t,py=ground-4*height*t*(1-t);
                float tail=.035f;
                float before=Math.max(0,t-tail);
                c.drawLine(x+direction*reach*before,ground-4*height*before*(1-before),px,py,rain);
            }
        }
        rain.setStyle(Paint.Style.FILL);rain.setAlpha(255);
    }
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
        float w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        double elapsed=(SystemClock.uptimeMillis()-animationStart)/1000d;
        float seconds=(float)elapsed;
        boolean wet=rainCode(code), snowy=snowCode(code), cloudy=code>=1;
        if(wet){drawRain(c,w,h,elapsed,code);}
        else if(cloudy) {
            for(int i=0;i<7;i++) {
                float x=((seed(i,17)*w+seconds*(4+i*.4f)*d)%(w*1.6f))-w*.3f;
                float y=h*(.12f+seed(i,51)*.45f);
                glow(c,cloud,x,y,w*.35f,h*.15f);
            }
        } else glow(c,current!=null&&current.day?sun:moon,w*.75f,h*.2f,w*.33f,h*.4f);
        if(snowy) {
            rain.setStyle(Paint.Style.FILL); rain.setColor(0x65E7EFF5);
            for(int i=0;i<45;i++) {
                float y=(seed(i,91)*h+seconds*(12+seed(i,51)*16)*d)%h;
                float x=(seed(i,79)*w+(float)Math.sin(seconds*.4f+i)*12*d+w)%w;
                c.drawCircle(x,y,(1+seed(i,3))*d,rain);
            }
        }
        if(previewCode>=0) c.drawText("Weather effect preview",w/2,h-16*d,previewText);
        if(running && screenActive && isShown()){
            if(wet)postInvalidateOnAnimation();
            else if(cloudy||snowy)postInvalidateDelayed(40);
        }
    }
    void shutdown(){
        closed=true;running=false;removeCallbacks(expirePreview);animate().cancel();
        if(cloudWorker!=null)cloudWorker.interrupt();
    }
    @Override protected void onDetachedFromWindow() {
        shutdown();super.onDetachedFromWindow();
    }
}
