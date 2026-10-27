package tv.widgetv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
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
import java.util.Locale;

/** Read-only weather page: current conditions, next six hours, and five days. */
public final class ForecastView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), surface = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final float d;
    private final Typeface type;
    private Weather.City current;
    private Weather.Forecast forecast;
    private boolean failed;
    private Shader background;
    private static final int WHITE = 0xFFF1F6FF, MUTED = 0xFFB7D0E9, RAIN = 0xFF87CAFF;

    public ForecastView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        type = Typeface.create(getResources().getFont(R.font.outfit), 400, false);
        text.setTypeface(type);
        text.setFontVariationSettings("'wght' 400");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void setWeather(Weather.City[] cities, Weather.Forecast forecast, boolean failed) {
        current = cities == null ? null : cities[0];
        this.forecast = forecast; this.failed = failed;
        setContentDescription(current == null || forecast == null ? "Weather forecast unavailable or loading" :
                "Shah Alam. " + current.temp + " degrees. " + condition(current.code) + ". " + forecast.hours.size() +
                        " hourly forecasts and " + forecast.days.size() + " daily forecasts. From Open-Meteo.");
        invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        background = new LinearGradient(0, 0, w/d, h/d, new int[]{0xFF284D6B,0xFF1C3656,0xFF172B47}, null, Shader.TileMode.CLAMP);
    }
    private void label(Canvas c, String value, float x, float y, float size, int color, Paint.Align align) {
        text.setTextSize(size); text.setColor(color); text.setTextAlign(align);
        c.drawText(value, x, y, text);
    }
    private void panel(Canvas c, float l, float t, float r, float b) {
        rect.set(l,t,r,b); surface.setShader(null); surface.setStyle(Paint.Style.FILL); surface.setColor(0x46364C67);
        c.drawRoundRect(rect, 13, 13, surface);
        surface.setStyle(Paint.Style.STROKE); surface.setStrokeWidth(.5f); surface.setColor(0x38C0D8EC);
        c.drawRoundRect(rect, 13, 13, surface); surface.setStyle(Paint.Style.FILL);
    }
    private void icon(Canvas c, int code, boolean day, float x, float y, int size) {
        int resource = Weather.icon(new Weather.City(0, code, day, 0, 0, false));
        Drawable image = icons.get(resource);
        if (image == null) { image = getResources().getDrawable(resource, null).mutate(); image.setTint(WHITE); icons.put(resource, image); }
        c.save(); c.translate(x, y); image.setBounds(0, 0, size, size); image.draw(c); c.restore();
    }
    @Override protected void onDraw(Canvas canvas) {
        Canvas c = canvas;
        // Keep the daily rows separated even when the header leaves a shorter content area.
        float unit = d * Math.min(1f, getHeight() / (200f * d));
        c.save(); c.scale(unit, unit);
        float w = getWidth()/unit, h = getHeight()/unit;
        rect.set(0,0,w,h); surface.setShader(background); surface.setStyle(Paint.Style.FILL); surface.setAlpha(90);
        c.drawRoundRect(rect,18,18,surface); surface.setShader(null);
        if (current == null || forecast == null) {
            label(c, failed ? "Forecast unavailable" : "Loading forecast…", w/2, h/2-4, 22, WHITE, Paint.Align.CENTER);
            label(c, failed ? "Check your connection. The launcher will retry automatically." : "Shah Alam · Open-Meteo", w/2, h/2+21, 12, MUTED, Paint.Align.CENTER);
            c.restore(); return;
        }
        float currentW = w*.25f;
        float dailyY = h*.64f;
        float footerY = h-7;
        label(c, "Shah Alam", currentW/2, 24, 17, WHITE, Paint.Align.CENTER);
        label(c, current.temp + "°", currentW/2, 76, 52, WHITE, Paint.Align.CENTER);
        label(c, condition(current.code), currentW/2, 94, 14, MUTED, Paint.Align.CENTER);
        label(c, "H:" + current.max + "°  L:" + current.min + "°", currentW/2, 110, 12, WHITE, Paint.Align.CENTER);
        float hourlyL = currentW+8, hourlyR = w-14;
        panel(c,hourlyL,12,hourlyR,dailyY-8);
        label(c,"HOURLY FORECAST",hourlyL+14,29,10,MUTED,Paint.Align.LEFT);
        label(c,"Next " + forecast.hours.size() + " hours",hourlyR-14,29,11,MUTED,Paint.Align.RIGHT);
        float col = (hourlyR-hourlyL-20)/forecast.hours.size();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "ha", Locale.UK);
        for(int i=0;i<forecast.hours.size();i++) {
            Weather.Hour hour = forecast.hours.get(i);
            float x = hourlyL+10+col*(i+.5f);
            label(c,Instant.ofEpochMilli(hour.at).atZone(JakimProvider.MALAYSIA).format(hourFormat),x,48,11,WHITE,Paint.Align.CENTER);
            icon(c,hour.code,hour.day,x-10,57,20);
            label(c,hour.rain > 0 ? hour.rain+"%" : "",x,89,10,RAIN,Paint.Align.CENTER);
            label(c,hour.temp+"°",x,109,17,WHITE,Paint.Align.CENTER);
        }
        panel(c,14,dailyY,w-14,h-21);
        label(c,"5-DAY FORECAST",28,dailyY+16,10,MUTED,Paint.Align.LEFT);
        float dayCol = (w-56)/forecast.days.size();
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for(Weather.Day day:forecast.days) { low=Math.min(low,day.min); high=Math.max(high,day.max); }
        LocalDate today = LocalDate.now(JakimProvider.MALAYSIA);
        for(int i=0;i<forecast.days.size();i++) {
            Weather.Day day=forecast.days.get(i);
            float l=28+dayCol*i, r=l+dayCol-14;
            LocalDate date=Instant.ofEpochMilli(day.at).atZone(JakimProvider.MALAYSIA).toLocalDate();
            label(c,date.equals(today)?"Today":date.format(DateTimeFormatter.ofPattern("EEE",Locale.UK)),l,dailyY+33,12,WHITE,Paint.Align.LEFT);
            icon(c,day.code,true,r-16,dailyY+21,16);
            float y = Math.min(dailyY+49,h-29), left=l+24, right=r-23;
            label(c,day.min+"°",l,y+4,11,MUTED,Paint.Align.LEFT);
            label(c,day.max+"°",r,y+4,11,WHITE,Paint.Align.RIGHT);
            surface.setColor(0x44203C58); rect.set(left,y-2,right,y+2); c.drawRoundRect(rect,2,2,surface);
            float span=Math.max(1,high-low);
            float fillL=left+(right-left)*(day.min-low)/span;
            float fillR=left+(right-left)*(day.max-low)/span;
            surface.setShader(new LinearGradient(left,0,right,0,0xFF82BDD6,0xFFE3CE76,Shader.TileMode.CLAMP)); surface.setAlpha(255);
            rect.set(fillL,y-2,Math.max(fillL+2,fillR),y+2); c.drawRoundRect(rect,2,2,surface); surface.setShader(null);
        }
        String at=Instant.ofEpochMilli(forecast.updated).atZone(JakimProvider.MALAYSIA).format(DateTimeFormatter.ofPattern("HH:mm",Locale.UK));
        label(c,(failed?"Saved forecast · ":"")+"Updated " + at + " · Open-Meteo",w/2,footerY,10,MUTED,Paint.Align.CENTER);
        c.restore();
    }
    static String condition(int code) {
        if(code==0) return "Clear";
        if(code==1||code==2) return "Partly cloudy";
        if(code==3) return "Cloudy";
        if(code==45||code==48) return "Fog";
        if(code>=95) return "Thunderstorms";
        if((code>=71&&code<=77)||code==85||code==86) return "Snow";
        if((code>=51&&code<=67)||(code>=80&&code<=82)) return "Rain showers";
        return "Current conditions";
    }
}
