package tv.widgetv.home;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Official JAKIM e-Solat schedule provider (Zone SGR01: Shah Alam, Petaling, Klang, Gombak, Sepang).
 * Data sourced directly from the official portal (e-solat.gov.my).
 */
final class JakimProvider {
    private static final String TAG = Config.TAG;
    private static final Map<String, Entry> cache = new HashMap<>();
    private static boolean loaded = false;

    static final class Entry {
        final int[] minutes; // [Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha]
        final int hijriYear, hijriMonth, hijriDay;

        Entry(int[] minutes, int hijriYear, int hijriMonth, int hijriDay) {
            this.minutes = minutes;
            this.hijriYear = hijriYear;
            this.hijriMonth = hijriMonth;
            this.hijriDay = hijriDay;
        }
    }

    private static synchronized void ensureLoaded(Context ctx) {
        if (loaded && !cache.isEmpty()) return;
        if (ctx == null) return;
        try (InputStream is = ctx.getAssets().open("jakim_sgr01.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String dateStr = keys.next();
                JSONObject obj = root.getJSONObject(dateStr);
                JSONArray arr = obj.getJSONArray("times");
                int[] times = new int[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    times[i] = arr.getInt(i);
                }
                String hijri = obj.optString("hijri", null);
                int hY = 0, hM = 0, hD = 0;
                if (hijri != null && hijri.contains("-")) {
                    String[] parts = hijri.split("-");
                    if (parts.length == 3) {
                        hY = Integer.parseInt(parts[0]);
                        hM = Integer.parseInt(parts[1]) - 1; // 0-indexed month
                        hD = Integer.parseInt(parts[2]);
                    }
                }
                cache.put(dateStr, new Entry(times, hY, hM, hD));
            }
            loaded = true;
            Log.i(TAG, "jakim: loaded " + cache.size() + " days of official JAKIM e-Solat times");
        } catch (Exception e) {
            Log.w(TAG, "jakim: could not load jakim_sgr01.json", e);
        }
    }

    static Entry get(Context ctx, LocalDate date) {
        ensureLoaded(ctx);
        String key = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return cache.get(key);
    }

    /**
     * Attempts to fill PrayerDay fields using official JAKIM data.
     * Returns true if JAKIM data for today was found and successfully applied.
     */
    static boolean populate(Context ctx, long now, long[] times, long[] nightEdges, int[] hijriOut, int[] offsets) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        LocalDate today = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        Entry todayEntry = get(ctx, today);
        if (todayEntry == null) return false;

        // Populate today's 6 prayer times:
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long midnightToday = cal.getTimeInMillis();

        for (int i = 0; i < 6; i++) {
            times[i] = midnightToday + (todayEntry.minutes[i] + offsets[i]) * 60_000L;
        }

        // Yesterday's Isha:
        Entry yEntry = get(ctx, today.minusDays(1));
        if (yEntry != null) {
            nightEdges[0] = (midnightToday - 24 * 3_600_000L) + (yEntry.minutes[5] + offsets[5]) * 60_000L;
        } else {
            nightEdges[0] = times[5] - 24 * 3_600_000L;
        }

        // Tomorrow's Fajr:
        Entry tEntry = get(ctx, today.plusDays(1));
        if (tEntry != null) {
            nightEdges[1] = (midnightToday + 24 * 3_600_000L) + (tEntry.minutes[0] + offsets[0]) * 60_000L;
        } else {
            nightEdges[1] = times[0] + 24 * 3_600_000L;
        }

        // Hijri date:
        hijriOut[0] = todayEntry.hijriDay;
        hijriOut[1] = todayEntry.hijriMonth;
        hijriOut[2] = todayEntry.hijriYear;

        return true;
    }
}
