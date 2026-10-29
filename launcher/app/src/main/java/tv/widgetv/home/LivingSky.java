package tv.widgetv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Random;

/** Approved clear-sky treatment. Celestial objects have independent opacity from the faint sky tint. */
final class LivingSky {
    static final int SKY_ALPHA=38,CELESTIAL_ALPHA=128; // 15% and 50%.
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap texture=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
    private final Bitmap moon=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
    private float lastIllumination=-1;
    private boolean lastWaxing;
    private int lastTint=-1;
    private Shader tint;
    private final Shader sun=new RadialGradient(-10,-12,64,new int[]{0xFFFFF8DF,0xFFFFE3A4,0xFFF8C275},null,Shader.TileMode.CLAMP);
    private final Shader sunHalo=new RadialGradient(0,0,190,new int[]{0x1CFFE0A0,0x00FFE0A0},null,Shader.TileMode.CLAMP);
    private final Shader moonHalo=new RadialGradient(0,0,200,new int[]{0x1397B5DA,0x0097B5DA},null,Shader.TileMode.CLAMP);
    LivingSky(){
        Canvas c=new Canvas(texture);Random r=new Random(441);
        paint.setShader(new RadialGradient(56,44,116,new int[]{0xFFF1F0DF,0xFFD1D6D8,0xFF879BAD},null,Shader.TileMode.CLAMP));
        c.drawCircle(80,80,77,paint);paint.setShader(null);
        c.save();Path circle=new Path();circle.addCircle(80,80,77,Path.Direction.CW);c.clipPath(circle);
        for(int i=0;i<95;i++){paint.setColor(i%3==0?0x26536C83:0x1850677A);c.drawCircle(r.nextFloat()*160,r.nextFloat()*160,2+r.nextFloat()*10,paint);}c.restore();
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
        moon.eraseColor(0);Canvas c=new Canvas(moon);paint.setColor(0xFFFFFFFF);paint.setAlpha(255);paint.setShader(null);c.drawBitmap(texture,0,0,paint);
        paint.setColor(0xFA09172B);
        for(int row=3;row<157;row++){
            double yn=(row+.5-80)/77.0;float span=(float)Math.sqrt(Math.max(0,1-yn*yn))*77;
            float edge=80+terminator(entry.illuminated,entry.waxing)*span;
            if(entry.waxing)c.drawRect(80-span,row,edge,row+1,paint);
            else c.drawRect(edge,row,80+span,row+1,paint);
        }
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
            if(daytime){paint.setShader(sun);c.drawCircle(0,0,41,paint);paint.setShader(null);}
            else c.drawBitmap(moon,null,new RectF(-40,-40,40,40),paint);
            paint.setAlpha(255);c.restore();
        }
        c.restore();
    }
}
