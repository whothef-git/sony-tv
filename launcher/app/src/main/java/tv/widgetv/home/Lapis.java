package tv.widgetv.home;

/** Design tokens (values from Afnon's Tokens.java). */
final class Lapis {
    static final int LAPIS_900 = 0xFF0C1B33;
    static final int LAPIS_800 = 0xFF12264A;
    static final int GOLD_400 = 0xFFD8B46A;
    static final int GOLD_200 = 0xFFF0D79C;
    static final int PLASTER_100 = 0xFFEFE7D6;
    static final int MIST_400 = 0xFF9FB0CC;

    /** color with its alpha replaced by a (0..1). */
    static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }

    private Lapis() {
    }
}
