package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import org.json.JSONObject;
import java.time.LocalDate;

/** Targeted wallpaper checks only. Does not activate smart-home controls or launcher apps. */
public final class LivingSkyChecks extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private String fixture(LocalDate date,String phase,String illumination)throws Exception{
        JSONObject data=new JSONObject().put("year",date.getYear()).put("month",date.getMonthValue()).put("day",date.getDayOfMonth()).put("curphase",phase).put("fracillum",illumination);
        return new JSONObject().put("properties",new JSONObject().put("data",data)).toString();
    }
    private void rejects(String data,LocalDate date,String label)throws Exception{
        boolean rejected=false;try{MoonCalendar.parse(data,date);}catch(Exception expected){rejected=true;}check(rejected,label);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();String file="sky-test-"+java.util.UUID.randomUUID();MoonCalendar provider=null,reloaded=null;
        Context context=new ContextWrapper(getTargetContext()){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(file,mode);}
        };
        try{
            LocalDate today=MoonCalendar.date(System.currentTimeMillis());
            MoonCalendar.Entry waning=MoonCalendar.parse(fixture(today,"Waning Gibbous","88%"),today);
            check(!waning.waxing&&Math.abs(waning.illuminated-.88f)<.001f,"official waning phase parsed");
            String[] phases={"New Moon","Waxing Crescent","First Quarter","Waxing Gibbous","Full Moon","Waning Gibbous","Last Quarter","Waning Crescent"};
            for(String phase:phases){MoonCalendar.Entry entry=MoonCalendar.parse(fixture(today,phase,"50%"),today);check(entry.phase.equals(phase),"phase accepted: "+phase);}
            rejects(fixture(today.minusDays(1),"Full Moon","100%"),today,"stale calendar date rejected");
            rejects(fixture(today,"Unknown","50%"),today,"unknown phase rejected");
            for(String invalid:new String[]{"NaN%","-1%","101%","88","null"})rejects(fixture(today,"Full Moon",invalid),today,"invalid illumination rejected: "+invalid);
            rejects("{\"error\":\"unavailable\"}",today,"API error rejected");
            check(LivingSky.terminator(0,true)==1&&LivingSky.terminator(1,true)==-1,"new and full moon terminators");
            check(LivingSky.terminator(.5f,true)==0&&LivingSky.terminator(.5f,false)==0,"quarter terminators");
            check(LivingSky.terminator(.88f,true)<0&&LivingSky.terminator(.88f,false)>0,"waxing and waning sides distinct");
            long rise=7*3_600_000L,set=19*3_600_000L;
            check(LivingSky.sunProgress(rise,rise,set)==0&&LivingSky.sunProgress(set,rise,set)==1,"sun follows rise and set");
            check(LivingSky.sunProgress(13*3_600_000L,rise,set)==.5f,"midday arc midpoint");
            check(LivingSky.nightProgress(25*3_600_000L,rise,set)==.5f,"night arc crosses midnight");
            LivingSky renderer=new LivingSky();Bitmap image=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
            image.eraseColor(0xFF0B192D);renderer.draw(canvas,1920,1080,13*3_600_000L,rise,set,null);
            int sun=image.getPixel(975,430),sky=image.getPixel(1800,700);
            check(Color.red(sun)>110&&Color.red(sun)<180,"sun remains translucent at 50 percent");
            check(Math.abs(Color.red(sky)-11)<40&&Math.abs(Color.green(sky)-25)<40,"sky tint stays faint");
            image.eraseColor(0xFF0B192D);renderer.draw(canvas,1920,1080,25*3_600_000L,rise,set,waning);
            check(Color.red(image.getPixel(965,430))>40,"calendar moon renders on night arc");
            image.recycle();
            provider=new MoonCalendar(context);String url=provider.url(System.currentTimeMillis());
            check(url.startsWith("https://aa.usno.navy.mil/api/rstt/oneday?"),"official HTTPS calendar endpoint");
            context.getSharedPreferences("moon_calendar",Context.MODE_PRIVATE).edit().putString("url",url).putString("response",fixture(today,"Waning Gibbous","88%")).commit();
            reloaded=new MoonCalendar(context);
            check(reloaded.get(System.currentTimeMillis())!=null,"daily phase restored offline");
            check(reloaded.get(System.currentTimeMillis()+86_400_000L)==null,"previous day's phase not substituted");
            MoonCalendar.Entry live=MoonCalendar.parse(MoonCalendar.download(url),today);
            check(live.date.equals(today)&&live.illuminated>=0&&live.illuminated<=1,"real official calendar response verified on TV");
            result.putString("stream","PASS: "+checks+" wallpaper checks, including official calendar HTTPS on TV. No device controls tested.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL after "+checks+" wallpaper checks: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(provider!=null)provider.shutdown();if(reloaded!=null)reloaded.shutdown();getTargetContext().deleteSharedPreferences(file);}
    }
}
