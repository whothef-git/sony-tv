package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Read-only weather page: current conditions, extended hourly forecast, 5-day daily outlook.
 * onDraw() allocates NOTHING — all Paints, Shaders and Gradients are pre-built.
 */
public final class ForecastView extends View {

    // Two weights — never call setFontVariationSettings inside onDraw
    private final Paint tNorm = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tBold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final float d;

    // Pre-allocated shaders — updated in setWeather() / onSizeChanged()
    private LinearGradient barGradient;   // reused for all day range bars
    private float barGradW = -1;          // track width used to build barGradient

    private Weather.City current;
    private Weather.Forecast forecast;
    private boolean failed;

    private static final int WHITE      = 0xFFF1F6FF;
    private static final int MUTED      = 0xFF9FBAD4;
    private static final int MUTED_LT   = 0xFFD8E7F6;
    private static final int RAIN_COLOR = 0xFF6DBBFF;
    private static final int CHIP_BG    = 0x35101F30;
    private static final int PANEL_BG   = 0x4016273C;
    private static final int PANEL_EDGE = 0x20C0D8EC;

    public ForecastView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;

        Typeface base = getResources().getFont(R.font.outfit);
        tNorm.setTypeface(Typeface.create(base, 400, false));
        tBold.setTypeface(Typeface.create(base, 700, false));
        tNorm.setAntiAlias(true);
        tBold.setAntiAlias(true);
        surface.setAntiAlias(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setWeather(Weather.City[] cities, Weather.Forecast f, boolean failed) {
        current = cities == null ? null : cities[0];
        forecast = f;
        this.failed = failed;
        barGradW = -1; // force gradient rebuild on next draw
        setContentDescription(current == null || f == null
                ? "Weather forecast unavailable or loading"
                : "Shah Alam. " + current.temp + "°. " + ForecastView.condition(current.code));
        invalidate();
    }

    // ── helpers (no allocation) ────────────────────────────────────────────

    private void norm(Canvas c, String s, float x, float y, float sz, int color, Paint.Align a) {
        tNorm.setTextSize(sz); tNorm.setColor(color); tNorm.setTextAlign(a);
        c.drawText(s, x, y, tNorm);
    }
    private void bold(Canvas c, String s, float x, float y, float sz, int color, Paint.Align a) {
        tBold.setTextSize(sz); tBold.setColor(color); tBold.setTextAlign(a);
        c.drawText(s, x, y, tBold);
    }

    private void panel(Canvas c, float l, float t, float r, float b, float r2) {
        rect.set(l, t, r, b);
        surface.setShader(null); surface.setStyle(Paint.Style.FILL); surface.setColor(PANEL_BG);
        c.drawRoundRect(rect, r2, r2, surface);
        surface.setStyle(Paint.Style.STROKE); surface.setStrokeWidth(0.75f); surface.setColor(PANEL_EDGE);
        c.drawRoundRect(rect, r2, r2, surface);
        surface.setStyle(Paint.Style.FILL);
    }

    private void chip(Canvas c, float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        surface.setShader(null); surface.setStyle(Paint.Style.FILL); surface.setColor(CHIP_BG);
        c.drawRoundRect(rect, 8, 8, surface);
        surface.setStyle(Paint.Style.STROKE); surface.setStrokeWidth(0.5f); surface.setColor(0x18FFFFFF);
        c.drawRoundRect(rect, 8, 8, surface);
        surface.setStyle(Paint.Style.FILL);
    }

    private void icon(Canvas c, int code, boolean day, float x, float y, int size) {
        int res = Weather.icon(new Weather.City(0, code, day, 0, 0, false));
        Drawable img = icons.get(res);
        if (img == null) {
            img = getResources().getDrawable(res, null).mutate();
            img.setTint(WHITE); icons.put(res, img);
        }
        c.save(); c.translate(x, y); img.setBounds(0, 0, size, size); img.draw(c); c.restore();
    }

    private void divider(Canvas c, float x, float t, float b) {
        surface.setShader(null); surface.setStyle(Paint.Style.FILL);
        surface.setColor(0x14FFFFFF); surface.setStrokeWidth(0.5f);
        c.drawLine(x, t, x, b, surface);
    }

    // ── draw ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        // All coordinates are in dp; canvas is scaled by density.
        canvas.save();
        canvas.scale(d, d);
        float w = getWidth() / d;
        float h = getHeight() / d;

        if (current == null || forecast == null) {
            panel(canvas, 0, 0, w, h - 20, 14);
            bold(canvas, failed ? "Forecast unavailable" : "Loading forecast…",
                    w / 2, h / 2 - 4, 22, WHITE, Paint.Align.CENTER);
            norm(canvas, failed ? "Check your connection. The launcher will retry automatically."
                    : "Shah Alam · Open-Meteo",
                    w / 2, h / 2 + 20, 12, MUTED, Paint.Align.CENTER);
            canvas.restore(); return;
        }

        float upperH   = Math.min(176f, h * 0.56f);
        float lowerT   = upperH + 10f;
        float lowerB   = h - 20f;
        float footerY  = h - 5f;

        // ── Hero card ─────────────────────────────────────────────────────
        float heroR = 264f;
        panel(canvas, 0, 0, heroR, upperH, 14);

        bold(canvas, "SHAH ALAM", 20, 26, 11, MUTED, Paint.Align.LEFT);
        bold(canvas, current.temp + "°", 20, 72, 48, WHITE, Paint.Align.LEFT);
        icon(canvas, current.code, current.day, heroR - 90, 38, 46);
        norm(canvas, condition(current.code), 20, 94, 13, MUTED_LT, Paint.Align.LEFT);
        norm(canvas, "H: " + current.max + "°   L: " + current.min + "°",
                20, 112, 11, MUTED, Paint.Align.LEFT);

        // Metric chips
        float cY1 = 124, cY2 = 160;
        float p1L = 20, p1R = p1L + 68;
        chip(canvas, p1L, cY1, p1R, cY2);
        bold(canvas, "FEELS", (p1L + p1R) / 2, cY1 + 13, 8.5f, 0xFF8EAAC5, Paint.Align.CENTER);
        bold(canvas, current.apparentTemp + "°", (p1L + p1R) / 2, cY2 - 7, 12, WHITE, Paint.Align.CENTER);

        float p2L = p1R + 7, p2R = p2L + 68;
        chip(canvas, p2L, cY1, p2R, cY2);
        bold(canvas, "HUMID", (p2L + p2R) / 2, cY1 + 13, 8.5f, 0xFF8EAAC5, Paint.Align.CENTER);
        bold(canvas, current.humidity + "%", (p2L + p2R) / 2, cY2 - 7, 12, WHITE, Paint.Align.CENTER);

        float p3L = p2R + 7, p3R = heroR - 20;
        chip(canvas, p3L, cY1, p3R, cY2);
        bold(canvas, "WIND", (p3L + p3R) / 2, cY1 + 13, 8.5f, 0xFF8EAAC5, Paint.Align.CENTER);
        bold(canvas, current.windSpeed + " km/h", (p3L + p3R) / 2, cY2 - 7, 10.5f, WHITE, Paint.Align.CENTER);

        // ── Hourly strip ──────────────────────────────────────────────────
        float hL = heroR + 12f;
        panel(canvas, hL, 0, w, upperH, 14);

        List<Weather.Hour> hrs = forecast.hours;
        int nH = Math.min(8, hrs.size());
        bold(canvas, "HOURLY FORECAST", hL + 18, 25, 10, MUTED, Paint.Align.LEFT);
        norm(canvas, "Next " + nH + " hours", w - 18, 25, 11, MUTED, Paint.Align.RIGHT);

        float hW = (w - hL - 28);
        float cW = hW / nH;
        boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "ha", Locale.UK);

