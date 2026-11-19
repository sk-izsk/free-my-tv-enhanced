package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [custom] The streaming app's own icon (Netflix's red N, Prime Video's…), read from the TV once in the background and
 * kept in memory with rounded corners: the picks' cards show it under the runtime badge. Nothing is bundled with the
 * app and nothing comes from the network: the icon is the one the app installed on this TV already has.
 */
final class ServiceIcons {
    private ServiceIcons() {}

    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();   // loading, loaded or failed: asked once
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();   // a logo that couldn't be fetched (offline): asked again after a minute
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();   // TMDB's logos: a slow server never holds the app icons

    /** The icon at `px` pixels, or null while it loads (`onLoaded` runs when it is in), or when the app has none. */
    static Bitmap get(Context c, String pkg, int px, Runnable onLoaded) {
        if (pkg == null || px <= 0) return null;
        final String key = pkg + "@" + px;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        Long failed = FAILED.get(key);
        if (failed != null && System.currentTimeMillis() - failed > 60_000 && FAILED.remove(key, failed)) ASKED.remove(key);   // [custom] retry
        if (!ASKED.add(key)) return null;
        final Context app = c.getApplicationContext();
        (pkg.startsWith("tmdb:") ? NET : IO).execute(() -> {
            try {
                Drawable d = pkg.startsWith("tmdb:") ? logo(app, pkg.substring(5)) : app.getPackageManager().getApplicationIcon(pkg);
                Bitmap square = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, px, px);
                d.draw(new Canvas(square));
                Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);   // the same, with rounded corners
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                new Canvas(out).drawRoundRect(new RectF(0, 0, px, px), px * 0.22f, px * 0.22f, paint);
                square.recycle();
                CACHE.put(key, out);
                if (onLoaded != null) onLoaded.run();
            } catch (Exception e) {   // not installed, or no icon: no logo on the card
                if (pkg.startsWith("tmdb:")) FAILED.put(key, System.currentTimeMillis());   // the network may be back soon
                AppLog.w("service icon " + pkg + ": " + e.getMessage());
            }
        });
        return null;
    }

    /** A provider's logo from TMDB's image server (w92, a few KB), kept on the TV: one request per logo, ever. */
    private static Drawable logo(Context c, String path) throws java.io.IOException {
        java.io.File dir = new java.io.File(c.getCacheDir(), "provider-logos");
        java.io.File f = new java.io.File(dir, path.replaceAll("[^A-Za-z0-9.]", "_"));
        if (!f.exists()) {
            dir.mkdirs();
            java.io.File part = new java.io.File(dir, f.getName() + ".part");   // published only once it is complete and a picture
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL("https://image.tmdb.org/t/p/w92" + path).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            try {
                try (java.io.InputStream in = h.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
                    byte[] buf = new byte[4096];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        if ((total += n) > 200_000) throw new java.io.IOException("logo too large");
                        out.write(buf, 0, n);
                    }
                }
                if (android.graphics.BitmapFactory.decodeFile(part.getPath()) == null) throw new java.io.IOException("not an image");
                if (!part.renameTo(f)) throw new java.io.IOException("couldn't keep the logo");
            } finally {
                h.disconnect();
                part.delete();   // gone after the rename; a failed one never stays
            }
        }
        Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath());
        if (b == null) {
            f.delete();
            throw new java.io.IOException("not an image");
        }
        return new android.graphics.drawable.BitmapDrawable(c.getResources(), b);
    }
}
