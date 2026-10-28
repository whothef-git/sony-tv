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
import java.util.ArrayList;
import java.util.List;
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
        final int apparentTemp, humidity, windSpeed;
        final boolean day;
        /** Today's high/low in that city's own local day. */
        final int max, min;
        final boolean hasRange;

        City(int temp, int code, boolean day, int max, int min, boolean hasRange) {
            this(temp, code, day, max, min, hasRange, -1, -1, -1);
        }

        City(int temp, int code, boolean day, int max, int min, boolean hasRange,
                int apparentTemp, int humidity, int windSpeed) {
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.max = max;
            this.min = min;
            this.hasRange = hasRange;
            this.apparentTemp = apparentTemp;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
        }
    }

    static final class Hour {
        final long at;
        final int temp, code, rain, humidity, apparentTemp, windSpeed;
        final boolean day;
        Hour(long at, int temp, int code, int rain, boolean day, int humidity, int apparentTemp, int windSpeed) {
            this.at = at; this.temp = temp; this.code = code; this.rain = rain; this.day = day;
            this.humidity = humidity;
            this.apparentTemp = apparentTemp; this.windSpeed = windSpeed;
        }
    }
    static final class Day {
        final long at;
        final int min, max, code, rainMax;
        Day(long at, int min, int max, int code, int rainMax) {
            this.at = at; this.min = min; this.max = max; this.code = code; this.rainMax = rainMax;
        }
    }
    static final class Forecast {
        final List<Hour> hours = new ArrayList<>();
        final List<Day> days = new ArrayList<>();
        long updated;
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
    private boolean failed;

    Weather(Context c) {
        prefs = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        Config cfg = Config.get(c);
        enabled = cfg.weatherEnabled;
        cities = cfg.secondEnabled ? 2 : 1;
        String lats = coord(cfg.lat) + (cities == 2 ? "," + coord(cfg.secondLat) : "");
        String lngs = coord(cfg.lng) + (cities == 2 ? "," + coord(cfg.secondLng) : "");
        url = "https://api.open-meteo.com/v1/forecast?latitude=" + lats + "&longitude=" + lngs
                + "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,relative_humidity_2m,apparent_temperature,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max&timezone=auto&forecast_days=6&timeformat=unixtime&wind_speed_unit=kmh"
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
                    prefs.getInt("x" + i, 0), prefs.getInt("n" + i, 0), prefs.contains("x" + i),
                    prefs.getInt("a" + i, -1), prefs.getInt("h" + i, -1), prefs.getInt("w" + i, -1));
        }
        return out;
    }

    boolean lastFetchFailed() { return failed; }

    Forecast forecast() {
        if (current() == null) return null;
        try { return parseForecast(new JSONObject(prefs.getString("forecast", "")),
                System.currentTimeMillis(), prefs.getLong("at", 0)); }
        catch (Exception ex) { return null; }
    }

    static Forecast parseForecast(JSONObject json, long now, long updated) throws Exception {
        Forecast out = new Forecast();
        out.updated = updated;
        JSONObject hourly = json.getJSONObject("hourly"), daily = json.getJSONObject("daily");
        JSONArray ht = hourly.getJSONArray("time"), dt = daily.getJSONArray("time");
        for (int i = 0; i < ht.length() && out.hours.size() < 8; i++) {
            long at = ht.getLong(i) * 1000L;
            if (at < now) continue;
            int rain = hourly.getJSONArray("precipitation_probability").getInt(i);
            if (rain < 0 || rain > 100) throw new IllegalArgumentException("Invalid rain probability");
            out.hours.add(new Hour(at, (int) Math.round(hourly.getJSONArray("temperature_2m").getDouble(i)),
                    hourly.getJSONArray("weather_code").getInt(i), rain, hourly.getJSONArray("is_day").getInt(i) == 1,
                    optionalPercent(hourly, "relative_humidity_2m", i),
                    optionalRounded(hourly, "apparent_temperature", i), optionalRounded(hourly, "wind_speed_10m", i)));
        }
        java.time.LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(JakimProvider.MALAYSIA).toLocalDate();
        for (int i = 0; i < dt.length() && out.days.size() < 5; i++) {
            long at = dt.getLong(i) * 1000L;
            if (java.time.Instant.ofEpochMilli(at).atZone(JakimProvider.MALAYSIA).toLocalDate().isBefore(today)) continue;
            int min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i));
            int max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i));
            if (max < min) throw new IllegalArgumentException("Invalid temperature range");
            out.days.add(new Day(at, min, max, daily.getJSONArray("weather_code").getInt(i),
                    optionalPercent(daily, "precipitation_probability_max", i)));
        }
        if (out.hours.isEmpty() || out.days.isEmpty()) throw new IllegalArgumentException("No current forecast");
        return out;
    }

    private static int optionalPercent(JSONObject object, String key, int index) {
        JSONArray values = object.optJSONArray(key);
        if (values == null || values.isNull(index)) return -1;
        int value = values.optInt(index, -1);
        return value >= 0 && value <= 100 ? value : -1;
    }

    private static int optionalRounded(JSONObject object, String key, int index) {
        JSONArray values = object.optJSONArray(key);
        double value = values == null ? Double.NaN : values.optDouble(index, Double.NaN);
        return Double.isFinite(value) ? (int) Math.round(value) : Integer.MIN_VALUE;
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
                failed = !ok;
                onDone.run();
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
            parseForecast(arr.getJSONObject(0), System.currentTimeMillis(), System.currentTimeMillis());
            SharedPreferences.Editor e = prefs.edit().clear();
            StringBuilder log = new StringBuilder("weather: fetch ok");
            for (int i = 0; i < cities; i++) {
                JSONObject cur = arr.getJSONObject(i).getJSONObject("current");
                double t = cur.getDouble("temperature_2m");
                int code = cur.getInt("weather_code");
                boolean day = cur.getInt("is_day") == 1;
                int apparent = (int) Math.round(cur.getDouble("apparent_temperature"));
                int humidity = cur.getInt("relative_humidity_2m");
                int wind = (int) Math.round(cur.getDouble("wind_speed_10m"));
                if (humidity < 0 || humidity > 100 || wind < 0) throw new IllegalArgumentException("Invalid weather metrics");
                JSONObject daily = arr.getJSONObject(i).getJSONObject("daily");
                double max = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double min = daily.getJSONArray("temperature_2m_min").getDouble(0);
                e.putInt("t" + i, (int) Math.round(t)).putInt("c" + i, code).putBoolean("d" + i, day)
                        .putInt("x" + i, (int) Math.round(max)).putInt("n" + i, (int) Math.round(min))
                        .putInt("a" + i, apparent).putInt("h" + i, humidity).putInt("w" + i, wind);
                log.append(i == 0 ? " here=" : " second=").append(t).append(" code=").append(code)
                        .append(" is_day=").append(day ? 1 : 0).append(" max=").append(max).append(" min=").append(min);
            }
            e.putString("forecast", arr.getJSONObject(0).toString());
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