        for (int i = 0; i < nH; i++) {
            Weather.Hour hr = hrs.get(i);
            float cx = hL + 14 + cW * (i + 0.5f);
            String timeStr = (i == 0) ? "Now"
                    : Instant.ofEpochMilli(hr.at).atZone(JakimProvider.MALAYSIA).format(fmt);

            if (i == 0) bold(canvas, timeStr, cx, 48, 11.5f, WHITE, Paint.Align.CENTER);
            else         norm(canvas, timeStr, cx, 48, 11.5f, WHITE, Paint.Align.CENTER);

            icon(canvas, hr.code, hr.day, cx - 11, 57, 22);

            if (hr.rain > 0) bold(canvas, hr.rain + "%", cx, 96, 10, RAIN_COLOR, Paint.Align.CENTER);
            else             norm(canvas, "—", cx, 96, 10, 0x40B7D0E9, Paint.Align.CENTER);

            bold(canvas, hr.temp + "°", cx, 122, 16, WHITE, Paint.Align.CENTER);

            if (hr.humidity > 0)
                norm(canvas, hr.humidity + "%", cx, 148, 10, 0xFF8EAAC5, Paint.Align.CENTER);
        }

        // ── 5-day strip ───────────────────────────────────────────────────
        panel(canvas, 0, lowerT, w, lowerB, 14);

