package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** API-supplied Arabic and original Malay translation; one cached verse per prayer interval. */
final class PrayerVerse {
    static final String API = "https://api.mengaji.my/v1/";
    interface Source { String get(String url) throws Exception; }
    private final SharedPreferences cache;
    private final Source source;
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Entry saved;
    private long savedSlot = -1, attemptedSlot = -1, lastAttempt;
    private boolean inFlight, failed, closed;

    static final class Entry {
        final int id, surah, ayah;
        final String name, arabic, malay;
        Entry(int id, int surah, int ayah, String name, String arabic, String malay) {
            this.id=id; this.surah=surah; this.ayah=ayah; this.name=name; this.arabic=arabic; this.malay=malay;
        }
        CharSequence display() { return display(null); }
        CharSequence display(Typeface arabicFont) {
            SpannableStringBuilder text = new SpannableStringBuilder(arabic);
            text.setSpan(new RelativeSizeSpan(1.4f), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (arabicFont != null) text.setSpan(new TypefaceSpan(arabicFont), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append("\n\n").append(malay).append("\n\n");
            int referenceStart = text.length();
            text.append(reference());
            text.setSpan(new RelativeSizeSpan(.75f), referenceStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(Lapis.MIST_400), referenceStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return text;
        }
        String reference() { return "Surah " + name + " · " + surah + ":" + ayah; }
        JSONObject json() throws Exception {
            return new JSONObject().put("id",id).put("surah_id",surah).put("nombor_ayat",ayah)
                    .put("nama_surah",name).put("teks_arab",arabic).put("terjemahan_melayu",malay);
        }
    }

    PrayerVerse(Context context) { this(context, PrayerVerse::download); }
    PrayerVerse(Context context, Source source) {
        this.source=source; cache=context.getSharedPreferences("prayer_verse",Context.MODE_PRIVATE);
        try {
            JSONObject stored=new JSONObject(cache.getString("entry",""));
            saved=parseData(stored.getJSONObject("verse")); savedSlot=stored.getLong("slot");
        } catch(Exception ignored) { saved=null; savedSlot=-1; }
    }

    /** Stable through midnight and sunrise: only the five obligatory prayer times change the key. */
    static long slot(long now, long[] times, long yesterdayIsha) {
        if(times==null || times.length!=6 || yesterdayIsha<=0) throw new IllegalArgumentException("Prayer times missing");
        long start=yesterdayIsha;
        for(int p:PrayerTimeline.COLUMNS) {
            if(times[p]<=start) throw new IllegalArgumentException("Unordered prayer times");
            if(now>=times[p]) start=times[p]; else break;
        }
        return start;
    }
    Entry get(long slot) { return saved!=null && savedSlot==slot ? saved : null; }
    boolean failed() { return failed; }

    void maybeFetch(long slot, Runnable done) {
        long now=SystemClock.elapsedRealtime();
        if(closed || get(slot)!=null || inFlight || slot==attemptedSlot && now-lastAttempt<60_000) return;
        inFlight=true; attemptedSlot=slot; lastAttempt=now;
        int previous=saved==null ? -1 : saved.id;
        net.execute(()->{
            Entry loaded=null;
            try {
                loaded=parse(source.get(API+"random"));
                // A random response may repeat (or be cached upstream). Guarantee a new verse
                // with one bounded, documented lookup; never retry random requests in a loop.
                if(loaded.id==previous) {
                    int next=previous%6236+1;
                    loaded=parse(source.get(API+"ayat/"+next));
                    if(loaded.id!=next) throw new IOException("Quran API returned a mismatched verse");
                }
            } catch(Exception unavailable) { loaded=null; Log.w(Config.TAG,"quran: Arabic/Malay API unavailable",unavailable); }
            Entry result=loaded;
            main.post(()->{
                if(closed) return;
                inFlight=false; failed=result==null;
                if(result!=null) {
                    saved=result; savedSlot=slot;
                    try { cache.edit().putString("entry",new JSONObject().put("slot",slot).put("verse",result.json()).toString()).apply(); }
                    catch(Exception ignored) { }
                }
                done.run();
            });
        });
    }

    static Entry parse(String json) throws Exception {
        JSONObject root=new JSONObject(json);
        if(!root.getBoolean("success")) throw new IOException("Quran API reported an error");
        return parseData(root.getJSONObject("data"));
    }
    private static Entry parseData(JSONObject data) throws Exception {
        int id=data.getInt("id"), surah=data.getInt("surah_id"), ayah=data.getInt("nombor_ayat");
        String name=data.getString("nama_surah").trim(), arabic=data.getString("teks_arab").trim(), malay=data.getString("terjemahan_melayu").trim();
        if(id<1 || id>6236 || surah<1 || surah>114 || ayah<1 || ayah>286 || name.isEmpty() || name.length()>120
                || arabic.isEmpty() || malay.isEmpty() || "null".equalsIgnoreCase(malay) || arabic.length()>20000 || malay.length()>20000
                || !java.util.regex.Pattern.compile("[\\u0600-\\u06FF]").matcher(arabic).find()
                || arabic.contains("<") || malay.contains("<")) throw new IOException("Quran API text or reference missing/invalid");
        return new Entry(id,surah,ayah,name,arabic,malay);
    }

    static String download(String address) throws Exception {
        if(!address.equals(API+"random") && !address.matches("https://api\\.mengaji\\.my/v1/ayat/[1-9][0-9]{0,3}"))
            throw new IOException("Unexpected Quran API URL");
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        try {
            connection.setConnectTimeout(8000); connection.setReadTimeout(12000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","Octahedron-TV/3.0 (Arabic and Malay verse reader)");
            if(connection.getResponseCode()!=200) throw new IOException("Quran API HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>131072) throw new IOException("Quran API response too large");
                    out.write(buffer,0,count);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
    void shutdown() { closed=true; main.removeCallbacksAndMessages(null); net.shutdownNow(); }
}
