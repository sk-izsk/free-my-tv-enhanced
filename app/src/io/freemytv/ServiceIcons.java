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
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** The icon at `px` pixels, or null while it loads (`onLoaded` runs when it is in), or when the app has none. */
    static Bitmap get(Context c, String pkg, int px, Runnable onLoaded) {
        if (pkg == null || px <= 0) return null;
        final String key = pkg + "@" + px;
        Bitmap b = CACHE.get(key);
        if (b != null || !ASKED.add(key)) return b;
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            try {
                Drawable d = app.getPackageManager().getApplicationIcon(pkg);
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
                AppLog.w("service icon " + pkg + ": " + e.getMessage());
            }
        });
        return null;
    }
}
