package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Tests only the daily-hadith schedule, parser and cache. No smart-device or other screen tests. */
public final class DailyHadithChecks extends Instrumentation {
    private int checks;
    private boolean fitOnly;
    private boolean shortPreview;
    @Override public void onCreate(Bundle args){super.onCreate(args);fitOnly=args!=null&&"true".equals(args.getString("fit_only"));shortPreview=args!=null&&"true".equals(args.getString("short_preview"));start();}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    @Override public void onStart(){
        if(fitOnly||shortPreview){checkAutoFit();return;}
        Bundle out=new Bundle();String file="hadith-test-"+java.util.UUID.randomUUID();
        Context context=new ContextWrapper(getTargetContext()){
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(file,mode);}
        };
        DailyHadith provider=null,reloaded=null;
        try{
            provider=new DailyHadith(context);
            LocalDate start=LocalDate.of(2026,1,1);Set<String> urls=new HashSet<>();
            for(int i=0;i<365;i++){
                String url=provider.url(start.plusDays(i));
                check(url!=null&&url.matches("https://myhadith\\.islam\\.gov\\.my/hadith/[0-9]+"),"official source day "+i);
                check(urls.add(url),"distinct scheduled page day "+i);
            }
            check(provider.url(start).equals(provider.url(start.plusDays(365))),"365-day wrap");
            check(DailyHadith.index(start.minusDays(1))==364,"date before anchor");
            LocalDate leap=LocalDate.of(2028,2,29);
            check((DailyHadith.index(leap.minusDays(1))+1)%365==DailyHadith.index(leap),"leap day included");
            check((DailyHadith.index(leap)+1)%365==DailyHadith.index(leap.plusDays(1)),"leap day next entry");
            DailyHadith.Entry sample=DailyHadith.parsePage("https://myhadith.islam.gov.my/hadith/1",
                    "<h2 class='hadith-card-title'>PARSER FIXTURE</h2><div class='arabic-text'><p>أَبْجَد</p><div>هَوَّز</div></div><div class='translation-text'><p>Exact &amp; unchanged.</p><p>Keep punctuation: ‘ ’.</p></div>");
            check(sample.arabic.contains("أَبْجَد")&&sample.arabic.contains("هَوَّز"),"nested original text and diacritics preserved");
            check(sample.translation.contains("Exact & unchanged.")&&sample.translation.contains("‘ ’"),"entities and punctuation preserved");
            check(sample.display().equals(sample.title+"\n"+sample.translation)&&!sample.display().contains(sample.arabic),"Malay only without paraphrase or truncation");
            boolean rejected=false;try{DailyHadith.parsePage("https://myhadith.islam.gov.my/hadith/1","<h2>Incomplete</h2>");}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"changed source format fails explicitly");
            LocalDate today=LocalDate.now(JakimProvider.MALAYSIA);
            DailyHadith target=provider;CountDownLatch done=new CountDownLatch(1);AtomicReference<DailyHadith.Entry> received=new AtomicReference<>();
            runOnMainSync(()->target.maybeFetch(today,()->{received.set(target.get(today));done.countDown();}));
            check(done.await(35,TimeUnit.SECONDS),"official daily page fetch completes");
            DailyHadith.Entry original=received.get();
            check(original!=null&&original.url.equals(provider.url(today)),"real official page parsed");
            check(!original.arabic.isEmpty()&&!original.translation.isEmpty(),"both original sections available");
            reloaded=new DailyHadith(context);
            check(reloaded.get(today)!=null&&reloaded.get(today).display().equals(original.display()),"original text restored from offline cache");
            check(reloaded.get(today.plusDays(1))==null,"cached previous entry is never labeled tomorrow's hadith");
            out.putString("stream","PASS: "+checks+" daily-hadith checks; real MyHadith fetch and isolated cache. No other screens/devices tested.\n");
            finish(Activity.RESULT_OK,out);
        }catch(Throwable e){out.putString("stream","FAIL after "+checks+" hadith checks: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");finish(Activity.RESULT_CANCELED,out);}
        finally{if(provider!=null)provider.shutdown();if(reloaded!=null)reloaded.shutdown();getTargetContext().deleteSharedPreferences(file);}
    }
    private void checkAutoFit(){
        Bundle result=new Bundle();Activity activity=null;String original=null;
        try{
            DailyHadith.Entry shortEntry=shortPreview?DailyHadith.parsePage("https://myhadith.islam.gov.my/hadith/1049",DailyHadith.download("https://myhadith.islam.gov.my/hadith/1049")):null;
            activity=startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity target=activity;
            runOnMainSync(()->{
                try{java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showScreen",int.class,boolean.class);show.setAccessible(true);show.invoke(target,1,false);}
                catch(Exception e){throw new RuntimeException(e);}
            });
            waitForIdleSync();android.os.SystemClock.sleep(150);
            android.widget.TextView body=activity.findViewById(R.id.thought_text),heading=activity.findViewById(R.id.thought_tag);
            original=body.getText().toString();float previous=Float.MAX_VALUE;
            if(shortEntry!=null){
                runOnMainSync(()->body.setText(shortEntry.display()));waitForIdleSync();android.os.SystemClock.sleep(250);
                runOnMainSync(()->{
                    android.text.Layout layout=body.getLayout();
                    check(layout.getLineEnd(layout.getLineCount()-1)==shortEntry.display().length(),"complete short original hadith");
                    check(layout.getHeight()<=body.getHeight()-body.getCompoundPaddingTop()-body.getCompoundPaddingBottom(),"short hadith fits");
                    check(body.getTextSize()==body.getAutoSizeMaxTextSize(),"short hadith uses largest configured content size");
                    check(heading.getTextSize()>body.getTextSize(),"heading stays larger");
                });
                Bundle preview=new Bundle();preview.putString("stream","SHORT PREVIEW READY: original MyHadith 1049; largest content font; automatically restores in 30 seconds.\n");sendStatus(0,preview);
                android.os.SystemClock.sleep(30_000);
            }
            if(!shortPreview)
            for(int words:new int[]{40,200,800,2000}){
                StringBuilder fixture=new StringBuilder("UJIAN SUSUN ATUR\n");
                for(int i=0;i<words;i++)fixture.append("Perkataan ujian ");
                String text=fixture.toString();runOnMainSync(()->body.setText(text));
                waitForIdleSync();android.os.SystemClock.sleep(150);
                float before=previous;
                runOnMainSync(()->{
                    android.text.Layout layout=body.getLayout();
                    check(layout!=null&&layout.getLineEnd(layout.getLineCount()-1)==text.length(),"complete text laid out: "+words);
                    check(layout.getHeight()<=body.getHeight()-body.getCompoundPaddingTop()-body.getCompoundPaddingBottom(),"no vertical clipping: "+words);
                    check(heading.getTextSize()>body.getTextSize(),"heading larger: "+words);
                    check(body.getTextSize()<=before,"longer text never increases size: "+words);
                    check(!(body.getParent() instanceof android.widget.ScrollView),"no scroll container");
                });
                previous=body.getTextSize();
            }
            String saved=original;runOnMainSync(()->body.setText(saved));waitForIdleSync();
            result.putString("stream","PASS: "+checks+(shortPreview?" original short-hadith TV preview checks; daily content restored.":" TV auto-fit checks across short and long synthetic entries. No network fetch.")+" No device controls.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL auto-fit after "+checks+" checks: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{if(activity!=null&&original!=null){android.widget.TextView body=activity.findViewById(R.id.thought_text);String saved=original;runOnMainSync(()->body.setText(saved));}}
    }
}
