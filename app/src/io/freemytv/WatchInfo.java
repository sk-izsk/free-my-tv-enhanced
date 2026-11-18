package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [custom] The badges of the Continue Watching row (length, rating, year), the same as the picks'. The TV's watch data
 * has none, so each title is looked up on TMDB once (Recs.titleBadges, with your key; nothing without one) and kept
 * on the TV. The home screen only reads the cache while it draws; the lookups run on one background thread.
 */
final class WatchInfo {
    private WatchInfo() {}

    private static final String[] NONE = new String[3];   // looked up this run and not found: not asked again until a restart
    private static final Map<String, String[]> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String SEP = "\u0001";

    private static String key(WatchNext.Item it) {
        return "v2|" + it.title.toLowerCase(Locale.ROOT) + "|" + (it.episode != null) + "|" + Ui.language;   // v2: exact-title match
    }

    /** {rating, length, year} (each may be null), or null when not known (yet). Memory only: safe while drawing. */
    static String[] get(WatchNext.Item it) {
        if (it.title == null) return null;
        String[] v = CACHE.get(key(it));
        return v == NONE ? null : v;
    }

    /** Looks up what the cache lacks, then runs `done` (on the lookup thread) if anything new arrived. */
    static void load(Context c, List<WatchNext.Item> items, Runnable done) {
        final List<WatchNext.Item> todo = new ArrayList<>();
        for (WatchNext.Item it : items) if (it.title != null && !CACHE.containsKey(key(it))) todo.add(it);
        if (todo.isEmpty()) return;
        final SharedPreferences sp = c.getApplicationContext().getSharedPreferences("watchinfo", Context.MODE_PRIVATE);
        IO.execute(() -> {
            boolean fresh = false;
            for (WatchNext.Item it : todo) {
                String k = key(it);
                if (CACHE.containsKey(k)) continue;
                String saved = sp.getString(k, null);
                if (saved != null) {
                    CACHE.put(k, saved.split(SEP, -1));
                    fresh = true;
                    continue;
                }
                try {
                    String[] v = Recs.titleBadges(it.title, it.episode != null);
                    if (v == null) {
                        CACHE.put(k, NONE);
                        continue;
                    }
                    CACHE.put(k, v);
                    sp.edit().putString(k, join(v)).apply();
                    fresh = true;
                } catch (Exception e) {   // offline, a bad key: try again at the next start; never reaches the home screen
                    AppLog.w("watch badges: " + e.getMessage());
                }
            }
            if (fresh && done != null) done.run();
        });
    }

    private static String join(String[] v) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < v.length; i++) s.append(i > 0 ? SEP : "").append(v[i] == null ? "" : v[i]);
        return s.toString();
    }
}
