package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import java.util.Arrays;

/** Condition fixtures and real timed drawing on the TV; does not change live weather data. */
public final class WeatherRainChecks extends Instrumentation {
    private int checks;
    private Throwable failure;
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private void ui(Runnable task){runOnMainSync(()->{try{task.run();}catch(Throwable e){failure=e;}});if(failure!=null)throw new AssertionError(failure);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();WeatherAtmosphereView[] view={null};
        Bitmap first=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);
        Bitmap second=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);
        int[] a=new int[960*540],b=new int[a.length];
        try{
            for(int code:new int[]{51,53,55,56,57,61,63,65,66,67,80,81,82,95,96,99})
                check(WeatherAtmosphereView.rainCode(code),"wet condition accepted: "+code);
            for(int code:new int[]{-1,0,1,2,3,45,48,71,73,75,77,85,86})
                check(!WeatherAtmosphereView.rainCode(code)&&WeatherAtmosphereView.rainDrops(code)==0,"non-rain has no raindrops: "+code);
            check(WeatherAtmosphereView.rainDrops(51)<WeatherAtmosphereView.rainDrops(61)
                    &&WeatherAtmosphereView.rainDrops(61)<WeatherAtmosphereView.rainDrops(63)
                    &&WeatherAtmosphereView.rainDrops(63)<WeatherAtmosphereView.rainDrops(65),"rain density follows drizzle/light/moderate/heavy");
            ui(()->{
                view[0]=new WeatherAtmosphereView(getTargetContext(),null);
                view[0].measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));
                view[0].layout(0,0,960,540);
                view[0].setWeather(null);view[0].draw(new Canvas(first));
            });
            first.getPixels(a,0,960,0,0,960,540);
            check(Arrays.stream(a).allMatch(pixel->pixel==0),"missing weather draws no invented condition");
            ui(()->{
                view[0].setScreenActive(true,false);view[0].setWeather(new Weather.City(24,63,false,30,24,true));
                first.eraseColor(0);view[0].draw(new Canvas(first));
            });
            Thread.sleep(140);
            ui(()->view[0].draw(new Canvas(second)));
            first.getPixels(a,0,960,0,0,960,540);second.getPixels(b,0,960,0,0,960,540);
            int changed=0;for(int i=960*380;i<a.length;i++)if(a[i]!=b[i])changed++;
            check(changed>100,"rain moves through the lower scene rather than remaining static");
            ui(()->{first.eraseColor(0);view[0].drawSplashes(new Canvas(first),960,540,12.3,65);});
            first.getPixels(a,0,960,0,0,960,540);
            int splashPixels=0,upperPixels=0;
            for(int i=0;i<a.length;i++)if(a[i]!=0){if(i/960>490)splashPixels++;else upperPixels++;}
            check(splashPixels>20&&upperPixels==0,"heavy rain splashes stay along the bottom edge");
            ui(()->{second.eraseColor(0);view[0].drawSplashes(new Canvas(second),960,540,12.44,65);});
            second.getPixels(b,0,960,0,0,960,540);
            check(!Arrays.equals(a,b),"splash droplets move and fade after impacts");
            ui(()->{first.eraseColor(0);view[0].drawSplashes(new Canvas(first),960,540,12.3,0);});
            first.getPixels(a,0,960,0,0,960,540);
            check(Arrays.stream(a).allMatch(pixel->pixel==0),"dry weather has no bottom splashes");
            ui(()->{
                view[0].setWeather(new Weather.City(25,0,false,31,24,true));
                first.eraseColor(0);view[0].draw(new Canvas(first));
            });
            Thread.sleep(100);
            ui(()->{second.eraseColor(0);view[0].draw(new Canvas(second));});
            first.getPixels(a,0,960,0,0,960,540);second.getPixels(b,0,960,0,0,960,540);
            check(Arrays.equals(a,b),"clear weather has no moving rain particles");
            ui(()->{view[0].setRunning(false);view[0].setScreenActive(false,false);});
            check(view[0].getVisibility()==View.GONE,"atmosphere is hidden outside Weather");
            result.putString("stream","PASS: "+checks+" weather-rain checks on TV. Weather conditions are fixtures; no live reading or smart-device control changed.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL after "+checks+" rain checks: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(view[0]!=null)runOnMainSync(()->view[0].shutdown());first.recycle();second.recycle();}
    }
}