        List<Weather.Day> days = forecast.days;
        int nD = Math.min(5, days.size());
        bold(canvas, "5-DAY FORECAST", 20, lowerT + 19, 10, MUTED, Paint.Align.LEFT);
        norm(canvas, "Daily Outlook", w - 20, lowerT + 19, 11, MUTED, Paint.Align.RIGHT);

        int spanLow = Integer.MAX_VALUE, spanHigh = Integer.MIN_VALUE;
        for (Weather.Day day : days) { spanLow = Math.min(spanLow, day.min); spanHigh = Math.max(spanHigh, day.max); }
        float span = Math.max(1f, spanHigh - spanLow);

        float dCW = (w - 24) / nD;
        LocalDate today = LocalDate.now(JakimProvider.MALAYSIA);

        // Pre-build bar gradient once when width is known (no allocation inside loop)
        float barFullW = dCW - 16 - 26 - 26; // approximate inner bar width
        if (barGradient == null || barGradW != barFullW) {
            barGradient = new LinearGradient(0, 0, barFullW, 0,
                    0xFF82BDD6, 0xFFE3CE76, Shader.TileMode.CLAMP);
            barGradW = barFullW;
        }

        for (int i = 0; i < nD; i++) {
            Weather.Day day = days.get(i);
            float dl = 12 + dCW * i + 8;
            float dr = dl + dCW - 16;

            if (i > 0) divider(canvas, 12 + dCW * i, lowerT + 26, lowerB - 14);

            LocalDate date = Instant.ofEpochMilli(day.at).atZone(JakimProvider.MALAYSIA).toLocalDate();
            String dayName = date.equals(today) ? "Today"
                    : date.format(DateTimeFormatter.ofPattern("EEE", Locale.UK));
            String dateStr = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK));

            bold(canvas, dayName,  dl, lowerT + 38, 13, WHITE, Paint.Align.LEFT);
            norm(canvas, dateStr,  dr, lowerT + 38, 10, MUTED, Paint.Align.RIGHT);

            icon(canvas, day.code, true, dl, lowerT + 47, 20);
            norm(canvas, condition(day.code), dl + 26, lowerT + 62, 11, MUTED_LT, Paint.Align.LEFT);

            if (day.rainMax > 0)
                bold(canvas, day.rainMax + "%", dr, lowerT + 62, 10, RAIN_COLOR, Paint.Align.RIGHT);

            float barY = lowerB - 18f;
            norm(canvas, day.min + "°", dl, barY + 4, 11, MUTED, Paint.Align.LEFT);
            bold(canvas, day.max + "°", dr, barY + 4, 12, WHITE, Paint.Align.RIGHT);

            float barL = dl + 25, barR = dr - 26;
            rect.set(barL, barY - 2.5f, barR, barY + 2.5f);
            surface.setShader(null); surface.setStyle(Paint.Style.FILL);
            surface.setColor(0x28FFFFFF);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, surface);

            float fillL = barL + (barR - barL) * (day.min - spanLow) / span;
            float fillR = barL + (barR - barL) * (day.max - spanLow) / span;
            fillR = Math.max(fillL + 4, fillR);

            // Translate the pre-built gradient to the correct position
            canvas.save();
            canvas.translate(barL, 0);
            surface.setShader(barGradient);
            rect.set(fillL - barL, barY - 2.5f, fillR - barL, barY + 2.5f);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, surface);
            canvas.restore();
            surface.setShader(null);
        }

        // ── Footer ────────────────────────────────────────────────────────
        String at = Instant.ofEpochMilli(forecast.updated)
                .atZone(JakimProvider.MALAYSIA)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
        norm(canvas, (failed ? "Saved forecast · " : "") + "Updated " + at + " · Open-Meteo",
                w / 2, footerY, 10, MUTED, Paint.Align.CENTER);

        canvas.restore();
    }

    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 95) return "Thunderstorms";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "Rain showers";
        return "Current conditions";
    }
}
