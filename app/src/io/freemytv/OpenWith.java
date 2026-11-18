package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [custom] The owner's addition (see ENHANCED.md): which apps a pick can open in. Any app on the TV that opens Nuvio's
 * title links (nuvio://tmdb/movie/603) can be one, such as the Nuvio forks Arc (com.nuvio.tv.test) and One
 * (app.onetube.tv); Home Settings > Picks > Open picks with picks them and their order. With two or more, OK on a
 * pick asks which one (HomeView's menu); with one, it just opens there.
 */
final class OpenWith {
    private OpenWith() {}

    /** The most apps the chooser lists at once (the menu has no scroll). */
    static final int SHOWN = 3;

    private static final Uri PROBE = Uri.parse("nuvio://tmdb/movie/603");

    /** Every app on the TV that opens the links, as [package, label], in the system's order. */
    static List<String[]> candidates(Context c) {
        PackageManager pm = c.getPackageManager();
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_VIEW, PROBE), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
                out.add(new String[] {pkg, String.valueOf(ri.loadLabel(pm))});
            }
        } catch (RuntimeException e) {
            AppLog.w("open with: couldn't list the apps: " + e.getMessage());
        }
        return out;
    }

    /** The chosen apps still on the TV, in the owner's order, as [package, label]; at most SHOWN. */
    static List<String[]> chosen(Context c) {
        List<String[]> all = candidates(c), out = new ArrayList<>();
        for (String pkg : Prefs.openWith()) {
            for (String[] app : all) {
                if (app[0].equals(pkg)) {
                    out.add(app);
                    break;
                }
            }
            if (out.size() == SHOWN) break;
        }
        return out;
    }

    /** The first chosen app on the TV (where a pick opens without asking), or null for none. */
    static String first(Context c) {
        List<String[]> apps = chosen(c);
        return apps.isEmpty() ? null : apps.get(0)[0];
    }
}
