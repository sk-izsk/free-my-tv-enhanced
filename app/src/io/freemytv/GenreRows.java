package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [custom] The owner's addition (see ENHANCED.md phase 5): up to three genre rows under the shelf (Home Settings →
 * Picks → Genre rows; off until a genre is chosen). Each row is TMDB's most popular titles of that genre on the TV's
 * services (Recs.genreRow, the picks' own filter), movies and shows, refreshed every 3 hours; the home screen shows 7 of
 * the selected tab's kind. Their images are TMDB's small backdrops (300 px wide), kept in a small memory cache and on
 * disk, loaded only when a row is drawn: nothing is fetched or decoded while the rows are off or never scrolled to.
 */
final class GenreRows {
    static final int MAX_ROWS = 3, PER_ROW = 7;
    private static final long EVERY = 3 * 3600_000L;
    private static final String IMAGES = "https://image.tmdb.org/t/p/w300";

    interface Listener { void onGenreRows(); }

    private final Context context;
    private final Recs recs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** "Horror|movie" → its titles. Replaced whole (read on the main thread, written there too). */
    private Map<String, List<Recs.Pick>> rows = new HashMap<>();
    private String loadedFor = "";
    private long loadedAt;
    private boolean loading;
    /** About 4 MB: a 300×169 image is ~200 KB, 21 titles fit with room to spare. */
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(5 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private final Map<Bitmap, BitmapShader> shaders = new HashMap<>();
    private final Set<String> pending = new HashSet<>();

    GenreRows(Context c, Recs recs, Listener listener) {
        context = c.getApplicationContext();
        this.recs = recs;
        this.listener = listener;
    }

    /** The genres chosen, in order (at most MAX_ROWS). */
    static List<String> chosen() {
        List<String> g = Prefs.genreRows();
        return g.size() > MAX_ROWS ? g.subList(0, MAX_ROWS) : g;
    }

    /** Fetches the rows if the genres changed or they're older than 3 hours (in the background; the listener says when). */
    void refreshIfStale() {
        List<String> genres = chosen();
        String key = String.join(",", genres) + "|" + Recs.key().hashCode() + "|" + Prefs.tasteNo().hashCode() + (Prefs.indiaGenreRows() ? "|india" : "");
        long now = System.currentTimeMillis();
        if (genres.isEmpty() || loading || key.equals(loadedFor) && now - loadedAt < EVERY && now >= loadedAt) return;
        loading = true;
        io.execute(() -> {
            Map<String, List<Recs.Pick>> out = new HashMap<>();
            for (String g : genres) {
                for (String kind : new String[] {"movie", "tv"}) {
                    try {
                        out.put(g + "|" + kind, recs.genreRow(g, kind, 2 * PER_ROW));   // spares for the ones the picks show
                    } catch (Exception e) {
                        AppLog.w("genre row " + g + " " + kind + ": " + e.getMessage());
                    }
                }
            }
            boolean any = false;
            for (List<Recs.Pick> l : out.values()) any |= !l.isEmpty();
            final boolean got = any;   // nothing yet (the picks' filter not known): asked again next time, not in 3 hours
            main.post(() -> {
                loading = false;
                if (!got) return;
                rows = out;
                loadedFor = key;
                loadedAt = System.currentTimeMillis();
                // Their images now, in the background (~100 KB each): the first visit to a row then has them ready
                // instead of decoding while it slides in.
                for (List<Recs.Pick> l : out.values()) for (int i = 0; i < l.size() && i < PER_ROW; i++) image(l.get(i));
                listener.onGenreRows();
            });
        });
    }

    /** The row's titles for a tab: "movie", "tv", or null (Explore: movies and shows alternating). */
    List<Recs.Pick> row(String genre, String kind) {
        List<Recs.Pick> movies = rows.get(genre + "|movie"), shows = rows.get(genre + "|tv");
        if (movies == null) movies = Collections.emptyList();
        if (shows == null) shows = Collections.emptyList();
        if ("movie".equals(kind)) return movies;
        if ("tv".equals(kind)) return shows;
        List<Recs.Pick> out = new ArrayList<>();
        for (int i = 0; i < movies.size() || i < shows.size(); i++) {
            if (i < movies.size()) out.add(movies.get(i));
            if (i < shows.size()) out.add(shows.get(i));
        }
        return out;
    }

    /** The title's image if it's ready; else null, and it's fetched (the listener asks for a redraw when it comes). */
    Bitmap image(Recs.Pick p) {
        if (p.backdrop == null || p.backdrop.isEmpty()) return null;
        Bitmap b = images.get(p.backdrop);
        if (b != null) return b;
        if (pending.add(p.backdrop)) {
            final String path = p.backdrop;
            io.execute(() -> {
                Bitmap got = load(path);
                main.post(() -> {
                    pending.remove(path);
                    if (got == null) return;
                    images.put(path, got);
                    listener.onGenreRows();
                });
            });
        }
        return null;
    }

    BitmapShader shader(Bitmap b) {
        BitmapShader s = shaders.get(b);
        if (s == null) {
            if (shaders.size() > 40) shaders.clear();   // evicted bitmaps' shaders go with them
            s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shaders.put(b, s);
        }
        return s;
    }

    /** From the disk cache, else from TMDB (then cached); null on any error. Background thread only. */
    private Bitmap load(String path) {
        File dir = new File(context.getCacheDir(), "genre-rows");
        File f = new File(dir, path.replace("/", ""));
        try {
            if (!f.exists()) {
                if (!dir.exists() && !dir.mkdirs()) return null;
                HttpURLConnection c = (HttpURLConnection) new URL(IMAGES + path).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    if (c.getResponseCode() != 200) return null;
                    byte[] buf = new byte[16 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;   // opaque photos: half the memory
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
    }
}
