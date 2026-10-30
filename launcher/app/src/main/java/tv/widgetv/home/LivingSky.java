package tv.widgetv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Typeface;

/** Approved clear-sky treatment. Celestial objects have independent opacity from the faint sky tint. */
final class LivingSky {
    static final int SKY_ALPHA=38,CELESTIAL_ALPHA=128; // 15% and 50%.
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phaseLabel=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect labelBounds=new Rect();
    private final Bitmap texture=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
    private final Bitmap moon=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
    private float lastIllumination=-1;
    private boolean lastWaxing;
    private int lastTint=-1;
    private Shader tint;
    static final float MOON_DIAMETER=90, SUN_DIAMETER=92;
    private final Bitmap solar;
    private final Shader sunHalo=new RadialGradient(0,0,190,new int[]{0x1CFFE0A0,0x00FFE0A0},null,Shader.TileMode.CLAMP);
    private final Shader moonHalo=new RadialGradient(0,0,200,new int[]{0x1397B5DA,0x0097B5DA},null,Shader.TileMode.CLAMP);
    LivingSky(Context context){
        paint.setFilterBitmap(true);
        phaseLabel.setTypeface(Typeface.create(context.getResources().getFont(R.font.outfit),400,false));
        phaseLabel.setTextSize(14);
        phaseLabel.setTextAlign(Paint.Align.CENTER);
        phaseLabel.setColor(0xFFA5B4CA);
        loadDisc(context,R.drawable.lunar_surface,texture,.03f,.97f);
        solar=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
        loadDisc(context,R.drawable.solar_surface,solar,.063f,.937f);
    }
    /** Decode once at a bounded size; a circular clip prevents any oval or rectangular edge. */
    private void loadDisc(Context context,int resource,Bitmap target,float start,float end){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inScaled=false;options.inSampleSize=4;
        Bitmap source=BitmapFactory.decodeResource(context.getResources(),resource,options);
        Canvas canvas=new Canvas(target);Path disc=new Path();disc.addCircle(80,80,80,Path.Direction.CW);
        canvas.clipPath(disc);
        canvas.drawBitmap(source,new Rect(Math.round(source.getWidth()*start),Math.round(source.getHeight()*start),
                Math.round(source.getWidth()*end),Math.round(source.getHeight()*end)),new RectF(0,0,160,160),paint);
        source.recycle();
    }
    static float sunProgress(long now,long rise,long set){return (now-rise)/(float)(set-rise);}
    static float nightProgress(long now,long rise,long set){
        long start=now<rise?set-86_400_000L:set,end=now<rise?rise:rise+86_400_000L;
        return (now-start)/(float)(end-start);
    }
    /** Signed terminator in a unit disc: waxing illuminates the right side, waning the left. */
    static float terminator(float illuminated,boolean waxing){return (waxing?1:-1)*(1-2*illuminated);}
    private void phase(MoonCalendar.Entry entry){
        if(lastIllumination==entry.illuminated&&lastWaxing==entry.waxing)return;
        lastIllumination=entry.illuminated;lastWaxing=entry.waxing;
        int[] pixels=new int[160*160];texture.getPixels(pixels,0,160,0,0,160,160);
        float lightZ=2*entry.illuminated-1;
        float lightX=(entry.waxing?1:-1)*(float)Math.sqrt(Math.max(0,1-lightZ*lightZ));
        for(int row=0;row<160;row++)for(int col=0;col<160;col++){
            float x=(col+.5f-80)/80,y=(row+.5f-80)/80,r2=x*x+y*y;
            int index=row*160+col,color=pixels[index];
            if(r2>1){pixels[index]=0;continue;}
            float light=x*lightX+(float)Math.sqrt(1-r2)*lightZ;
            float brightness=.06f+.94f*(float)Math.sqrt(Math.max(0,light));
            pixels[index]=(color&0xFF000000)|((int)(((color>>16)&255)*brightness)<<16)
                    |((int)(((color>>8)&255)*brightness)<<8)|(int)((color&255)*brightness);
        }
        moon.setPixels(pixels,0,160,0,0,160,160);
    }
    private void buildTint(float hour){
        int bucket=Math.round(hour*60);if(bucket==lastTint)return;lastTint=bucket;
        float[] hours={0,5,7,12,17,19,21,24};
        int[][] colors={{0xFF060D20,0xFF0B1930,0xFF14273C},{0xFF111D38,0xFF353C61,0xFF927580},{0xFF102443,0xFF4B657E,0xFFE5AD7D},{0xFF0F294D,0xFF3E779E,0xFF9EC1D1},{0xFF182C48,0xFF8C7762,0xFFE3B476},{0xFF231D3D,0xFF815065,0xFFCD835E},{0xFF0A142A,0xFF14253C,0xFF26374A},{0xFF060D20,0xFF0B1930,0xFF14273C}};
        int i=0;while(i<hours.length-2&&hour>=hours[i+1])i++;
        float f=(hour-hours[i])/(hours[i+1]-hours[i]);int[] mixed=new int[3];
        for(int n=0;n<3;n++)mixed[n]=SkyView.lerpColor(colors[i][n],colors[i+1][n],f);
        tint=new LinearGradient(0,0,0,1080,mixed,new float[]{0,.55f,1},Shader.TileMode.CLAMP);
    }
    void draw(Canvas c,int width,int height,long now,long rise,long set,MoonCalendar.Entry entry){
        if(set<=rise||width==0||height==0)return;
        boolean daytime=now>=rise&&now<=set;
        float progress=daytime?sunProgress(now,rise,set):nightProgress(now,rise,set);
        progress=Math.max(0,Math.min(1,progress));
        float solarHour=daytime?6+12*progress:(18+12*progress)%24;
        buildTint(solarHour);
        c.save();c.scale(width/1920f,height/1080f);
        paint.setShader(tint);paint.setAlpha(SKY_ALPHA);c.drawRect(0,0,1920,1080,paint);paint.setShader(null);paint.setAlpha(255);
        if(daytime||entry!=null){
            float x=320+progress*1310,y=795-(float)Math.sin(progress*Math.PI)*365;
            float edge=Math.max(0,Math.min(1,Math.min(progress*15,(1-progress)*15)));
            if(!daytime)phase(entry);
            c.save();c.translate(x,y);
            paint.setAlpha(Math.round(edge*255));paint.setShader(daytime?sunHalo:moonHalo);c.drawCircle(0,0,daytime?190:200,paint);paint.setShader(null);
            paint.setAlpha(Math.round(edge*CELESTIAL_ALPHA));
            float radius=(daytime?SUN_DIAMETER:MOON_DIAMETER)/2;
            c.drawBitmap(daytime?solar:moon,null,new RectF(-radius,-radius,radius,radius),paint);
            if(!daytime){
                phaseLabel.setAlpha(Math.round(edge*255*.7f));
                phaseLabel.getTextBounds(entry.phase,0,entry.phase.length(),labelBounds);
                c.drawText(entry.phase,0,radius+8-labelBounds.top,phaseLabel);
            }
            paint.setAlpha(255);c.restore();
        }
        c.restore();
    }
}
