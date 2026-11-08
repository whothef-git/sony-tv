package tv.widgetv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Positive-only local rain signal from the documented RainViewer Weather Maps API. */
final class LocalRainRadar {
    private static final String TILE_HOST = "https://tilecache.rainviewer.com";
    private static final int TILE_SIZE = 256;
    private static final long MAX_AGE_MS = 15 * 60_000L;

    // Unsmoothed Universal Blue rain colors for echoes of at least 20 dBZ.
    // https://www.rainviewer.com/files/rainviewer_api_colors_table.csv
    private static final int[] RAIN_COLORS = {
        0xff00a3e0, 0xff009ad5, 0xff0091ca, 0xff0088bf, 0xff007fb4, 0xff0077aa, 0xff0070a3,
        0xff00699c, 0xff006295, 0xff005b8e, 0xff005588, 0xff005180, 0xff004e78, 0xff004a70,
        0xff004768, 0xffffee00, 0xffffe000, 0xffffd200, 0xffffc500, 0xffffb700, 0xffffaa00,
        0xffff9f00, 0xffff9500, 0xffff8b00, 0xffff8100, 0xffff4400, 0xfff23600, 0xffe62800,
        0xffd91b00, 0xffcd0d00, 0xffc10000, 0xffa80000, 0xff8f0000, 0xff760000, 0xff5d0000,
        0xffffaaff, 0xffff9fff, 0xffff95ff, 0xffff8bff, 0xffff81ff, 0xffff77ff, 0xffff6cff,
        0xffff62ff, 0xffff58ff, 0xffff4eff, 0xffffffff, 0xff00ff00,
    };

    private LocalRainRadar() {}

    static boolean isFresh(long frameTime, long now) {
        return frameTime > 0 && frameTime <= now && now - frameTime <= MAX_AGE_MS;
    }

    /** Returns the timestamp of a positive radar frame, or zero when no fresh signal is available. */
    static long fetch(String latitude, String longitude) {
        try {
            JSONObject manifest = new JSONObject(new String(download(
                    "https://api.rainviewer.com/public/weather-maps.json", 64 * 1024), StandardCharsets.UTF_8));
            if (!TILE_HOST.equals(manifest.optString("host"))) return 0;
            JSONArray frames = manifest.getJSONObject("radar").getJSONArray("past");
            long now = System.currentTimeMillis();
            long newestTime = 0;
            String path = "";
            for (int i = 0; i < frames.length(); i++) {
                JSONObject frame = frames.getJSONObject(i);
                long seconds = frame.optLong("time", 0);
                if (seconds <= 0 || seconds > now / 1000L) continue;
                long at = seconds * 1000L;
                String candidatePath = frame.optString("path");
                if (at > newestTime && isFresh(at, now) && candidatePath.matches("/v2/radar/[a-f0-9]{8,64}")) {
                    newestTime = at;
                    path = candidatePath;
                }
            }
            if (newestTime == 0) return 0;

            // The coordinate tile is centered on the configured local city. Disable smoothing.
            byte[] png = download(TILE_HOST + path + "/256/7/" + latitude + "/" + longitude + "/2/0_0.png", 512 * 1024);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(png, 0, png.length, options);
            if (options.outWidth != TILE_SIZE || options.outHeight != TILE_SIZE
                    || !"image/png".equals(options.outMimeType)) return 0;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap tile = BitmapFactory.decodeByteArray(png, 0, png.length, options);
            if (tile == null) return 0;
            try {
                int[] center = new int[9];
                tile.getPixels(center, 0, 3, TILE_SIZE / 2 - 1, TILE_SIZE / 2 - 1, 3, 3);
                return isFresh(newestTime, System.currentTimeMillis()) && hasRainNear(center) ? newestTime : 0;
            } finally {
                tile.recycle();
            }
        } catch (IOException | org.json.JSONException | IllegalArgumentException unavailable) {
            Log.w(Config.TAG, "weather radar unavailable: " + unavailable.getClass().getSimpleName());
            return 0;
        }
    }

    static boolean hasRainNear(int[] pixels) {
        int wet = 0;
        for (int pixel : pixels) {
            for (int rain : RAIN_COLORS) {
                if (pixel == rain) {
                    if (++wet >= 5) return true;
                    break;
                }
            }
        }
        return false;
    }

    private static byte[] download(String url, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IOException("Radar HTTP error");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                for (int length; (length = input.read(buffer)) != -1;) {
                    if (output.size() + length > limit) throw new IOException("Radar response too large");
                    output.write(buffer, 0, length);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
