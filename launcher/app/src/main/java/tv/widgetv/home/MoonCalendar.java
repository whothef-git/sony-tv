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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Official daily lunar phase; one small HTTPS response per local date, with a private offline cache. */
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
    private long attemptedAt;
    private boolean pending,closed;
    MoonCalendar(Context context){
        cache=context.getSharedPreferences("moon_calendar",Context.MODE_PRIVATE);config=Config.get(context);
        try{loadedUrl=cache.getString("url","");entry=parse(cache.getString("response",""),date(System.currentTimeMillis()));}
        catch(Exception ignored){entry=null;}
    }
    static LocalDate date(long now){return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();}
    String url(long now){
        double tz=ZoneId.systemDefault().getRules().getOffset(Instant.ofEpochMilli(now)).getTotalSeconds()/3600.0;
        return String.format(Locale.US,"https://aa.usno.navy.mil/api/rstt/oneday?ID=OctaTV&date=%s&coords=%.6f,%.6f&tz=%.2f",date(now),config.lat,config.lng,tz);
    }
    Entry get(long now){return entry!=null&&entry.date.equals(date(now))&&url(now).equals(loadedUrl)?entry:null;}
    void maybeFetch(long now,Runnable changed){
        String requested=url(now);long elapsed=SystemClock.elapsedRealtime();
        if(closed||pending||get(now)!=null||requested.equals(attemptedUrl)&&elapsed-attemptedAt<30*60_000L)return;
        pending=true;attemptedUrl=requested;attemptedAt=elapsed;LocalDate day=date(now);
        worker.execute(()->{
            String response=null;Entry result=null;
            try{response=download(requested);result=parse(response,day);}
            catch(Exception e){Log.w(Config.TAG,"moon: official daily calendar unavailable ("+e.getClass().getSimpleName()+")");}
            String body=response;Entry parsed=result;
            main.post(()->{
                if(closed)return;pending=false;
                if(parsed!=null){entry=parsed;loadedUrl=requested;cache.edit().putString("url",requested).putString("response",body).apply();Log.i(Config.TAG,"moon: official phase loaded for "+day+" "+parsed.phase);changed.run();}
            });
        });
    }
    static Entry parse(String response,LocalDate expected)throws Exception{
        JSONObject root=new JSONObject(response);
        if(root.has("error"))throw new IllegalArgumentException("Calendar error");
        JSONObject data=root.getJSONObject("properties").getJSONObject("data");
        LocalDate actual=LocalDate.of(data.getInt("year"),data.getInt("month"),data.getInt("day"));
        if(!actual.equals(expected))throw new IllegalArgumentException("Wrong calendar date");
        String phase=data.getString("curphase");
        boolean waxing;
        switch(phase){
            case "New Moon":case "Waxing Crescent":case "First Quarter":case "Waxing Gibbous":case "Full Moon":waxing=true;break;
            case "Waning Gibbous":case "Last Quarter":case "Waning Crescent":waxing=false;break;
            default:throw new IllegalArgumentException("Unknown lunar phase");
        }
        String fraction=data.getString("fracillum");
        if(!fraction.matches("[0-9]+(?:\\.[0-9]+)?%"))throw new IllegalArgumentException("Invalid illumination");
        float lit=Float.parseFloat(fraction.substring(0,fraction.length()-1))/100f;
        if(!Float.isFinite(lit)||lit<0||lit>1)throw new IllegalArgumentException("Invalid illumination");
        return new Entry(actual,phase,lit,waxing);
    }
    static String download(String address)throws Exception{
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())||!"aa.usno.navy.mil".equals(url.getHost()))throw new IllegalArgumentException("Unofficial calendar");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        try{
            c.setConnectTimeout(8_000);c.setReadTimeout(12_000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Octahedron-TV/2.1 (daily lunar calendar)");
            if(c.getResponseCode()!=200)throw new java.io.IOException("Calendar HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];for(int n;(n=in.read(buf))!=-1;){if(out.size()+n>65_536)throw new java.io.IOException("Calendar response too large");out.write(buf,0,n);}
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
    void shutdown(){closed=true;worker.shutdownNow();}
}
