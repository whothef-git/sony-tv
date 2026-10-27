package tv.widgetv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The four geometric tiles for the wallpaper overlay, each a small bitmap that a BitmapShader repeats.
 * Lines are opaque gold; SkyView applies the ~13% alpha and the top-down fade. Sizes are for a
 * 1920x1080 window (density 2); every shape that crosses an edge is also drawn on the opposite edge so
 * the tile repeats seamlessly.
 */
final class Patterns {
    static final int STAR8 = 0, HEX_GIRIH = 1, CIRCLES = 2, ZELLIGE = 3;
    static final String[] NAMES = {"star8", "hex-girih", "circles", "zellige"};

    private Patterns() {
    }

    static Bitmap tile(int pattern, float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, density));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Lapis.GOLD_400);
        switch (pattern) {
            case STAR8: return star8(Math.round(50 * density), p);
            case HEX_GIRIH: return hexGirih(Math.round(17 * density), p);
            case CIRCLES: return circles(Math.round(48 * density), p);
            default: return zellige(Math.round(42 * density), p);
        }
    }

    /** Two overlapping squares (one turned 45°) with short connectors to the neighbours: a 46-unit tile scaled up. */
    private static Bitmap star8(int t, Paint p) {
        Bitmap b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float m = t / 2f, half = t * 10f / 46f, tip = half * 1.4142f;
        c.drawRect(m - half, m - half, m + half, m + half, p);
        Path d = new Path();
        d.moveTo(m, m - tip);
        d.lineTo(m + tip, m);
        d.lineTo(m, m + tip);
        d.lineTo(m - tip, m);
        d.close();
        c.drawPath(d, p);
        c.drawLine(m, 0, m, m - tip, p);
        c.drawLine(m, m + tip, m, t, p);
        c.drawLine(0, m, m - tip, m, p);
        c.drawLine(m + tip, m, t, m, p);
        return b;
    }

    /** Flat-top honeycomb (outer hexagons) with a pointy inner hexagon in each cell. */
    private static Bitmap hexGirih(int r, Paint p) {
        float h = (float) (Math.sqrt(3) * r);
        Bitmap b = Bitmap.createBitmap(3 * r, Math.round(h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float[][] centres = {{0, 0}, {3 * r, 0}, {0, h}, {3 * r, h}, {1.5f * r, h / 2}};
        for (float[] o : centres) {
            c.drawPath(polygon(o[0], o[1], r, 6, 0), p);
            c.drawPath(polygon(o[0], o[1], r * 0.5f, 6, 30), p);
        }
        return b;
    }

    /** Overlapping circles on a square lattice (centre + corners). */
    private static Bitmap circles(int t, Paint p) {
        Bitmap b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = t / 2f;
        c.drawCircle(r, r, r, p);
        for (int x = 0; x <= t; x += t) for (int y = 0; y <= t; y += t) c.drawCircle(x, y, r, p);
        return b;
    }

    /**
     * Zellige: the octagon-and-square tiling with an 8-point star inside each octagon and a small diamond
     * inside each square. Denser than the others (~84px tile) because it reads best that way.
     */
    private static Bitmap zellige(int t, Paint p) {
        Bitmap b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float m = t / 2f;
        // Octagon with its flat sides on the tile edges; neighbours share those edges, the cut corners make the squares.
        c.drawPath(polygon(m, m, (float) (m / Math.cos(Math.PI / 8)), 8, 22.5f), p);
        c.drawPath(star(m, m, t * 0.40f, t * 0.23f), p);
        float corner = t * 0.09f;
        for (int x = 0; x <= t; x += t) for (int y = 0; y <= t; y += t) c.drawPath(polygon(x, y, corner, 4, 0), p);
        return b;
    }

    private static Path polygon(float cx, float cy, float r, int n, float startDeg) {
        Path path = new Path();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    /** 8-point star outline: 16 vertices alternating outer and inner radius. */
    private static Path star(float cx, float cy, float outer, float inner) {
        Path path = new Path();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(22.5 * i);
            float r = i % 2 == 0 ? outer : inner;
            float x = cx + (float) (r * Math.cos(a)), y = cy + (float) (r * Math.sin(a));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }
}
