package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;

/** Clock/header checks only; no launcher app or smart-device controls are activated. */
public final class ClockHeaderChecks extends Instrumentation {
    private int checks;
    private Throwable failure;
    private volatile MainActivity launched;
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent)
            throws InstantiationException,IllegalAccessException,ClassNotFoundException{
        Activity activity=super.newActivity(loader,name,intent);
        if(activity instanceof MainActivity)launched=(MainActivity)activity;
        return activity;
    }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void ui(Runnable task){runOnMainSync(()->{try{task.run();}catch(Throwable error){failure=error;}});if(failure!=null)throw new AssertionError(failure);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();MainActivity activity=null;
        try{
            runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));
            for(int i=0;i<50&&launched==null;i++)Thread.sleep(100);
            if(launched==null)throw new AssertionError("Launcher activity did not start");
            activity=launched;
            MainActivity target=activity;Thread.sleep(1200);
            TextView time=target.findViewById(R.id.clock),seconds=target.findViewById(R.id.clock_seconds);
            TextView temperature=target.findViewById(R.id.here_temp),date=target.findViewById(R.id.date);
            TextView away=target.findViewById(R.id.away_clock);
            View tabs=target.findViewById(R.id.screen_tabs),weather=target.findViewById(R.id.here_weather);
            int[] initial={0};String[] first={""};
            ui(()->{
                check(time instanceof RollingTimeView&&seconds instanceof RollingTimeView,"both time components support digit motion");
                check(away instanceof RollingTimeView,"Saudi clock supports the same transition");
                check(away.getText().toString().chars().filter(c->c==':').count()==1,"Saudi clock retains hours and minutes format");
                check(time.getLineCount()==1,"hours and minutes stay on one line");
                check(Math.abs(seconds.getTextSize()-temperature.getTextSize())<.01,"seconds match current temperature size");
                check(Math.abs(time.getTextSize()-75*target.getResources().getDisplayMetrics().scaledDensity)<.01,"large clock retains its original size");
                check(seconds.getText().toString().matches(":\\d{2}"),"seconds include leading zero and separator");
                check(Math.abs(time.getTop()+time.getBaseline()-seconds.getTop()-seconds.getBaseline()-seconds.getTranslationY())<1,
                        "hours minutes and seconds share a baseline");
                initial[0]=tabs.getTop();first[0]=seconds.getText().toString();
                if(weather.getVisibility()==View.VISIBLE){
                    int end=date.getText().toString().indexOf('\n');if(end<0)end=date.length();
                    float dateRight=date.getLeft()+date.getCompoundPaddingLeft()+date.getLayout().getPrimaryHorizontal(end);
                    float weatherRight=((View)weather.getParent()).getLeft()+weather.getRight()+weather.getTranslationX();
                    check(Math.abs(dateRight-weatherRight)<1,"weather right edge aligns with end of Hijri year: "+dateRight+" vs "+weatherRight);
                }
            });
            Thread.sleep(1300);
            ui(()->{
                check(!first[0].equals(seconds.getText().toString()),"seconds advance on the running TV clock");
                check(initial[0]==tabs.getTop(),"per-second updates do not move navigation tabs");
                try{
                    Method render=MainActivity.class.getDeclaredMethod("renderLocalTime",ZonedDateTime.class,boolean.class);render.setAccessible(true);
                    try{
                        render.invoke(target,ZonedDateTime.parse("2026-10-31T23:59:59+08:00"),true);
                        check(time.getText().toString().equals("23:59")&&seconds.getText().toString().equals(":59"),"end-of-day fixture renders");
                        render.invoke(target,ZonedDateTime.parse("2026-11-01T00:00:00+08:00"),true);
                        check(time.getText().toString().equals("00:00")&&seconds.getText().toString().equals(":00"),"hour minute second rollover renders");
                        render.invoke(target,ZonedDateTime.parse("2026-11-01T12:00:01+08:00"),false);
                        check(time.getText().toString().equals("12:00")&&seconds.getText().toString().equals(":01"),"twelve-hour formatting remains supported");
                    }finally{render.invoke(target,ZonedDateTime.now(),android.text.format.DateFormat.is24HourFormat(target));}
                }catch(Exception error){throw new AssertionError(error);}
            });
            result.putString("stream","PASS: "+checks+" clock/header checks on TV. Rollover uses fixtures; ticking and alignment checked on actual views.\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL after "+checks+" checks: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
