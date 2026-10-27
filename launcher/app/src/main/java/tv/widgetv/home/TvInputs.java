package tv.widgetv.home;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Entries for the Inputs dropdown: every HDMI/AV passthrough input, then one "Live TV" entry per
 * hardware tuner (e.g. Antenna, Satellite). Both open in the stock TV app via TvContract URIs.
 */
final class TvInputs {
    private static final String TAG = Config.TAG;
    private static final String TV_APP = "com.android.tv";

    static final class Entry {
        final String label;
        final Intent intent;

        Entry(String label, Intent intent) {
            this.label = label;
            this.intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    static List<Entry> load(Context ctx) {
        TvInputManager tim = ctx.getSystemService(TvInputManager.class);
        List<TvInputInfo> inputs = tim == null ? Collections.emptyList() : tim.getTvInputList();
        Log.i(TAG, "TvInputManager.getTvInputList() returned " + inputs.size() + " inputs");

        List<Entry> passthrough = new ArrayList<>();
        List<Entry> tuners = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            String label = String.valueOf(info.loadLabel(ctx));
            Log.i(TAG, "  " + info.getId() + " type=" + info.getType() + " passthrough="
                    + info.isPassthroughInput() + " hidden=" + info.isHidden(ctx) + " label=" + label);
            if (info.isHidden(ctx)) continue;
            if (info.isPassthroughInput()) {
                passthrough.add(new Entry(label, new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.getId()))));
            } else if (info.getType() == TvInputInfo.TYPE_TUNER && info.getId().contains("/HW")) {
                // Framework-generated ids of hardware-backed inputs end in "/HW<id>"; this skips
                // software "tuners" such as the Google TV preview services.
                tuners.add(new Entry(ctx.getString(R.string.live_tv) + " · " + label,
                        new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(info.getId()))));
            }
        }
        Comparator<Entry> byLabel = (a, b) -> a.label.compareToIgnoreCase(b.label);
        passthrough.sort(byLabel);
        tuners.sort(byLabel);

        List<Entry> out = new ArrayList<>(passthrough);
        out.addAll(tuners);
        Intent tvApp = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(TV_APP);
        if (tuners.isEmpty() && tvApp != null) {
            // Nothing visible to us (e.g. TvInputManager filtered): let the stock TV app pick.
            out.add(new Entry(ctx.getString(passthrough.isEmpty() ? R.string.tv_inputs : R.string.live_tv), tvApp));
        }
        return out;
    }

    private TvInputs() {
    }
}
