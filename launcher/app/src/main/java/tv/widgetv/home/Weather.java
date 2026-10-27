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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather and today's high/low from Open-Meteo (free, no key, no account): the launcher's only
 * network use. One request covers the prayer city and, when the second clock is on, the second city.
 * MainActivity calls maybeFetch() only while it is resumed (onResume and the minute tick, whose receiver
 * is registered only while resumed), so nothing is fetched while another app is in front. No retries,
 * alarms or services: a failed fetch is tried again at a later stale check (at most every 10 minutes).
 * The last result is cached in SharedPreferences together with the URL it came from, so editing the
 * cities or units in config.xml never shows the old cities' numbers.
 */
final class Weather {
    private static final String TAG = Config.TAG;
    private static final long STALE_MS = 30 * 60_000L, HIDE_MS = 3 * 60 * 60_000L, RETRY_GAP_MS = 10 * 60_000L;

    /** One city's reading, in the configured units. */
    static final class City {
        final int temp, code;
        final boolean day;
        /** Today's high/low in that city's own local day. */
        final int max, min;
        final boolean hasRange;

        City(int temp, int code, boolean day, int max, int min, boolean hasRange) {
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.max = max;
            this.min = min;
            this.hasRange = hasRange;
        }
    }

    private final SharedPreferences prefs;
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean enabled;
    /** 1 (prayer city) or 2 (plus the second clock's city). */
    private final int cities;
    private final String url;
    private boolean inFlight;
    private long lastAttempt;

    Weather(Context c) {
        prefs = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        Config cfg = Config.get(c);
        enabled = cfg.weatherEnabled;
        cities = cfg.secondEnabled ? 2 : 1;
        String lats = coord(cfg.lat) + (cities == 2 ? "," + coord(cfg.secondLat) : "");
        String lngs = coord(cfg.lng) + (cities == 2 ? "," + coord(cfg.secondLng) : "");
        url = "https://api.open-meteo.com/v1/forecast?latitude=" + lats + "&longitude=" + lngs
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
                + "&temperature_unit=" + cfg.weatherUnits;
    }

    private static String coord(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    /**
     * Index 0 the prayer city, 1 the second city (when enabled). null when weather is off, never
     * fetched for the current config, or older than 3 hours (hidden).
     */
    City[] current() {
        if (!enabled || !url.equals(prefs.getString("url", ""))) return null;
        long at = prefs.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > HIDE_MS) return null;
        City[] out = new City[cities];
        for (int i = 0; i < cities; i++) {
            out[i] = new City(prefs.getInt("t" + i, 0), prefs.getInt("c" + i, 0), prefs.getBoolean("d" + i, true),
                    prefs.getInt("x" + i, 0), prefs.getInt("n" + i, 0), prefs.contains("x" + i));
        }
        return out;
    }

    /** Fetch if the cache is older than 30 minutes (or force); onDone runs on the main thread after a success. */
    void maybeFetch(boolean force, Runnable onDone) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        boolean stale = !url.equals(prefs.getString("url", "")) || now - prefs.getLong("at", 0) > STALE_MS;
        if (inFlight || (!force && (!stale || now - lastAttempt < RETRY_GAP_MS))) return;
        inFlight = true;
        lastAttempt = now;
        Log.i(TAG, "weather: fetch start" + (force ? " (forced)" : ""));
        net.execute(() -> {
            boolean ok = fetch();
            main.post(() -> {
                inFlight = false;
                if (ok) onDone.run();
            });
        });
    }

    private boolean fetch() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setUseCaches(false);
            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "weather: HTTP " + conn.getResponseCode());
                return false;
            }
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) body.write(buf, 0, n);
            }
            // Open-Meteo answers one location with an object and several with an array.
            String json = body.toString("UTF-8").trim();
            JSONArray arr = json.startsWith("[") ? new JSONArray(json) : new JSONArray().put(new JSONObject(json));
            SharedPreferences.Editor e = prefs.edit().clear();
            StringBuilder log = new StringBuilder("weather: fetch ok");
            for (int i = 0; i < cities; i++) {
                JSONObject cur = arr.getJSONObject(i).getJSONObject("current");
                double t = cur.getDouble("temperature_2m");
                int code = cur.getInt("weather_code");
                boolean day = cur.getInt("is_day") == 1;
                JSONObject daily = arr.getJSONObject(i).getJSONObject("daily");
                double max = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double min = daily.getJSONArray("temperature_2m_min").getDouble(0);
                e.putInt("t" + i, (int) Math.round(t)).putInt("c" + i, code).putBoolean("d" + i, day)
                        .putInt("x" + i, (int) Math.round(max)).putInt("n" + i, (int) Math.round(min));
                log.append(i == 0 ? " here=" : " second=").append(t).append(" code=").append(code)
                        .append(" is_day=").append(day ? 1 : 0).append(" max=").append(max).append(" min=").append(min);
            }
            e.putString("url", url).putLong("at", System.currentTimeMillis()).apply();
            Log.i(TAG, log.toString());
            return true;
        } catch (Exception ex) {
            Log.w(TAG, "weather: fetch failed: " + ex);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** WMO weather code to one of the seven thin-line icons. */
    static int icon(City c) {
        int w = c.code;
        if (w == 0) return c.day ? R.drawable.wx_clear_day : R.drawable.wx_clear_night;
        if (w == 1 || w == 2) return c.day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (w == 3) return R.drawable.wx_cloudy;
        if (w == 45 || w == 48) return R.drawable.wx_fog;
        if ((w >= 71 && w <= 77) || w == 85 || w == 86) return R.drawable.wx_snow;
        if (w >= 95) return R.drawable.wx_thunder;
        if ((w >= 51 && w <= 67) || (w >= 80 && w <= 82)) return R.drawable.wx_rain;
        return R.drawable.wx_cloudy;
    }

    void shutdown() {
        net.shutdownNow();
    }
}
