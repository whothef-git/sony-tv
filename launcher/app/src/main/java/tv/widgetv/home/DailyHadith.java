package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A fixed 365-page rotation. Original text is read from MyHadith, not authored or bundled here. */
final class DailyHadith {
    static final int DAYS=365;
    private static final LocalDate ANCHOR=LocalDate.of(2026,1,1);
    private static final String PREFIX="https://myhadith.islam.gov.my/hadith/";
    private final List<String> urls=new ArrayList<>();
    private final SharedPreferences cache;
    private final ExecutorService net=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Entry entry;
    private boolean inFlight, failed, closed;
    private String attempted;
    private long lastAttempt;

    static final class Entry {
        final String url,title,arabic,translation;
        Entry(String url,String title,String arabic,String translation){this.url=url;this.title=title;this.arabic=arabic;this.translation=translation;}
        String display(){return title+"\n\nMatan\n"+arabic+"\n\nMaksud Hadith\n"+translation;}
        JSONObject json() throws Exception {return new JSONObject().put("url",url).put("title",title).put("arabic",arabic).put("translation",translation);}
    }

    DailyHadith(Context context){
        cache=context.getSharedPreferences("daily_hadith",Context.MODE_PRIVATE);
        try(InputStream in=context.getResources().openRawResource(R.raw.hadith_rotation)){
            JSONArray list=new JSONObject(read(in)).getJSONArray("urls");
            for(int i=0;i<list.length();i++){
                String url=list.getString(i);
                if(!url.matches("https://myhadith\\.islam\\.gov\\.my/hadith/[0-9]+"))throw new IllegalArgumentException("Unexpected hadith source");
                urls.add(url);
            }
            if(urls.size()!=DAYS||new HashSet<>(urls).size()!=DAYS)throw new IllegalArgumentException("Rotation must have 365 distinct official pages");
        }catch(Exception e){urls.clear();Log.w(Config.TAG,"hadith: source schedule unavailable",e);}
        try{
            JSONObject o=new JSONObject(cache.getString("entry",""));
            Entry saved=new Entry(o.getString("url"),o.getString("title"),o.getString("arabic"),o.getString("translation"));
            if(urls.contains(saved.url)&&!saved.arabic.isEmpty()&&!saved.translation.isEmpty())entry=saved;
        }catch(Exception ignored){ }
    }

    static int index(LocalDate day){return (int)Math.floorMod(day.toEpochDay()-ANCHOR.toEpochDay(),(long)DAYS);}
    String url(LocalDate day){return urls.size()==DAYS?urls.get(index(day)):null;}
    Entry get(LocalDate day){String wanted=url(day);return entry!=null&&entry.url.equals(wanted)?entry:null;}
    boolean failed(){return failed||urls.size()!=DAYS;}

    void maybeFetch(LocalDate day,Runnable done){
        String wanted=url(day);long now=android.os.SystemClock.elapsedRealtime();
        if(closed||wanted==null||get(day)!=null||inFlight||wanted.equals(attempted)&&now-lastAttempt<60_000)return;
        inFlight=true;attempted=wanted;lastAttempt=now;
        net.execute(()->{
            Entry loaded=null;
            try{loaded=parsePage(wanted,download(wanted));}catch(Exception e){Log.w(Config.TAG,"hadith: official page unavailable",e);}
            final Entry result=loaded;
            main.post(()->{
                if(closed)return;
                inFlight=false;failed=result==null;
                if(result!=null){entry=result;try{cache.edit().putString("entry",result.json().toString()).apply();}catch(Exception ignored){ }}
                done.run();
            });
        });
    }

    static Entry parsePage(String url,String page){
        if(!url.startsWith(PREFIX))throw new IllegalArgumentException("Unofficial source");
        String title=plain(element(page,"h2","hadith-card-title"));
        String arabic=plain(element(page,"div","arabic-text"));
        String translation=plain(element(page,"div","translation-text"));
        if(title.isEmpty()||arabic.isEmpty()||translation.isEmpty())throw new IllegalArgumentException("Original hadith sections missing");
        return new Entry(url,title,arabic,translation);
    }

    /** Extract the complete matching HTML element, including nested paragraphs/divs. */
    private static String element(String page,String tag,String css){
        Pattern start=Pattern.compile("<"+tag+"\\b[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\b"+css+"\\b[^\"']*[\"'][^>]*>",Pattern.CASE_INSENSITIVE);
        Matcher first=start.matcher(page);
        if(!first.find())throw new IllegalArgumentException("Hadith page format changed: "+css);
        int begin=first.end(),depth=1;
        Matcher tags=Pattern.compile("</?"+tag+"\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(page);
        tags.region(begin,page.length());
        while(tags.find()){
            if(tags.group().startsWith("</"))depth--;else if(!tags.group().endsWith("/>"))depth++;
            if(depth==0)return page.substring(begin,tags.start());
        }
        throw new IllegalArgumentException("Incomplete hadith page");
    }

    private static String plain(String value){return Html.fromHtml(value,Html.FROM_HTML_MODE_LEGACY).toString().trim();}
    private static String read(InputStream in)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];
        for(int n;(n=in.read(buf))!=-1;){if(out.size()+n>1_048_576)throw new java.io.IOException("Hadith page too large");out.write(buf,0,n);}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    static String download(String address)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        try{
            connection.setConnectTimeout(10_000);connection.setReadTimeout(15_000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Octahedron-TV/2.1 (daily MyHadith reader)");
            if(connection.getResponseCode()!=200)throw new java.io.IOException("MyHadith HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream()){return read(in);}
        }finally{connection.disconnect();}
    }
    void shutdown(){closed=true;net.shutdownNow();}
}
