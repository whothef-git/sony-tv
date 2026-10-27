package tv.widgetv.home;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Thought of the day from res/raw/thoughts.json:
 * {"weekday":[{"text":"…","source":"…"|null}], "friday":[…]}. The pick is stable for the whole local
 * (TV time zone) day: weekday[epochDay % n] on other days, friday[(epochDay / 7) % n] on Fridays.
 */
final class Thoughts {
    private static final String TAG = Config.TAG;

    static final class Thought {
        final boolean friday;
        final String text, source; // source may be null

        Thought(boolean friday, String text, String source) {
            this.friday = friday;
            this.text = text;
            this.source = source;
        }
    }

    private JSONArray weekday, friday;

    Thoughts(Context c) {
        try (InputStream in = c.getResources().openRawResource(R.raw.thoughts)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject root = new JSONObject(out.toString("UTF-8"));
            weekday = root.optJSONArray("weekday");
            friday = root.optJSONArray("friday");
            Log.i(TAG, "thoughts: weekday=" + size(weekday) + " friday=" + size(friday));
        } catch (Exception e) {
            Log.w(TAG, "thoughts: cannot read res/raw/thoughts.json", e);
        }
    }

    /** null when there is nothing to show for that day. */
    Thought pick(LocalDate day) {
        boolean fri = day.getDayOfWeek() == DayOfWeek.FRIDAY;
        long epochDay = day.toEpochDay();
        JSONArray list = fri && size(friday) > 0 ? friday : weekday;
        if (size(list) == 0) return null;
        boolean fromFriday = list == friday;
        int i = (int) Math.floorMod(fromFriday ? epochDay / 7 : epochDay, (long) list.length());
        JSONObject o = list.optJSONObject(i);
        if (o == null || o.optString("text", "").isEmpty()) return null;
        String source = o.isNull("source") ? null : o.optString("source", null);
        if (source != null && source.isEmpty()) source = null;
        return new Thought(fromFriday, o.optString("text"), source); // the Jumu'ah tag only with a Friday entry
    }

    private static int size(JSONArray a) {
        return a == null ? 0 : a.length();
    }
}
