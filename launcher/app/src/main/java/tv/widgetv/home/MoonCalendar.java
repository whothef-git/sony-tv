package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Time-specific USNO illumination, refreshed every fifteen minutes while visible. */
final class MoonCalendar {
    static final class Entry {
        final LocalDate date;
        final String phase;
        final float illuminated;
        final boolean waxing;
        Entry(LocalDate date,String phase,float illuminated,boolean waxing){this.date=date;this.phase=phase;this.illuminated=illuminated;this.waxing=waxing;}
    }
    private final SharedPreferences cache;
    private final Config config;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Entry entry;
    private String loadedUrl,attemptedUrl;
    private long attemptedAt,loadedAt;
    static final long INTERVAL_MS=15*60_000L, MAX_AGE_MS=30*60_000L;
    private boolean pending,closed;
    MoonCalendar(Context context){
        cache=context.getSharedPreferences("moon_calendar",Context.MODE_PRIVATE);config=Config.get(context);
        try{loadedUrl=cache.getString("instant_url","");loadedAt=cache.getLong("instant_at",0);entry=parseAt(cache.getString("instant_response",""),loadedAt);}
        catch(Exception ignored){entry=null;}
    }
    static LocalDate date(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();}
    static long bucket(long now){return Math.floorDiv(now,INTERVAL_MS)*INTERVAL_MS;}
    String url(long now){
        ZonedDateTime utc=Instant.ofEpochMilli(bucket(now)).atZone(ZoneOffset.UTC);
        return String.format(Locale.US,"https://aa.usno.navy.mil/api/celnav?ID=OctaTV&date=%s&time=%02d:%02d:00&coords=%.6f,%.6f",
                utc.toLocalDate(),utc.getHour(),utc.getMinute(),config.lat,config.lng);
    }
    Entry get(long now){
        long age=now-loadedAt;
        return entry!=null&&age>=0&&age<MAX_AGE_MS&&url(loadedAt).equals(loadedUrl)?entry:null;
    }
    void maybeFetch(long now,Runnable changed){
        String requested=url(now);long elapsed=SystemClock.elapsedRealtime();
        if(closed||pending||requested.equals(loadedUrl)&&get(now)!=null||requested.equals(attemptedUrl)&&elapsed-attemptedAt<5*60_000L)return;
        pending=true;attemptedUrl=requested;attemptedAt=elapsed;long requestedAt=bucket(now);
        worker.execute(()->{
            String response=null;Entry result=null;
            try{response=download(requested);result=parseAt(response,requestedAt);}
            catch(Exception e){Log.w(Config.TAG,"moon: time-specific calendar unavailable ("+e.getClass().getSimpleName()+")");}
            String body=response;Entry parsed=result;
            main.post(()->{
                if(closed)return;pending=false;
                if(parsed!=null){entry=parsed;loadedUrl=requested;loadedAt=requestedAt;cache.edit().putString("instant_url",requested).putLong("instant_at",requestedAt).putString("instant_response",body).apply();Log.i(Config.TAG,"moon: time-specific phase loaded "+parsed.phase+" "+Math.round(parsed.illuminated*100)+"%");changed.run();}
            });
        });
    }
    static Entry parseAt(String response,long expected)throws Exception{
        JSONObject root=new JSONObject(response);
        if(root.has("error"))throw new IllegalArgumentException("Calendar error");
        JSONObject data=root.getJSONObject("properties");
        ZonedDateTime utc=Instant.ofEpochMilli(expected).atZone(ZoneOffset.UTC);
        LocalDate actual=LocalDate.of(data.getInt("year"),data.getInt("month"),data.getInt("day"));
        java.time.LocalTime time=java.time.LocalTime.parse(data.getString("time"));
        if(!actual.equals(utc.toLocalDate())||!time.equals(utc.toLocalTime())||data.getDouble("tz")!=0)
            throw new IllegalArgumentException("Wrong calendar instant");
        Object illumination=data.opt("moon_illum");
        if(!(illumination instanceof Number))throw new IllegalArgumentException("Moon not visible or illumination missing");
        double percent=((Number)illumination).doubleValue();
        if(!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Invalid illumination");
        String phase=data.getString("moon_phase");
        return phaseEntry(date(expected),phase,(float)(percent/100));
    }
    private static Entry phaseEntry(LocalDate date,String phase,float illuminated){
        boolean waxing;
        switch(phase){
            case "New Moon":case "Waxing Crescent":case "First Quarter":case "Waxing Gibbous":case "Full Moon":waxing=true;break;
            case "Waning Gibbous":case "Last Quarter":case "Waning Crescent":waxing=false;break;
            default:throw new IllegalArgumentException("Unknown lunar phase");
        }
        return new Entry(date,phase,illuminated,waxing);
    }
    static Entry parse(String response,LocalDate expected)throws Exception{
        JSONObject root=new JSONObject(response);
        if(root.has("error"))throw new IllegalArgumentException("Calendar error");
        JSONObject data=root.getJSONObject("properties").getJSONObject("data");
        LocalDate actual=LocalDate.of(data.getInt("year"),data.getInt("month"),data.getInt("day"));
        if(!actual.equals(expected))throw new IllegalArgumentException("Wrong calendar date");
        String phase=data.getString("curphase");
        String fraction=data.getString("fracillum");
        if(!fraction.matches("[0-9]+(?:\\.[0-9]+)?%"))throw new IllegalArgumentException("Invalid illumination");
        float lit=Float.parseFloat(fraction.substring(0,fraction.length()-1))/100f;
        if(!Float.isFinite(lit)||lit<0||lit>1)throw new IllegalArgumentException("Invalid illumination");
        return phaseEntry(actual,phase,lit);
    }
    static String download(String address)throws Exception{
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())||!"aa.usno.navy.mil".equals(url.getHost()))throw new IllegalArgumentException("Unofficial calendar");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        try{
            c.setConnectTimeout(8_000);c.setReadTimeout(12_000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Octahedron-TV/2.1 (time-specific lunar calendar)");
            if(c.getResponseCode()!=200)throw new java.io.IOException("Calendar HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];for(int n;(n=in.read(buf))!=-1;){if(out.size()+n>65_536)throw new java.io.IOException("Calendar response too large");out.write(buf,0,n);}
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    void shutdown(){closed=true;worker.shutdownNow();}
}
