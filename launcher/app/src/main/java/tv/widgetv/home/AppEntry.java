package tv.widgetv.home;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;

/** One launchable app: label and pre-rendered tile art. */
final class AppEntry {
    final ComponentName component;
    final String label;
    final boolean leanback;
    final Bitmap art;

    AppEntry(ComponentName component, String label, boolean leanback, Bitmap art) {
        this.component = component;
        this.label = label;
        this.leanback = leanback;
        this.art = art;
    }

    String pkg() {
        return component.getPackageName();
    }

    Intent launchIntent() {
        String category = leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER;
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
