package tv.widgetv.home;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Layout;
import android.widget.TextView;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Quran card checks only: mocked rotation/cache failures plus real API HTTPS on the Sony TV. */
public final class PrayerVerseChecks extends Instrumentation {
    private int checks;
    private void check(boolean value,String why) { if(!value)throw new AssertionError(why); checks++; }
    private String fixture(int id) throws Exception {
        JSONObject verse=new JSONObject().put("id",id).put("surah_id",1).put("nombor_ayat",id)
                .put("nama_surah","Parser fixture").put("teks_arab","\u0628\u0650 \u062a\u064e")
                .put("terjemahan_melayu","Teks ujian. Kekalkan tanda baca: ‘ ’.");
        return new JSONObject().put("success",true).put("data",verse).toString();
    }
    private void fetch(PrayerVerse provider,long slot) throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(()->provider.maybeFetch(slot,done::countDown));
        check(done.await(25,TimeUnit.SECONDS),"fetch completes asynchronously");
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle out=new Bundle(); String file="verse-check-"+java.util.UUID.randomUUID();
        Context context=new ContextWrapper(getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String n,int m) { return super.getSharedPreferences(file,m); }
        };
        PrayerVerse provider=null,reloaded=null,unavailable=null;
        try {
            long base=LocalDate.of(2026,10,30).atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant().toEpochMilli();
            long[] times={base+6*3600000L,base+7*3600000L,base+13*3600000L,base+16*3600000L,base+19*3600000L,base+20*3600000L};
            long yesterday=base-4*3600000L;
            check(PrayerVerse.slot(base,times,yesterday)==yesterday,"midnight keeps previous Isyak");
            for(int p:PrayerTimeline.COLUMNS) {
                long previous=p==PrayerDay.FAJR?yesterday:PrayerVerse.slot(times[p]-1,times,yesterday);
                check(PrayerVerse.slot(times[p],times,yesterday)==times[p],"rotates at prayer boundary "+p);
                check(previous!=times[p],"does not rotate before boundary "+p);
            }
            check(PrayerVerse.slot(times[PrayerDay.SUNRISE],times,yesterday)==times[PrayerDay.FAJR],"Syuruk does not rotate");
            long[] tomorrow=times.clone();for(int i=0;i<6;i++)tomorrow[i]+=86400000L;
            check(PrayerVerse.slot(base+86400000L,tomorrow,times[PrayerDay.ISHA])==times[PrayerDay.ISHA],"Isyak persists across midnight");
            PrayerVerse.Entry entry=PrayerVerse.parse(fixture(1));
            check(entry.display().toString().equals(entry.arabic+"\n\n"+entry.malay+"\n\n"+entry.reference()),"Arabic and exact Malay preserved with spacing and reference directly below translation");
            for(String bad:new String[]{"{}","{\"success\":false}",fixture(1).replace("terjemahan_melayu","wrong"),fixture(1).replace("teks_arab","wrong")}) {
                boolean rejected=false;try{PrayerVerse.parse(bad);}catch(Exception expected){rejected=true;}check(rejected,"invalid API data rejected");
            }
            AtomicInteger requests=new AtomicInteger();
            provider=new PrayerVerse(context,url->{requests.incrementAndGet();return fixture(url.endsWith("ayat/2")?2:1);});
            fetch(provider,times[0]);check(provider.get(times[0])!=null,"first prayer verse cached");
            PrayerVerse same=provider;runOnMainSync(()->same.maybeFetch(times[0],()->{}));
            check(requests.get()==1,"reopening same prayer never changes or refetches verse");
            reloaded=new PrayerVerse(context,url->{throw new java.io.IOException("offline fixture");});
            check(reloaded.get(times[0])!=null,"same interval survives restart offline");
            check(reloaded.get(times[2])==null,"old interval not mislabeled as current");
            fetch(provider,times[2]);check(provider.get(times[2]).id==2&&requests.get()==3,"duplicate random result uses one next-verse lookup");
            unavailable=new PrayerVerse(context,url->{throw new java.io.IOException("offline fixture");});
            fetch(unavailable,times[3]);check(unavailable.get(times[3])==null&&unavailable.failed(),"outage shown without replacing current text with fake content");
            AtomicInteger retries=new AtomicInteger();PrayerVerse failed=unavailable;
            runOnMainSync(()->failed.maybeFetch(times[3],retries::incrementAndGet));
            check(retries.get()==0,"failed requests are rate limited");
            PrayerVerse.Entry live=PrayerVerse.parse(PrayerVerse.download(PrayerVerse.API+"ayat/1"));
            check(live.id==1&&live.surah==1&&live.ayah==1,"real Arabic/Malay API response verified over TV HTTPS");
            PrayerVerse.Entry longest=PrayerVerse.parse(PrayerVerse.download(PrayerVerse.API+"ayat/289"));
            check(longest.surah==2&&longest.ayah==282,"longest Quran verse loaded with complete Arabic and Malay");
            java.util.concurrent.atomic.AtomicReference<Throwable> uiFailure=new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(()->{ try {
                TextView text=new TextView(getTargetContext());
                text.setLayoutParams(new android.view.ViewGroup.LayoutParams(1600,290));
                text.setTypeface(getTargetContext().getResources().getFont(R.font.alexandria));
                android.graphics.Typeface arabicFont=getTargetContext().getResources().getFont(R.font.uthmanic_hafs);
                android.text.Spanned styled=(android.text.Spanned)live.display(arabicFont);
                android.text.style.TypefaceSpan[] fonts=styled.getSpans(0,styled.length(),android.text.style.TypefaceSpan.class);
                check(fonts.length==1&&styled.getSpanEnd(fonts[0])==live.arabic.length(),"Uthmanic font applies only to Arabic");
                check(styled.toString().indexOf(live.reference())>styled.toString().indexOf(live.malay),"surah and verse reference follow Malay translation");
                text.setAutoSizeTextTypeUniformWithConfiguration(1,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                text.setGravity(android.view.Gravity.TOP|android.view.Gravity.CENTER_HORIZONTAL);
                text.setTextDirection(android.view.View.TEXT_DIRECTION_FIRST_STRONG);
                text.setLineSpacing(0,1.12f);
                for(CharSequence sample:new CharSequence[]{live.display(arabicFont),longest.display(arabicFont)}) {
                    text.setText(sample);
                    for(int pass=0;pass<2;pass++) { text.measure(android.view.View.MeasureSpec.makeMeasureSpec(1600,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(290,1073741824));text.layout(0,0,1600,290); }
                    Layout layout=text.getLayout();check(layout!=null&&layout.getHeight()<=290&&layout.getLineEnd(layout.getLineCount()-1)==sample.length(),"short/long bilingual content fits completely: size="+text.getTextSize()+", height="+(layout==null?-1:layout.getHeight())+", length="+sample.length());
                }
            } catch(Throwable failure) { uiFailure.set(failure); } });
            if(uiFailure.get()!=null) throw new AssertionError("Bilingual layout check failed: "+uiFailure.get(),uiFailure.get());
            out.putString("stream","PASS: "+checks+" Quran verse checks, including real TV HTTPS, five prayer boundaries, midnight, duplicate prevention, cache and bilingual fit. No device controls tested.\n");finish(Activity.RESULT_OK,out);
        } catch(Throwable failure) {out.putString("stream","FAIL after "+checks+" Quran checks: "+failure+"\n");finish(Activity.RESULT_CANCELED,out);}
        finally { if(provider!=null)provider.shutdown();if(reloaded!=null)reloaded.shutdown();if(unavailable!=null)unavailable.shutdown();getTargetContext().deleteSharedPreferences(file); }
    }
}
