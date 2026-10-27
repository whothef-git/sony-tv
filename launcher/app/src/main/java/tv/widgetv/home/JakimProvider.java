package tv.widgetv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fixed SGR01 schedule and Hijri dates, from JAKIM only; cached for offline operation. */
final class JakimProvider {
    static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final Map<String, Entry> cache = new HashMap<>();
    private static final ExecutorService net = Executors.newSingleThreadExecutor();
    private static boolean loaded, inFlight;
    private static long lastAttempt;
    static final class Entry {
        final int[] minutes;
        final int hijriYear, hijriMonth, hijriDay;
        Entry(int[] minutes, int year, int month, int day) {
            this.minutes = minutes; hijriYear = year; hijriMonth = month; hijriDay = day;
        }
    }
    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) != -1;) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
    private static Entry entry(JSONArray values, String hijri) throws Exception {
        if (values.length() != 6) throw new IllegalArgumentException("Six times required");
        int[] minutes = new int[6];
        for (int i = 0; i < 6; i++) {
            minutes[i] = values.getInt(i);
            if (minutes[i] < 0 || minutes[i] >= 1440 || (i > 0 && minutes[i] <= minutes[i - 1]))
                throw new IllegalArgumentException("Invalid JAKIM time ordering");
        }
        String[] h = hijri.split("-");
        int year = Integer.parseInt(h[0]), month = Integer.parseInt(h[1]), day = Integer.parseInt(h[2]);
        if (month < 1 || month > 12 || day < 1 || day > 30) throw new IllegalArgumentException("Invalid Hijri date");
        return new Entry(minutes, year, month - 1, day);
    }
    static Map<String, Entry> parseOfficial(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if (!"SGR01".equals(root.optString("zone"))) throw new IllegalArgumentException("Wrong JAKIM zone");
        JSONArray days = root.getJSONArray("prayerTime");
        Map<String, Entry> parsed = new HashMap<>();
        String[] fields = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH);
        for (int i = 0; i < days.length(); i++) {
            JSONObject item = days.getJSONObject(i);
            JSONArray minutes = new JSONArray();
            for (String field : fields) {
                String[] hm = item.getString(field).split(":");
                int hour = Integer.parseInt(hm[0]), minute = Integer.parseInt(hm[1]);
                if (hour > 23 || hour < 0 || minute > 59 || minute < 0) throw new IllegalArgumentException("Invalid clock time");
                minutes.put(hour * 60 + minute);
            }
            String civilDate = item.getString("date").replace("-Mac-", "-Mar-").replace("-Mei-", "-May-")
                    .replace("-Ogos-", "-Aug-").replace("-Ogo-", "-Aug-").replace("-Okt-", "-Oct-").replace("-Dis-", "-Dec-");
            parsed.put(LocalDate.parse(civilDate, date).toString(), entry(minutes, item.getString("hijri")));
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("Empty JAKIM schedule");
        return parsed;
    }
    private static synchronized void ensureLoaded(Context ctx) {
        if (loaded || ctx == null) return;
        try (InputStream in = ctx.getAssets().open("jakim_sgr01.json")) {
            JSONObject bundled = new JSONObject(read(in));
            java.util.Iterator<String> keys = bundled.keys();
            while (keys.hasNext()) {
                String key = keys.next(); JSONObject item = bundled.getJSONObject(key);
                cache.put(key, entry(item.getJSONArray("times"), item.getString("hijri")));
            }
        } catch (Exception ex) { Log.w(Config.TAG, "jakim: bundled schedule unavailable", ex); }
        try {
            String raw = ctx.getSharedPreferences("jakim", Context.MODE_PRIVATE).getString("official", "");
            if (!raw.isEmpty()) cache.putAll(parseOfficial(raw));
        } catch (Exception ex) { Log.w(Config.TAG, "jakim: cached response invalid", ex); }
        loaded = true;
        Log.i(Config.TAG, "jakim: loaded " + cache.size() + " official days");
    }
    static synchronized Entry get(Context ctx, LocalDate date) {
        ensureLoaded(ctx);
        return cache.get(date.toString());
    }
    static synchronized void maybeRefresh(Context ctx, Runnable onDone) {
        Context app = ctx.getApplicationContext();
        ensureLoaded(app);
        SharedPreferences prefs = app.getSharedPreferences("jakim", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now(MALAYSIA);
        boolean complete = cache.containsKey(today.toString()) && cache.containsKey(today.plusDays(1).toString());
        if (inFlight || now - lastAttempt < 10 * 60_000L ||
                (complete && now - prefs.getLong("updated", 0) < 24 * 3600_000L)) return;
        inFlight = true; lastAttempt = now;
        net.execute(() -> {
            HttpURLConnection conn = null;
            boolean ok = false;
            try {
                String url = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone=SGR01&year=" + today.getYear();
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
                String raw;
                try (InputStream in = conn.getInputStream()) { raw = read(in); }
                Map<String, Entry> parsed = parseOfficial(raw);
                if (!parsed.containsKey(today.toString())) throw new IllegalArgumentException("JAKIM response lacks current date");
                synchronized (JakimProvider.class) { cache.putAll(parsed); }
                prefs.edit().putString("official", raw).putLong("updated", now).apply();
                Log.i(Config.TAG, "jakim: refreshed official SGR01 schedule, " + parsed.size() + " days");
                ok = true;
            } catch (Exception ex) { Log.w(Config.TAG, "jakim: refresh failed; retaining official cache: " + ex); }
            finally { if (conn != null) conn.disconnect(); }
            boolean changed = ok;
            new Handler(Looper.getMainLooper()).post(() -> {
                synchronized (JakimProvider.class) { inFlight = false; }
                if (changed) onDone.run();
            });
        });
    }
    static boolean populate(Context ctx, long now, long[] times, long[] nightEdges, int[] hijriOut, int[] ignoredOffsets) {
        LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(MALAYSIA).toLocalDate();
        Entry e = get(ctx, today);
        if (e == null) return false;
        long midnight = today.atStartOfDay(MALAYSIA).toInstant().toEpochMilli();
        for (int i = 0; i < 6; i++) times[i] = midnight + e.minutes[i] * 60_000L;
        Entry yesterday = get(ctx, today.minusDays(1)), tomorrow = get(ctx, today.plusDays(1));
        nightEdges[0] = yesterday == null ? times[0] : midnight - 86400_000L + yesterday.minutes[5] * 60_000L;
        nightEdges[1] = tomorrow == null ? Long.MAX_VALUE : midnight + 86400_000L + tomorrow.minutes[0] * 60_000L;
        hijriOut[0] = e.hijriDay; hijriOut[1] = e.hijriMonth; hijriOut[2] = e.hijriYear;
        return true;
    }
}
