package tv.widgetv.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

final class SmartHomeStyle {
    static final int WHITE=0xFFF0F5FF, BLUE=0xFFADC9EA, CYAN=0xFF14CFFF, GREEN=0xFF00D990, GREY=0xFF71849A, AMBER=0xFFFFC558;
    static int dp(Context c,float v) {return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable panel(Context c,boolean focus) {
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xDC12334C,0xD8071A30});
        bg.setCornerRadius(dp(c,12));bg.setStroke(dp(c,focus?2:1),focus?CYAN:0x78467593);return bg;
    }
    static void font(TextView t,float size,int color) {
        t.setTypeface(Typeface.create(t.getResources().getFont(R.font.outfit),400,false));
        t.setFontVariationSettings("'wght' 400");t.setTextSize(size);t.setTextColor(color);
    }
}
