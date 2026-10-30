package tv.widgetv.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen. The activity owns the D-pad completely: nothing is focusable, focus
 * is a (zone, index) pair, and one gold ring (GlassHighlight) springs to whatever that points at.
 * Zones top to bottom: pills (TOP), prayer timeline (or, with prayer_enabled=false, the clock header),
 * dock, grid; plus the Inputs dropdown and the settings panel as modal zones. Everything time-based
 * updates on ACTION_TIME_TICK only. Every user choice comes from res/values/config.xml (Config).
 */
public class MainActivity extends Activity {
    private static final String TAG = Config.TAG;
    private static final int COLS = 7;
    /** Debug broadcast action: "<applicationId>.DEBUG_SKY". */
    private String debugAction() {
        return getPackageName() + ".DEBUG_SKY";
    }

    private enum Zone { TOP, TABS, DOCK, GRID, MENU, SMART, HADITH }

    private ScrollView page;
    private FrameLayout content;
    private SkyView sky;
    private PrayerTimeline timeline;
    private GlassHighlight glass;
    private TextView clock, clockSeconds, awayClock, date, ssid;
    private ImageView[] pills; // the three round buttons: Inputs, Wi-Fi, Settings
    private Typeface ui, quranFont;
    private LinearLayout menu;
    private ScreenTabs screenTabs;
    private ForecastView forecastView;
    private WeatherAtmosphereView atmosphere;
    private View prayerScreen;
    private SmartHomeView smartHome;
    private int tabIdx;
    private RecyclerView dockList, gridList;
    private TileAdapter dockAdapter, gridAdapter;
    private List<TvInputs.Entry> menuEntries = new ArrayList<>();

    private Zone zone = Zone.DOCK;
    private int pillIdx, dockIdx, gridIdx, menuIdx;
    private String pendingPkg;        // focus to restore once apps are loaded
    private boolean resumed;
    private boolean needScroll = true;
    private int firstScreenH;
    private String lastFocusLog = "";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppLoader loader;
    private List<String> lastSignature;   // io thread only
    private List<String> dockPackages, hiddenPackages;
    private SharedPreferences prefs;
    private Weather weather;
    private PrayerDay day;
    /** Debug builds only: a fixed "now" for the timeline and sky (adb fake_time), or -1. */
    private int fakeMinutes = -1; // debug fake_time as minutes of the day, or -1
    /** Debug builds only: a fixed local date for the thought of the day (adb fake_date), or null. */
    private LocalDate fakeDate;
    private PrayerVerse verses;
    private long shownVerseSlot = -1;
    private View hereWeather, thought;
    private ImageView awayFlag;
    private TextView hereTemp, awayTemp, awayRange, thoughtTag, thoughtText, thoughtSource;
    private Config cfg;
    private View header, awayRow;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        page = findViewById(R.id.page);
        content = findViewById(R.id.content);
        sky = findViewById(R.id.sky);
        timeline = findViewById(R.id.timeline);
        glass = findViewById(R.id.glass);
        clock = findViewById(R.id.clock);
        clockSeconds = findViewById(R.id.clock_seconds);
        ((RollingTimeView)clockSeconds).setAlternatingDirection(true);
        awayClock = findViewById(R.id.away_clock);
        ((RollingTimeView)awayClock).setAlternatingDirection(true);
        ssid = findViewById(R.id.ssid);
        hereWeather = findViewById(R.id.here_weather);
        hereTemp = findViewById(R.id.here_temp);
        awayFlag = findViewById(R.id.away_flag);
        awayTemp = findViewById(R.id.away_temp);
        awayRange = findViewById(R.id.away_range);
        thought = findViewById(R.id.thought);
        thoughtTag = findViewById(R.id.thought_tag);
        thoughtText = findViewById(R.id.thought_text);
        thoughtSource = findViewById(R.id.thought_source);
        verses = new PrayerVerse(this);
        cfg = Config.get(this);
        header = findViewById(R.id.header);
        awayRow = findViewById(R.id.away_row);
        timeline.setVisibility(cfg.prayerEnabled ? View.VISIBLE : View.GONE);
        // Second clock: hidden entirely when off; the flag is hidden when its drawable name is unknown.
        awayRow.setVisibility(cfg.secondEnabled ? View.VISIBLE : View.GONE);
        if (cfg.secondFlag != 0) awayFlag.setImageResource(cfg.secondFlag);
        else awayFlag.setVisibility(View.GONE);
        awayClock.setContentDescription(cfg.secondLabel);
        ui = Typeface.create(getResources().getFont(R.font.outfit), 400, false);
        date = findViewById(R.id.date);
        date.setTypeface(ui);
        date.setFontVariationSettings("'wght' 400");
        menu = findViewById(R.id.menu);
        screenTabs = findViewById(R.id.screen_tabs);
        forecastView = findViewById(R.id.forecast_screen);
        atmosphere = findViewById(R.id.weather_atmosphere);
        prayerScreen = findViewById(R.id.prayer_screen);
        smartHome = findViewById(R.id.smart_home_screen);
        smartHome.setFocusCardsCallback(() -> focus(Zone.SMART, 0, false));
        pills = new ImageView[]{findViewById(R.id.btn_inputs), findViewById(R.id.btn_wifi),
                findViewById(R.id.btn_settings)};
        dockPackages = Arrays.asList(getResources().getStringArray(R.array.dock_packages));
        hiddenPackages = Arrays.asList(getResources().getStringArray(R.array.hidden_packages));
        weather = new Weather(this);
        applyClockFont();
        applyTextSize();

        Rect window = windowBounds();
        firstScreenH = window.height();
        findViewById(R.id.first_screen).getLayoutParams().height = firstScreenH;
        // Dock and grid share the same 7 columns: width minus 2 x (56dp margin + 8dp padding).
        int availableW = window.width() - dp(112) - dp(16);
        int colW = availableW / COLS;
        int cardW = colW - dp(16);
        int cardH = cardW * 9 / 16;
        loader = new AppLoader(getApplicationContext(), cardW, cardH);
        dockAdapter = new TileAdapter(cardW, cardH);
        gridAdapter = new TileAdapter(cardW, cardH);
        setUpList(dockList = findViewById(R.id.dock), dockAdapter);
        setUpList(gridList = findViewById(R.id.grid), gridAdapter);

