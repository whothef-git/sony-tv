package tv.widgetv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.palette.graphics.Palette;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finds launchable apps and renders their tile art. Not thread-safe: call it from one
 * background thread only. Rendered entries are cached per component until forget(pkg).
 */
final class AppLoader {
    /** A launchable activity and whether it came from LEANBACK_LAUNCHER. */
    static final class Candidate {
        final ActivityInfo info;
        final CharSequence label;
        final boolean leanback;

        Candidate(ActivityInfo info, CharSequence label, boolean leanback) {
            this.info = info;
            this.label = label;
            this.leanback = leanback;
        }

        String key() {
            return info.packageName + "/" + info.name + "/" + label;
        }
    }

    private final Context ctx;
    private final PackageManager pm;
    private final int artW;
    private final int artH;
    private final Map<ComponentName, AppEntry> cache = new HashMap<>();

    AppLoader(Context ctx, int artW, int artH) {
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
        this.artW = artW;
        this.artH = artH;
    }

    /** One activity per package, LEANBACK_LAUNCHER preferred over LAUNCHER. Cheap (no drawables). */
    Map<String, Candidate> query() {
        Map<String, Candidate> byPkg = new LinkedHashMap<>();
        addAll(byPkg, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addAll(byPkg, Intent.CATEGORY_LAUNCHER);
        byPkg.remove(ctx.getPackageName());
        return byPkg;
    }

    private void addAll(Map<String, Candidate> byPkg, String category) {
        boolean leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!byPkg.containsKey(pkg)) byPkg.put(pkg, new Candidate(ri.activityInfo, ri.loadLabel(pm), leanback));
        }
    }

    AppEntry entry(Candidate c) {
        ComponentName cn = new ComponentName(c.info.packageName, c.info.name);
        AppEntry cached = cache.get(cn);
        if (cached != null) return cached;
        Bitmap art = render(c.info);
        AppEntry e = new AppEntry(cn, String.valueOf(c.label), c.leanback, art, ambientColor(art));
        cache.put(cn, e);
        return e;
    }

    // Preserve the installed launcher's per-app backlight colors; compute once off the UI thread.
    private static int ambientColor(Bitmap art) {
        Palette palette = Palette.from(art).resizeBitmapArea(4096).generate();
        int color = palette.getVibrantColor(palette.getDominantColor(0xFFFFD56B));
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0.65f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    void forget(String pkg) {
        cache.keySet().removeIf(cn -> cn.getPackageName().equals(pkg));
    }

    /** Banner scaled to the tile size; apps without a banner get their icon on a tinted tile. */
    private Bitmap render(ActivityInfo ai) {
        Bitmap bmp = Bitmap.createBitmap(artW, artH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Drawable banner = ai.loadBanner(pm);
        if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
        if (banner != null) {
            banner.setBounds(0, 0, artW, artH);
            banner.draw(c);

            // Auto-crop transparent pillarbox/letterbox margins so the banner fits flush edge-to-edge
            int minX = artW, maxX = -1, minY = artH, maxY = -1;
            int[] row = new int[artW];
            for (int y = 0; y < artH; y++) {
                bmp.getPixels(row, 0, artW, 0, y, artW, 1);
                for (int x = 0; x < artW; x++) {
                    int alpha = (row[x] >>> 24);
                    if (alpha > 25) { // Non-transparent pixel
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            if (maxX > minX && maxY > minY && (minX > 1 || maxX < artW - 2 || minY > 1 || maxY < artH - 2)) {
                int contentW = maxX - minX + 1;
                int contentH = maxY - minY + 1;
                Bitmap cropped = Bitmap.createBitmap(bmp, minX, minY, contentW, contentH);
                bmp.recycle();
                bmp = Bitmap.createScaledBitmap(cropped, artW, artH, true);
            }
            return ensureSharpRectangle(bmp, artW, artH);
        }
        Drawable icon = ai.loadIcon(pm);
        Bitmap small = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 48, 48);
        icon.draw(new Canvas(small));
        int base = Palette.from(small).generate().getDominantColor(0xFF3A3A3C);
        small.recycle();
        c.drawColor(scaleValue(base, 0.45f));
        int size = artH * 3 / 5;
        icon.setBounds((artW - size) / 2, (artH - size) / 2, (artW + size) / 2, (artH + size) / 2);
        icon.draw(c);
        return ensureSharpRectangle(bmp, artW, artH);
    }

    /**
     * If an app's banner has rounded/beveled transparent corners (such as SportzX),
     * fills the transparent corner cutouts with the banner's outer edge color so all
     * apps share the exact same sharp 90-degree rectangular card geometry.
     */
    private static Bitmap ensureSharpRectangle(Bitmap bmp, int w, int h) {
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);

        // Check if any of the 4 extreme corners are transparent (rounded cutout)
        boolean hasTransparentCorners = (pixels[0] >>> 24) < 200
                || (pixels[w - 1] >>> 24) < 200
                || (pixels[(h - 1) * w] >>> 24) < 200
                || (pixels[w * h - 1] >>> 24) < 200;

        if (!hasTransparentCorners) {
            return bmp;
        }

        // Sample outer edge colors near the middle of each border
        int topColor = findFirstOpaque(pixels, w, h, w / 2, 0, 0, 1);
        int bottomColor = findFirstOpaque(pixels, w, h, w / 2, h - 1, 0, -1);
        int leftColor = findFirstOpaque(pixels, w, h, 0, h / 2, 1, 0);
        int rightColor = findFirstOpaque(pixels, w, h, w - 1, h / 2, -1, 0);

        int fallbackColor = topColor != 0 ? topColor : (bottomColor != 0 ? bottomColor : (leftColor != 0 ? leftColor : rightColor));
        if (fallbackColor == 0) fallbackColor = 0xFFFFFFFF; // default white

        // Fill transparent pixels in corner zones (outer 25% of card bounds)
        int cornerW = w / 4;
        int cornerH = h / 4;

        for (int y = 0; y < h; y++) {
            boolean inYCorner = y < cornerH || y >= h - cornerH;
            for (int x = 0; x < w; x++) {
                boolean inXCorner = x < cornerW || x >= w - cornerW;
                if (inYCorner && inXCorner) {
                    int p = pixels[y * w + x];
                    int alpha = (p >>> 24);
                    if (alpha < 220) {
                        int fill;
                        if (y < h / 2) {
                            fill = topColor != 0 ? topColor : fallbackColor;
                        } else {
                            fill = bottomColor != 0 ? bottomColor : fallbackColor;
                        }
                        pixels[y * w + x] = 0xFF000000 | (fill & 0x00FFFFFF);
                    }
                }
            }
        }

        Bitmap result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        bmp.recycle();
        return result;
    }

    private static int findFirstOpaque(int[] pixels, int w, int h, int startX, int startY, int stepX, int stepY) {
        int x = startX, y = startY;
        while (x >= 0 && x < w && y >= 0 && y < h) {
            int p = pixels[y * w + x];
            if ((p >>> 24) > 200) return p;
            x += stepX;
            y += stepY;
        }
        return 0;
    }

    private static int scaleValue(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }
}