        prefs = getSharedPreferences("focus", MODE_PRIVATE);
        try {
            zone = Zone.valueOf(prefs.getString("zone", Zone.DOCK.name()));
        } catch (IllegalArgumentException old) {
            zone = Zone.DOCK;
        }
        if (zone == Zone.MENU) zone = Zone.TOP;
        if (zone == Zone.SMART) zone = Zone.TABS;
        if (zone == Zone.HADITH) zone = Zone.TABS;
        pillIdx = prefs.getInt("pill", 0);
        pendingPkg = prefs.getString("pkg", null);

        showScreen(0, false);
        content.getViewTreeObserver().addOnGlobalLayoutListener(this::onLayoutChanged);
        page.setOnScrollChangeListener((v, x, y, ox, oy) -> sky.setDim(y / (firstScreenH * 0.6f)));

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        register(packageReceiver, pkgFilter, false);
        if (BuildConfig.DEBUG) register(debugReceiver, new IntentFilter(debugAction()), true);

        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback, main);
    }

    private void setUpList(RecyclerView list, TileAdapter adapter) {
        list.setLayoutManager(new GridLayoutManager(this, COLS));
        list.setItemAnimator(null);
        list.setNestedScrollingEnabled(false);
        list.setFocusable(false);
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        sky.setRunning(true);
        glass.setRunning(true);
        smartHome.setRunning(true);
        atmosphere.setRunning(tabIdx == 2);
        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, timeFilter, false);
        updateClock(); // also the weather stale check
        ((RollingTimeView)clock).setMotionEnabled(true);
        ((RollingTimeView)clockSeconds).setMotionEnabled(true);
        ((RollingTimeView)awayClock).setMotionEnabled(true);
        main.removeCallbacks(secondTick);
        secondTick.run();
        loadApps(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        main.removeCallbacks(secondTick);
        ((RollingTimeView)clock).setMotionEnabled(false);
        ((RollingTimeView)clockSeconds).setMotionEnabled(false);
        ((RollingTimeView)awayClock).setMotionEnabled(false);
        sky.setRunning(false);
        glass.setRunning(false);
        smartHome.setRunning(false);
        atmosphere.setRunning(false);
        unregisterReceiver(timeReceiver);
        if (zone == Zone.MENU) closeMenu();
        String pkg = focusedPkg();
        prefs.edit().putString("zone", zone.name()).putInt("pill", pillIdx).putString("pkg", pkg).apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME pressed while already home: back to the first dock tile. Returning from an app keeps focus.
        // Android pauses the activity before onNewIntent, so "already home" = our window still has focus.
        boolean atHome = resumed || hasWindowFocus();
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewIntent: resumed=" + resumed + " windowFocus=" + hasWindowFocus());
        if (atHome) {
            pendingPkg = null; // a still-loading app list must not restore the old focus over this
            if (dockAdapter.getItemCount() == 0 && zone != Zone.MENU) {
                zone = Zone.DOCK;
                dockIdx = 0; // applied by applyApps; with tiles present focus() below un-highlights the old one first
            }
        }
        if (atHome) showScreen(0, false);
        if (atHome && dockAdapter.getItemCount() > 0) {
            if (zone == Zone.MENU) closeMenu();
            focus(Zone.DOCK, 0, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        if (BuildConfig.DEBUG) unregisterReceiver(debugReceiver);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        io.shutdownNow();
        weather.shutdown();
        sky.shutdown();
        verses.shutdown();
        smartHome.shutdown();
    }

    private void showScreen(int index, boolean animate) {
        int previous = tabIdx;
        boolean changed = previous != index;
        tabIdx = index;
        boolean smart = index == 3;
        boolean hideApps = smart || index == 1 || index == 2;
        // INVISIBLE retains all original bounds so switching pages cannot move the Home header.
        awayRow.setVisibility(smart || !cfg.secondEnabled ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.dock_band).setVisibility(hideApps ? View.INVISIBLE : View.VISIBLE);
        gridList.setVisibility(hideApps ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.apps_heading).setVisibility(hideApps ? View.INVISIBLE : View.VISIBLE);
        page.setDescendantFocusability(smart ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        smartHome.setScreenActive(smart);
        screenTabs.setState(index, zone == Zone.TABS);
        atmosphere.setScreenActive(index == 2, animate);
        atmosphere.setRunning(resumed && index == 2);
        if (index == 1) renderVerse();
        View target = index == 1 ? prayerScreen : index == 2 ? forecastView : index == 3 ? smartHome : null;
        if (!animate) {
            prayerScreen.animate().cancel(); forecastView.animate().cancel();
            smartHome.animate().cancel(); smartHome.setVisibility(smart ? View.VISIBLE : View.GONE);
            smartHome.setAlpha(1f); smartHome.setTranslationX(0);
            prayerScreen.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
            forecastView.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
            prayerScreen.setAlpha(1f); forecastView.setAlpha(1f);
            prayerScreen.setTranslationX(0); forecastView.setTranslationX(0);
        } else if (changed) {
            float direction = index > previous ? 1f : -1f;
            View outgoing = previous == 1 ? prayerScreen : previous == 2 ? forecastView : previous == 3 ? smartHome : null;
            if (outgoing != null) {
                outgoing.animate().cancel();
                outgoing.animate().alpha(0f).translationX(-direction * dp(8)).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).withLayer()
                        .withEndAction(() -> {
                            if (tabIdx != previous) {
                                outgoing.setVisibility(View.GONE); outgoing.setTranslationX(0); outgoing.setAlpha(1f);
                            }
                        }).start();
            }
            if (target != null) {
                target.animate().cancel();
                if (target.getVisibility() != View.VISIBLE) { target.setAlpha(0f); target.setTranslationX(direction * dp(8)); }
                target.setVisibility(View.VISIBLE);
                target.animate().alpha(1f).translationX(0).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).withLayer().start();
            }
        }
        Log.i(TAG, "screen: " + ScreenTabs.LABELS[index]);
    }

    // ---------------------------------------------------------------- apps

    private void loadApps(boolean force) {
        io.execute(() -> {
            Map<String, AppLoader.Candidate> found = loader.query();
            List<String> sig = new ArrayList<>();
            for (AppLoader.Candidate c : found.values()) sig.add(c.key());
            if (!force && sig.equals(lastSignature)) return;
            lastSignature = sig;

            List<AppEntry> dock = new ArrayList<>();
            for (String pkg : dockPackages) {
                AppLoader.Candidate c = found.remove(pkg);
                if (c != null) dock.add(loader.entry(c));
            }
            for (String pkg : hiddenPackages) found.remove(pkg);
            List<AppEntry> grid = new ArrayList<>();
            for (AppLoader.Candidate c : found.values()) grid.add(loader.entry(c));
            grid.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            main.post(() -> applyApps(dock, grid));
        });
    }

    private void applyApps(List<AppEntry> dock, List<AppEntry> grid) {
        String keep = pendingPkg != null ? pendingPkg : focusedPkg();
        pendingPkg = null;
        dockAdapter.setItems(dock);
        gridAdapter.setItems(grid);
        int i = indexOf(dock, keep);
        if (i >= 0) dockIdx = i;
        int g = indexOf(grid, keep);
        if (g >= 0) gridIdx = g;
        dockIdx = clamp(dockIdx, dock.size());
        gridIdx = clamp(gridIdx, grid.size());
        if (zone == Zone.GRID && grid.isEmpty()) zone = Zone.DOCK;
        if (zone == Zone.DOCK && dock.isEmpty()) zone = Zone.TABS;
        dockAdapter.focused = zone == Zone.DOCK ? dockIdx : -1;
        gridAdapter.focused = zone == Zone.GRID ? gridIdx : -1;
        if (zone == Zone.TOP) setLook(true);
        needScroll = true; // scroll + ring are placed by onLayoutChanged once the tiles exist
        Log.i(TAG, "apps: dock=" + dock.size() + " grid=" + grid.size());
    }

    private static int indexOf(List<AppEntry> list, String pkg) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).pkg().equals(pkg)) return i;
        return -1;
    }

    private String focusedPkg() {
        if (zone == Zone.DOCK && dockIdx < dockAdapter.items.size()) return dockAdapter.items.get(dockIdx).pkg();
        if (zone == Zone.GRID && gridIdx < gridAdapter.items.size()) return gridAdapter.items.get(gridIdx).pkg();
        return null;
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            io.execute(() -> {
                if (pkg != null) loader.forget(pkg);
            });
            loadApps(true);
        }
    };

    // ---------------------------------------------------------------- D-pad

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_MENU && tabIdx == 3) {
            if (!down) smartHome.openManagement();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!down && !ev.isCanceled()) {
                    if (tabIdx == 3 && zone == Zone.TABS && ev.getEventTime() - ev.getDownTime() >= 600) smartHome.openManagement();
                    else select();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Back closes Inputs first; otherwise it returns to the Home screen.
                if (!down && zone == Zone.MENU) { closeMenu(); return true; }
                if (!down && zone == Zone.HADITH) { focus(Zone.TABS, 1, true); return true; }
                if (!down && tabIdx != 0) {
                    showScreen(0, true);
                    if (zone == Zone.TABS || zone == Zone.SMART) focus(Zone.TABS, 0, true);
                }
                return true;
            default:
                return super.dispatchKeyEvent(ev);
        }
    }

    private void move(int code) {
        int dockN = dockAdapter.getItemCount(), gridN = gridAdapter.getItemCount();
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, dn = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        switch (zone) {
            case TOP:
                if (left && pillIdx > 0) focus(Zone.TOP, pillIdx - 1, true);
                else if (right && pillIdx < pills.length - 1) focus(Zone.TOP, pillIdx + 1, true);
                else if (dn) focus(Zone.TABS, tabIdx, true);
                break;
            case TABS:
                if (left) focus(Zone.TABS, (tabIdx + ScreenTabs.LABELS.length - 1) % ScreenTabs.LABELS.length, true);
                else if (right) focus(Zone.TABS, (tabIdx + 1) % ScreenTabs.LABELS.length, true);
                else if (up) focus(Zone.TOP, pillIdx, true);
                else if (dn && tabIdx == 3) focus(Zone.SMART, 0, true);
                else if (dn && tabIdx == 1) break; // The complete hadith fits; no scroll zone.
                else if (dn && tabIdx == 2) break; // Weather uses the dock area; never focus hidden apps.
                else if (dn && dockN > 0) focus(Zone.DOCK, clamp(dockIdx, dockN), true);
                else if (dn && gridN > 0) focus(Zone.GRID, clamp(gridIdx, gridN), true);
                break;
            case HADITH:
                focus(Zone.TABS, 1, true);
                break;
            case SMART:
                if (smartHome.move(code)) focus(Zone.TABS, 3, true);
                break;
            case DOCK:
                if (left && dockIdx > 0) focus(Zone.DOCK, dockIdx - 1, true);
                else if (right && dockIdx < dockN - 1) focus(Zone.DOCK, dockIdx + 1, true);
                else if (up) focus(Zone.TABS, tabIdx, true);
                else if (dn && gridN > 0) focus(Zone.GRID, Math.min(dockIdx, gridN - 1), true);
                break;
            case GRID: {
                int col = gridIdx % COLS, row = gridIdx / COLS;
                if (left && col > 0) focus(Zone.GRID, gridIdx - 1, true);
                else if (right && col < COLS - 1 && gridIdx + 1 < gridN) focus(Zone.GRID, gridIdx + 1, true);
                else if (up && row > 0) focus(Zone.GRID, gridIdx - COLS, true);
                else if (up && dockN > 0) focus(Zone.DOCK, clamp(dockIdx, dockN), true); // last focused dock tile
                else if (up) focus(Zone.TABS, tabIdx, true);
                else if (dn && gridIdx + COLS < gridN) focus(Zone.GRID, gridIdx + COLS, true);
                else if (dn && (row + 1) * COLS < gridN) focus(Zone.GRID, gridN - 1, true); // short last row
                break;
            }
            case MENU:
                if (up && menuIdx > 0) focus(Zone.MENU, menuIdx - 1, true);
                else if (dn && menuIdx < menuEntries.size() - 1) focus(Zone.MENU, menuIdx + 1, true);
                break;
        }
    }

    private void select() {
        switch (zone) {
            case SMART:
                smartHome.openSelected();
                break;
            case TOP:
                if (pillIdx == 0) openMenu();
                else if (pillIdx == 1) start(new Intent(Settings.ACTION_WIFI_SETTINGS));
                else start(new Intent(Settings.ACTION_SETTINGS));
                break;
            case TABS:
                break; // Read-only screens switch on focus, never open prayer settings.
            case DOCK:
                if (dockIdx < dockAdapter.items.size()) start(dockAdapter.items.get(dockIdx).launchIntent());
                break;
            case GRID:
                if (gridIdx < gridAdapter.items.size()) start(gridAdapter.items.get(gridIdx).launchIntent());
                break;
            case MENU:
                Intent intent = menuEntries.get(menuIdx).intent;
                closeMenu();
                start(intent);
                break;
        }
    }

    private void start(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "cannot start " + intent, e);
        }
    }

    // ---------------------------------------------------------------- focus rendering

    private void focus(Zone z, int idx, boolean animate) {
        setLook(false);
        zone = z;
        switch (z) {
            case TOP: pillIdx = idx; break;
            case DOCK: dockIdx = idx; break;
            case GRID: gridIdx = idx; break;
            case MENU: menuIdx = idx; break;
            case TABS: showScreen(idx, true); break;
            default: break;
        }
        dockAdapter.focused = z == Zone.DOCK ? dockIdx : -1;
        gridAdapter.focused = z == Zone.GRID ? gridIdx : -1;
        setLook(true);
        scrollToZone(animate);
        updateGlass(animate);
    }

    private View focusedView() {
        switch (zone) {
            case TOP: return pills[pillIdx];
            case TABS: return screenTabs;
            case SMART: return smartHome.selectedView();
            case HADITH: return thoughtText;
            case DOCK: return dockList.getLayoutManager().findViewByPosition(dockIdx);
            case GRID: return gridList.getLayoutManager().findViewByPosition(gridIdx);
            case MENU: return menu.getChildAt(menuIdx);
            default: return null;
        }
    }

    private void setLook(boolean on) {
        View v = focusedView();
        if (v == null) return;
        switch (zone) {
            case HADITH:
                thoughtTag.setAlpha(on ? 1f : .8f);
                break;
            case SMART:
                smartHome.setCardsFocused(on);
                break;
            case DOCK:
            case GRID:
                TileAdapter.setLook(v, on);
                break;
            case TOP: {
                ImageView b = (ImageView) v;
                b.setBackgroundResource(on ? R.drawable.bg_btn_focus : R.drawable.bg_btn);
                b.setImageTintList(ColorStateList.valueOf(on ? Lapis.LAPIS_900 : Lapis.PLASTER_100));
                ssid.setVisibility(on && pillIdx == 1 ? View.VISIBLE : View.GONE); // SSID only while Wi-Fi is focused
                break;
            }
            case MENU:
                rowLook(v, on);
                break;
            case TABS:
                screenTabs.setState(tabIdx, on);
                break;
            default:
                break;
        }
    }

    /** Dropdown / panel row: gold fill with lapis text when focused. */
    private static void rowLook(View row, boolean on) {
        row.setBackgroundResource(on ? R.drawable.bg_row_focus : 0);
        if (row instanceof TextView) {
            ((TextView) row).setTextColor(on ? Lapis.LAPIS_900 : Lapis.PLASTER_100);
        } else if (row instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) row;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = (TextView) g.getChildAt(i);
                t.setTextColor(on ? Lapis.LAPIS_900 : (i == 0 ? Lapis.PLASTER_100 : Lapis.GOLD_400));
            }
        }
    }

    /** Re-place the ring after any layout pass (data loaded, SSID text width changed, menu shown). */
    private String clockFitKey;
    private void onLayoutChanged() {
        float timeBaseline=clock.getTop()+clock.getBaseline();
        clockSeconds.setTranslationY(timeBaseline-clockSeconds.getTop()-clockSeconds.getBaseline());
        hereWeather.setTranslationY(timeBaseline-hereWeather.getTop()-hereTemp.getTop()-hereTemp.getBaseline());
        // Align the existing weather row with the end of the first date paragraph (the Hijri year).
        if(date.getLayout()!=null&&hereWeather.getVisibility()==View.VISIBLE){
            int end=date.getText().toString().indexOf('\n');
            if(end<0)end=date.length();
            float dateRight=date.getLeft()+date.getCompoundPaddingLeft()+date.getLayout().getPrimaryHorizontal(end);
            // Fit fixed digit slots only when header content/font changes, never on a seconds tick.
            String fitKey=date.getText().subSequence(0,end)+"|"+dateRight+"|"+clockSeconds.getWidth()
                    +"|"+hereWeather.getWidth()+"|"+clock.getTextSize()+"|"+clock.getTypeface();
            if(!fitKey.equals(clockFitKey)){
                android.text.TextPaint measure=new android.text.TextPaint(clock.getPaint());
                measure.setTextScaleX(1);
                float natural=measure.measureText("88:88")+dp(2);
                int available=Math.max(dp(100),(int)Math.floor(dateRight-clock.getLeft()-clockSeconds.getWidth()-hereWeather.getWidth()-dp(2)));
                clockFitKey=fitKey;
                clock.setTextScaleX(Math.min(1,(available-2)/Math.max(1,natural)));
                ViewGroup.LayoutParams slot=clock.getLayoutParams();
                if(slot.width!=available){slot.width=available;clock.setLayoutParams(slot);}
            }
            float weatherRight=((View)hereWeather.getParent()).getLeft()+hereWeather.getRight();
            hereWeather.setTranslationX(Math.max(0,dateRight-weatherRight));
        }

        int tabsTop = header.getBottom() + dp(2);
        FrameLayout.LayoutParams nav = (FrameLayout.LayoutParams) screenTabs.getLayoutParams();
        if (header.getHeight() > 0 && nav.topMargin != tabsTop) {
            nav.topMargin = tabsTop;
            screenTabs.setLayoutParams(nav);
        }
        int screenTop = tabsTop + dp(52);
        FrameLayout.LayoutParams smartLp = (FrameLayout.LayoutParams) smartHome.getLayoutParams();
        int smartHeight = Math.max(dp(230), firstScreenH - screenTop - dp(18));
        if (smartLp.topMargin != screenTop || smartLp.height != smartHeight) {
            smartLp.topMargin = screenTop; smartLp.height = smartHeight;
            smartHome.setLayoutParams(smartLp);
        }
        FrameLayout.LayoutParams prayerLp = (FrameLayout.LayoutParams) prayerScreen.getLayoutParams();
        int prayerHeight = Math.max(dp(250), firstScreenH - screenTop - dp(18));
        if (prayerLp.topMargin != screenTop || prayerLp.height != prayerHeight) {
            prayerLp.topMargin = screenTop;
            prayerLp.height = prayerHeight;
            prayerScreen.setLayoutParams(prayerLp);
        }
        FrameLayout.LayoutParams forecastLp = (FrameLayout.LayoutParams) forecastView.getLayoutParams();
        int available = Math.max(dp(250), firstScreenH - screenTop - dp(18));
        if (forecastLp.topMargin != screenTop || forecastLp.height != available) {
            forecastLp.topMargin = screenTop;
            forecastLp.height = available;
            forecastView.setLayoutParams(forecastLp);
        }
        if (needScroll && focusedView() != null) {
            needScroll = false;
            if (zone == Zone.TOP) setLook(true);
            scrollToZone(false);
        }
        updateGlass(true);
    }

    private void updateGlass(boolean animate) {
        // Fade the app glow in place; never morph it into a tab/control rectangle.
        if (zone != Zone.DOCK && zone != Zone.GRID) {
            glass.setRingVisible(false);
            return;
        }
        View v = focusedView();
        if (v == null || v.getWidth() == 0) return;
        RectF r;
        float radius;
        boolean isDock = zone == Zone.DOCK;

        if (isDock || zone == Zone.GRID) {
            View card = v.findViewById(R.id.card);
            r = rectInContent(card);
            radius = 0f;
        } else if (zone == Zone.TABS) {
            r = rectInContent(v);
            r.inset(-dp(14), -dp(8));
            radius = dp(16);
        } else {
            r = rectInContent(v);
            radius = r.height() / 2;
        }

        boolean ring = zone == Zone.DOCK || zone == Zone.GRID;
        String focusLog = "focus: zone=" + zone + " ring=" + ring + " rect=" + r.toShortString() + " isDock=" + isDock;
        if (BuildConfig.DEBUG && !focusLog.equals(lastFocusLog)) Log.d(TAG, lastFocusLog = focusLog);
        if (isDock || zone == Zone.GRID) {
            TileAdapter adapter = isDock ? dockAdapter : gridAdapter;
            int index = isDock ? dockIdx : gridIdx;
            if (index >= 0 && index < adapter.items.size()) {
                glass.setAmbientColor(adapter.items.get(index).ambientColor, animate);
            }
        }
        glass.setRingVisible(ring);
        glass.moveTo(r, radius, isDock, animate);
    }

    /** Dock/timeline/top bar keep the first screen in view; grid rows sit 80dp below the top edge. */
    private void scrollToZone(boolean smooth) {
        int y = 0;
        if (zone == Zone.GRID) {
            View v = focusedView();
            if (v == null) return;
            y = Math.max(0, (int) rectInContent(v).top - dp(80));
        }
        if (smooth) page.smoothScrollTo(0, y);
        else page.scrollTo(0, y);
    }

    private RectF rectInContent(View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        content.offsetDescendantRectToMyCoords(v, r);
        return new RectF(r);
    }

    // ---------------------------------------------------------------- Inputs dropdown

    private void openMenu() {
        menuEntries = TvInputs.load(this);
        if (menuEntries.isEmpty()) return;
        menu.removeAllViews();
        for (TvInputs.Entry e : menuEntries) {
            TextView row = rowText(e.label, 16, Lapis.PLASTER_100);
            row.setPadding(dp(16), 0, dp(16), 0);
            menu.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }
        // Right-aligned under the button row (the buttons sit at the screen's right edge).
        RectF pill = rectInContent(pills[0]);
        RectF last = rectInContent(pills[pills.length - 1]);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
        lp.leftMargin = (int) last.right - lp.width;
        lp.topMargin = (int) pill.bottom + dp(12);
        menu.setLayoutParams(lp);
        menu.setAlpha(0f);
        menu.setVisibility(View.VISIBLE);
        menu.animate().alpha(1f).setDuration(160).start();
        focus(Zone.MENU, 0, true);
    }

    private void closeMenu() {
        menu.setVisibility(View.GONE);
        menu.removeAllViews();
        zone = Zone.TOP; // the rows are gone; focus() below has nothing to un-highlight
        focus(Zone.TOP, 0, true);
    }

    private TextView rowText(CharSequence text, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(ui);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        return t;
    }

    // ---------------------------------------------------------------- clock, date, prayer, weather

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    /**
     * "Now" for everything on screen. Debug builds can fake it (adb fake_time / fake_date) for a day
     * simulation; the TV's real clock and zone are never touched. Release builds always use the real clock.
     */
    private long nowMillis() {
        if (fakeMinutes < 0 && fakeDate == null) return System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        java.time.LocalDate d = fakeDate != null ? fakeDate : java.time.LocalDate.now(zone);
        java.time.LocalTime t = fakeMinutes >= 0 ? java.time.LocalTime.of(fakeMinutes / 60, fakeMinutes % 60)
                : java.time.LocalTime.now(zone);
        return ZonedDateTime.of(d, t, zone).toInstant().toEpochMilli();
    }

    private ZonedDateTime nowZoned() {
        return java.time.Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault());
    }

    /** Clocks + prayer day + date line + thought, without the weather stale check. */
    private void renderClocks() {
        renderLocalTime(nowZoned(), DateFormat.is24HourFormat(this));
        refreshPrayer();
    }

    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(this);
        ZonedDateTime local = nowZoned();
        renderLocalTime(local,h24);
        refreshPrayer(); // also redraws the date line and the second-clock line
        weather.maybeFetch(false, this::renderDateAndWeather);
        JakimProvider.maybeRefresh(this, () -> { if (resumed) refreshPrayer(); });
    }

    /** A lightweight wall-clock tick: no prayer, weather or smart-device refresh each second. */
    private final Runnable secondTick=new Runnable(){@Override public void run(){
        if(!resumed)return;
        renderLocalTime(nowZoned(),DateFormat.is24HourFormat(MainActivity.this));
        main.postDelayed(this,1000-Math.floorMod(System.currentTimeMillis(),1000));
    }};
    private void renderLocalTime(ZonedDateTime local,boolean h24){
        String time=hm(local,h24),seconds=String.format(Locale.US,":%02d",local.getSecond());
        if(!time.contentEquals(clock.getText()))clock.setText(time);
        if(!seconds.contentEquals(clockSeconds.getText()))clockSeconds.setText(seconds);
    }

    /** "12:30" (or "00:30" in 24-hour mode), no AM/PM. */
    private static String hm(ZonedDateTime z, boolean h24) {
        int h = z.getHour();
        return h24 ? String.format(Locale.US, "%02d:%02d", h, z.getMinute())
                : String.format(Locale.US, "%d:%02d", h % 12 == 0 ? 12 : h % 12, z.getMinute());
    }

    /** "3:00 am" style, for the second-clock line, the panel and the logs. */
    private static String hmAmPm(ZonedDateTime z, boolean h24) {
        return h24 ? hm(z, true) : hm(z, false) + (z.getHour() < 12 ? " am" : " pm");
    }

    private String clockText(long millis) {
        return hmAmPm(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()), DateFormat.is24HourFormat(this));
    }

    /**
     * Header clock font from Prayer settings: Cormorant Garamond Light (wght 300) or Outfit Thin (wght 200).
     * The Kitchener line uses the same family a little heavier (500 / 300) so it stays readable at ~32px.
     */
    /**
     * "Text size" (Normal / Large / Larger) for the secondary text, in sp (= px at 1080p / 2). The main clock and
     * the dock never change. Spans that embed icons are rebuilt afterwards because their size follows the text.
     */
    private void applyTextSize() {
        int l = PrayerPrefs.textSize(this);
        float[] hereT = {24, 28, 32}, dateT = {11, 12, 13}, awayT = {22, 26, 30}, awayR = {13, 15, 17};
        hereTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, hereT[l]);
        clockSeconds.setTextSize(TypedValue.COMPLEX_UNIT_SP, hereT[l]);
        ViewGroup.LayoutParams secondsSlot=clockSeconds.getLayoutParams();
        secondsSlot.width=(int)Math.ceil(clockSeconds.getPaint().measureText(":88"))+dp(2);
        clockSeconds.setLayoutParams(secondsSlot);
        clockFitKey=null;
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        awayClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, awayT[l]);
        awayTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        awayRange.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ViewGroup.LayoutParams fl = awayFlag.getLayoutParams();
        fl.height = Math.round(16 * getResources().getDisplayMetrics().scaledDensity);
        fl.width = Math.round((64f / 3f) * getResources().getDisplayMetrics().scaledDensity);
        awayFlag.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        awayFlag.setLayoutParams(fl);
        int timelineDp = timeline.setTextScale(15 + l * 2, 11 + l * 2);
        timeline.getLayoutParams().height = dp(timelineDp);
        timeline.requestLayout();
        LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) thought.getLayoutParams();
        // Stack below the timeline so larger text and wrapped thoughts cannot overlap.
        tp.topMargin = cfg.prayerEnabled ? dp(12) : 0;
        thought.setLayoutParams(tp);
        thoughtTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20 + l);
        Typeface hadithFont = Typeface.create(getResources().getFont(R.font.alexandria), 400, false);
        thoughtTag.setTypeface(hadithFont);
        thoughtText.setTypeface(hadithFont);
        thoughtSource.setTypeface(hadithFont);
        thoughtTag.setFontVariationSettings("'wght' 400");
        thoughtText.setFontVariationSettings("'wght' 400");
        thoughtSource.setFontVariationSettings("'wght' 400");
        quranFont = getResources().getFont(R.font.uthmanic_hafs);
        thoughtText.setAutoSizeTextTypeUniformWithConfiguration(1, 16 + l, 1, TypedValue.COMPLEX_UNIT_SP);
        thoughtSource.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9 + l);
        if (day != null) renderDateAndWeather();
    }

    private void applyClockFont() {
        clockFitKey=null;
        clock.setEllipsize(null);
        clockSeconds.setEllipsize(null);
        boolean outfit = PrayerPrefs.clockFont(this) == 1;
        Typeface tf = getResources().getFont(outfit ? R.font.outfit : R.font.cormorant_garamond);
        clock.setTypeface(tf);
        clock.setFontVariationSettings(outfit ? "'wght' 200" : "'wght' 300");
        clockSeconds.setTypeface(tf);
        clockSeconds.setFontVariationSettings(outfit ? "'wght' 200" : "'wght' 300");
        awayClock.setTypeface(tf);
        awayClock.setFontVariationSettings(outfit ? "'wght' 300" : "'wght' 500");
        // Temperatures in the clock family: light beside the main clock, as heavy as the second-clock time beside it.
        hereTemp.setTypeface(tf);
        hereTemp.setFontVariationSettings(outfit ? "'wght' 200" : "'wght' 300");
        Typeface weatherFont = Typeface.create(getResources().getFont(R.font.outfit), 400, false);
        awayTemp.setTypeface(weatherFont);
        awayTemp.setFontVariationSettings("'wght' 400");
        awayRange.setTypeface(weatherFont);
        awayRange.setFontVariationSettings("'wght' 400");
        // Cormorant's default figures are old-style (x-height); clocks want lining figures.
        for (TextView t : new TextView[]{clock, clockSeconds, awayClock, hereTemp, awayTemp}) t.setFontFeatureSettings("lnum");
        clock.setFontFeatureSettings("lnum, tnum");
        clockSeconds.setFontFeatureSettings("lnum, tnum");
        awayClock.setFontFeatureSettings("lnum, tnum");
    }

    /**
     * Recompute today's sun/prayer times (settings or minute changed) and push them to the timeline and the
     * sky. With prayer_enabled=false the same astronomy only drives the sky (dawn, sunrise, noon, afternoon,
     * sunset, night) and the timeline stays hidden.
     */
    private void refreshPrayer() {
        long now = nowMillis();
        day = PrayerPrefs.skyDay(this, now); // Official SGR01, zero user offsets; astronomy is only a sky fallback.
        timeline.setDay(day.isJakim ? day : null);
        sky.update(day);
        renderDateAndWeather();
        renderVerse();
        if (BuildConfig.DEBUG && cfg.prayerEnabled) timeline.post(this::logExpectedDot);
    }

    private void renderDateAndWeather() {
        if (day == null) return;
        Weather.City[] w = weather.current();
        if (forecastView != null) forecastView.setWeather(w, weather.forecast(), weather.lastFetchFailed());
        if (atmosphere != null) atmosphere.setWeather(w == null ? null : w[0]);
        boolean h24 = DateFormat.is24HourFormat(this);
        ZonedDateTime local = nowZoned();
        ZonedDateTime away = local.withZoneSameInstant(cfg.secondZone);
        String weekday = away.toLocalDate().equals(local.toLocalDate()) ? ""
                : away.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " ";
        String awayTime=weekday+hmAmPm(away,h24);
        if(!awayTime.contentEquals(awayClock.getText()))awayClock.setText(awayTime);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, DateFormat.format("EEEE d MMMM", nowMillis()), Lapis.PLASTER_100);
        if (cfg.prayerEnabled) {
            append(sb, "   ·   ", Lapis.MIST_400);
            append(sb, day.isJakim ? day.hijri() : "Tarikh Hijri belum tersedia", Lapis.GOLD_400);
        }
        if (w != null && w[0].hasRange) {
            sb.append('\n');
            int rangeStart = sb.length();
            sb.append(rangeText(w[0], Lapis.MIST_400));
            sb.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(13 * getResources().getDisplayMetrics().scaledDensity)),
                    rangeStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        date.setText(sb);

        // Weather sits beside each clock; hidden entirely (no gap) when never fetched or older than 3 h.
        boolean awayW = w != null && w.length > 1;
        hereWeather.setVisibility(w == null ? View.GONE : View.VISIBLE);
        awayTemp.setVisibility(awayW ? View.VISIBLE : View.GONE);
        awayRange.setVisibility(awayW && w[1].hasRange ? View.VISIBLE : View.GONE);
        if (w == null) return;
        hereTemp.setText(tempText(w[0], Lapis.PLASTER_100, hereTemp.getTextSize()));
        if (!awayW) return;
        SpannableStringBuilder awayConditions = new SpannableStringBuilder();
        appendIcon(awayConditions, Weather.icon(w[1]), Lapis.MIST_400,
                14 * getResources().getDisplayMetrics().scaledDensity);
        awayConditions.append(" ").append(String.valueOf(w[1].temp)).append("°");
        awayTemp.setText(awayConditions);
        awayRange.setText(rangeText(w[1], Lapis.alpha(Lapis.MIST_400, 0.7f)));
    }

    /** "[icon] 32°" */
    private CharSequence tempText(Weather.City c, int color, float textPx) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendIcon(sb, Weather.icon(c), color, textPx * 0.8f);
        sb.append(" ").append(String.valueOf(c.temp)).append("°");
        return sb;
    }

    /** "↑34° ↓22°" in mist at ~70%. */
    private static CharSequence rangeText(Weather.City c, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, "↑" + c.max + "°  ↓" + c.min + "°", color);
        // Alexandria's arrows are tiny; draw them with the system sans so they read at 3 m.
        for (int i = 0; i < sb.length(); i++) {
            char ch = sb.charAt(i);
            if (ch == '↑' || ch == '↓') {
                sb.setSpan(new TypefaceSpan(Typeface.DEFAULT), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    /** Original API text, changing only when the current official JAKIM prayer interval changes. */
    private void renderVerse() {
        if (!cfg.thoughtsEnabled) {
            thought.setVisibility(View.GONE);
            return;
        }
        thought.setVisibility(View.VISIBLE);
        thoughtTag.setText("AL QURAN VERSE OF THE DAY");
        thoughtSource.setVisibility(View.GONE);
        if (day == null || !day.isJakim) {
            thoughtText.setText("Jadual waktu solat JAKIM belum tersedia. Ayat akan dimuatkan apabila jadual tersedia.");
            shownVerseSlot = -1;
            return;
        }
        long slot = PrayerVerse.slot(day.now, day.times, day.yesterdayIsha);
        PrayerVerse.Entry entry = verses.get(slot);
        if (entry != null) {
            if (slot != shownVerseSlot) {
                thoughtText.setText(entry.display(quranFont));
                shownVerseSlot = slot;
            }
        } else {
            shownVerseSlot = -1;
            thoughtText.setText(verses.failed() ? "Ayat Al-Quran belum dapat dimuatkan. Sila semak sambungan Internet."
                    : "Memuatkan ayat Al-Quran dan terjemahan Bahasa Melayu…");
            if (tabIdx == 1) verses.maybeFetch(slot, () -> { if (!isDestroyed()) renderVerse(); });
        }
    }

    private static void append(SpannableStringBuilder sb, CharSequence text, int color) {
        int s = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A weather icon at ~0.9× the text size, centred on the line. */
    private void appendIcon(SpannableStringBuilder sb, int res, int color, float textPx) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(color);
        int size = Math.round(textPx * 0.9f * 1.15f); // the 24-unit icon has ~2 units of padding per side
        d.setBounds(0, 0, size, size);
        int s = sb.length();
        sb.append("￼");
        // ALIGN_CENTER is API 29; Android 9 sits the icon on the line's bottom instead.
        int align = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BOTTOM;
        sb.setSpan(new ImageSpan(d, align), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String timesLog() {
        StringBuilder sb = new StringBuilder();
        for (int p : PrayerTimeline.COLUMNS) sb.append(PrayerDay.NAMES[p]).append('=').append(clockText(day.times[p])).append(' ');
        return sb.toString().trim();
    }

    /**
     * Debug receipt: the dot x the timeline should draw, computed here from the times and the view width
     * (independently of PrayerTimeline.onDraw, which logs the x it actually drew).
     */
    private void logExpectedDot() {
        int w = timeline.getWidth();
        if (w == 0 || day == null) return;
        float col = w / 5f;
        long now = day.now;
        long[] t = new long[5];
        for (int i = 0; i < 5; i++) t[i] = day.times[PrayerTimeline.COLUMNS[i]];
        String seg;
        float frac, x;
        if (now < t[0]) {
            frac = PrayerTimeline.fraction(now, day.yesterdayIsha, t[0]);
            x = col * 0.5f * frac;
            seg = "Isha(yesterday)->Fajr";
        } else if (now >= t[4]) {
            frac = PrayerTimeline.fraction(now, t[4], day.tomorrowFajr);
            x = col * 4.5f + col * 0.5f * frac;
            seg = "Isha->Fajr(tomorrow)";
        } else {
            int k = 0;
            while (k < 3 && now >= t[k + 1]) k++;
            frac = (now - t[k]) / (float) (t[k + 1] - t[k]);
            x = col * (k + 0.5f) + col * frac;
            seg = PrayerDay.NAMES[PrayerTimeline.COLUMNS[k]] + "->" + PrayerDay.NAMES[PrayerTimeline.COLUMNS[k + 1]];
        }
        Log.i(TAG, String.format(Locale.US, "timeline expected: now=%s%s segment=%s fraction=%.4f expectedX=%.1f times=%s",
                clockText(now), fakeMinutes >= 0 || fakeDate != null ? " (fake)" : "", seg, frac, x, timesLog()));
    }

    /** Debug builds only: adb preview hooks for the sky, the timeline clock and the weather. */
    private final BroadcastReceiver debugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String period = intent.getStringExtra("period");
            if (period != null) {
                sky.forcedPeriod = Arrays.asList(SkyView.PERIODS).indexOf(period); // "auto" → -1
                if (sky.forcedPeriod < 0) sky.forcedPattern = -1;
            }
            if (intent.hasExtra("pattern")) sky.forcedPattern = intent.getIntExtra("pattern", -1);
            String fd = intent.getStringExtra("fake_date");
            if (fd != null) {
                try {
                    fakeDate = "auto".equals(fd) ? null : LocalDate.parse(fd);
                } catch (RuntimeException bad) {
                    fakeDate = null;
                }
                shownVerseSlot = -1;
            }
            String fake = intent.getStringExtra("fake_time");
            if (fake != null) {
                fakeMinutes = -1;
                String[] hm = fake.split(":");
                if (hm.length == 2) fakeMinutes = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
            }
            Log.i(TAG, "debug: period=" + sky.forcedPeriod + " pattern=" + sky.forcedPattern + " fakeMinutes=" + fakeMinutes
                    + " fakeDate=" + fakeDate + " now=" + nowZoned());
            if (intent.getBooleanExtra("weather_refresh", false)) weather.maybeFetch(true, MainActivity.this::renderDateAndWeather);
            if (intent.hasExtra("weather_effect_preview")) {
                int preview = intent.getIntExtra("weather_effect_preview", -1);
                atmosphere.setPreviewCode(preview);
                Log.i(TAG, "weather visual effect preview: " + preview);
            }
            // Everything time-based (both clocks, weekday prefix, date line, sky, timeline, thought) reads nowMillis().
            renderClocks();
        }
    };

    // ---------------------------------------------------------------- network pill

    // Android 12+ only hands the SSID to callbacks that ask for location info; older versions read it from WifiManager.
    private final ConnectivityManager.NetworkCallback networkCallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? new NetworkWatcher(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
            : new NetworkWatcher();

    private final class NetworkWatcher extends ConnectivityManager.NetworkCallback {
        NetworkWatcher() {
            super();
        }

        @RequiresApi(Build.VERSION_CODES.S)
        NetworkWatcher(int flags) {
            super(flags);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            ssid.setText(networkLabel(caps));
        }

        @Override
        public void onLost(Network network) {
            ssid.setText(R.string.offline);
        }
    }

    /**
     * SSID needs ACCESS_FINE_LOCATION (granted over adb), and on Android 9-10 Location turned on too;
     * otherwise plain "Wi-Fi".
     */
    @SuppressWarnings("deprecation") // WifiManager.getConnectionInfo: the only SSID source before Android 12
    private String networkLabel(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return getString(R.string.wifi);
        WifiInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info = caps.getTransportInfo() instanceof WifiInfo ? (WifiInfo) caps.getTransportInfo() : null;
        } else {
            WifiManager wifi = getApplicationContext().getSystemService(WifiManager.class);
            info = wifi == null ? null : wifi.getConnectionInfo();
        }
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.equals(WifiManager.UNKNOWN_SSID)) return getString(R.string.wifi);
        ssid = ssid.replaceAll("^\"|\"$", "");
        return ssid.isEmpty() ? getString(R.string.wifi) : ssid;
    }

    // ---------------------------------------------------------------- utils

    /** RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED exist from Android 13; older versions take no flag. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // the flagless call only runs below Android 13
    private void register(BroadcastReceiver receiver, IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, exported ? RECEIVER_EXPORTED : RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    /** The window's size: WindowMetrics from Android 11, the display's real size before (home is full screen). */
    @SuppressWarnings("deprecation") // Display.getRealSize: the pre-Android 11 equivalent
    private Rect windowBounds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return getWindowManager().getCurrentWindowMetrics().getBounds();
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        return new Rect(0, 0, size.x, size.y);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int i, int n) {
        return Math.max(0, Math.min(i, n - 1));
    }
}
