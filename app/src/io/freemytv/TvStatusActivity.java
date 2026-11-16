package io.freemytv;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.net.Inet4Address;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [custom] The owner's addition (see ENHANCED.md): TV Status, a glass panel on the right over the live home screen,
 * after the "TV Status" screen of Weatherglass TV (github.com/faizaldjoo/weatherglass-tv, MIT; the idea and layout,
 * no code). Memory, storage, CPU (sampled through the TV's own ADB every few seconds while open), how long the TV has
 * been up and its network; and four actions, all through the TV's ADB: Free memory (am kill-all: background processes
 * only), Clear caches (pm trim-caches), Restart home screen, Reboot TV (OK twice); and the Performance mode switch.
 * Opened from the drawer's shortcut, a top button (HomeApps) or a remote button over any app (RemoteKeys). Back closes it. Nothing here may throw into :home (its watchdog counts crashes).
 */
public class TvStatusActivity extends Activity {
    private static final float W = 660, H = 1000, MARGIN = 40;
    private static final String[] ACTIONS = {"Free memory", "Clear caches", "Restart home screen", "Reboot TV", "Performance mode"};
    /** The last action is the Performance mode switch, as wide as the panel. */
    private static final int PERF = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private PanelView view;
    private Click click;
    private volatile boolean open = true;

    // What the panel shows, written on the main thread.
    private String memValue = "…", memNote = "", storeValue = "…", cpuValue = "…", upValue = "…", netValue = "…", message = "";
    private float memFrac, storeFrac, cpuFrac = -1;
    private long[] lastCpu;
    private int focus;
    private boolean rebootArmed, busy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        click = new Click(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float k = dm.widthPixels / 1920f;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(MARGIN * k);
        lp.y = Math.round((1080 - H) / 2 * k);
        lp.width = Math.round(W * k);
        lp.height = Math.round(H * k);
        getWindow().setAttributes(lp);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        overridePendingTransition(0, 0);
        view = new PanelView(this, k);
        setContentView(view);
        refreshLocal();
        sampleCpu();
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    @Override protected void onDestroy() {
        open = false;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        click.release();
        super.onDestroy();
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // MARK: - What's measured

    /** Memory, storage, uptime and network: what the app can read itself (cheap; every 2 s while open). */
    private void refreshLocal() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long used = mi.totalMem - mi.availMem;
            memFrac = mi.totalMem > 0 ? used / (float) mi.totalMem : 0;
            memValue = Math.round(memFrac * 100) + "% · " + gb(used) + " of " + gb(mi.totalMem) + " GB";
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            storeFrac = total > 0 ? (total - free) / (float) total : 0;
            storeValue = gb(total - free) + " of " + gb(total) + " GB used";
            upValue = duration(SystemClock.elapsedRealtime());
            netValue = network();
        } catch (RuntimeException e) {
            AppLog.w("tv status: " + e.getMessage());
        }
        io.execute(() -> {   // the home screen's own memory, off the main thread (it takes a moment)
            try {
                Debug.MemoryInfo d = new Debug.MemoryInfo();
                Debug.getMemoryInfo(d);
                String note = "Home screen uses " + Math.round(d.getTotalPss() / 1024f) + " MB";
                main.post(() -> { memNote = note; view.invalidate(); });
            } catch (Throwable ignored) {
            }
        });
        view.invalidate();
        if (open) main.postDelayed(this::refreshLocal, 2000);
    }

    /** CPU use since the last sample, from /proc/stat through the TV's ADB (apps can't read it); every 3 s while open. */
    private void sampleCpu() {
        io.execute(() -> {
            long[] now = null;
            try (Adb adb = Adb.connect(getApplicationContext(), 10_000, null)) {
                now = parseCpu(adb.shell("head -1 /proc/stat"));
            } catch (Throwable e) {
                AppLog.w("tv status cpu: " + e.getMessage());
            }
            final long[] sample = now;
            main.post(() -> {
                if (sample == null) {
                    if (cpuFrac < 0) cpuValue = "Unavailable";
                } else if (lastCpu != null) {
                    long busyD = sample[0] - lastCpu[0], allD = sample[1] - lastCpu[1];
                    if (allD > 0) {
                        cpuFrac = Math.max(0, Math.min(1, busyD / (float) allD));
                        cpuValue = Math.round(cpuFrac * 100) + "% (last 3 s)";
                    }
                } else {
                    cpuValue = "Measuring…";
                }
                if (sample != null) lastCpu = sample;
                view.invalidate();
                if (open) main.postDelayed(this::sampleCpu, 3000);
            });
        });
    }

    /** {busy, total} jiffies from /proc/stat's first line. */
    private static long[] parseCpu(String line) {
        String[] p = line.trim().split("\\s+");
        if (p.length < 5 || !p[0].equals("cpu")) return null;
        long total = 0, idle = 0;
        for (int i = 1; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            total += v;
            if (i == 4 || i == 5) idle += v;   // idle, iowait
        }
        return new long[] {total - idle, total};
    }

    private String network() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm == null ? null : cm.getActiveNetwork();
        if (n == null) return "Not connected";
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        String kind = caps == null ? "Connected" : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Connected";
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp != null) {
            for (LinkAddress a : lp.getLinkAddresses()) {
                if (a.getAddress() instanceof Inet4Address) return kind + " · " + a.getAddress().getHostAddress();
            }
        }
        return kind;
    }

    private static String gb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / 1073741824.0);
    }

    private static String duration(long ms) {
        long m = ms / 60_000, d = m / 1440, h = m % 1440 / 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m % 60 + " min";
        return m % 60 + " min";
    }

    // MARK: - Actions

    private void act(int i) {
        if (busy) return;
        if (i == PERF) {   // the home screen behind reads Ui.lite as it draws
            boolean on = !Prefs.performance();
            Prefs.setPerformance(on);
            Ui.lite = on;
            rebootArmed = false;
            message = on ? "Performance mode on: flat panels, no shadows." : "Performance mode off: glass is back.";
            click.open();
            view.invalidate();
            return;
        }
        if (i == 3 && !rebootArmed) {
            rebootArmed = true;
            message = "Press OK again to reboot the TV.";
            click.open();
            view.invalidate();
            return;
        }
        rebootArmed = false;
        if (i == 2) {   // the home screen's process ends; the system brings Home back
            click.open();
            finish();
            // Its own handler: onDestroy clears `main`.
            new Handler(Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
            return;
        }
        busy = true;
        message = i == 0 ? "Freeing memory…" : i == 1 ? "Clearing caches…" : "Rebooting…";
        click.open();
        view.invalidate();
        io.execute(() -> {
            String result;
            try (Adb adb = Adb.connect(getApplicationContext(), 15_000, null)) {
                if (i == 0) {
                    long before = availMem();
                    adb.shell("am kill-all");
                    SystemClock.sleep(1500);
                    long freed = Math.max(0, availMem() - before);
                    result = "Freed " + freed / 1048576 + " MB (background apps closed).";
                } else if (i == 1) {
                    long before = freeData();
                    adb.shell("pm trim-caches 1024G");
                    long freed = Math.max(0, freeData() - before);
                    result = "Cleared " + freed / 1048576 + " MB of app caches.";
                } else {
                    adb.shell("reboot");
                    result = "Rebooting…";
                }
            } catch (Throwable e) {
                AppLog.w("tv status action: " + e.getMessage());
                result = "The TV's ADB didn't answer. Try again.";
            }
            final String r = result;
            main.post(() -> {
                busy = false;
                message = r;
                view.invalidate();
            });
        });
    }

    private long availMem() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private static long freeData() {
        return new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                if (!down) return true;
                int c = e.getKeyCode();
                int next = focus + (c == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : c == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                        : c == KeyEvent.KEYCODE_DPAD_UP ? -2 : 2);
                // The wide switch: down from either button of the row above, up back to the left one.
                if (c == KeyEvent.KEYCODE_DPAD_DOWN && focus >= PERF - 2 && focus < PERF) next = PERF;
                if (c == KeyEvent.KEYCODE_DPAD_UP && focus == PERF) next = PERF - 2;
                boolean sameRow = c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN || next / 2 == focus / 2;
                if (focus == PERF && (c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_RIGHT)) sameRow = false;
                if (next >= 0 && next < ACTIONS.length && sameRow) {
                    focus = next;
                    if (rebootArmed) {
                        rebootArmed = false;
                        message = "";
                    }
                    click.move();
                    view.invalidate();
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!down && !e.isCanceled()) act(focus);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    click.move();
                    finish();
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // MARK: - Drawing

    private final class PanelView extends View {
        private static final float PAD = 44, ROW = 90, BAR_H = 8, BTN_H = 84, GAP = 14;
        private final float k;
        private final boolean night;
        private final int ink, soft;
        private final Paint title, sub, label, value, note, btn, msg;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private LinearGradient tint, sheen, cardRim;
        private final String device;

        PanelView(Context c, float k) {
            super(c);
            this.k = k;
            night = HomeData.get(c).isNight(System.currentTimeMillis());
            ink = night ? 0xFFFFFFFF : 0xFF1D1D1F;
            soft = Ui.alpha(ink, 0.62f);
            title = Ui.text(Ui.semibold, 44 * k, ink);
            sub = Ui.text(Ui.medium, 22 * k, soft);
            label = Ui.text(Ui.semibold, 26 * k, ink);
            value = Ui.text(Ui.medium, 24 * k, soft);
            value.setTextAlign(Paint.Align.RIGHT);
            note = Ui.text(Ui.medium, 20 * k, soft);
            btn = Ui.text(Ui.semibold, 24 * k, ink);
            btn.setTextAlign(Paint.Align.CENTER);
            msg = Ui.text(Ui.medium, 22 * k, ink);
            msg.setTextAlign(Paint.Align.CENTER);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(2 * k);
            device = Build.MODEL + " · Android " + Build.VERSION.RELEASE;
            setFocusable(true);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            // Nearly opaque: TVs turn window blur off, so the glass is painted (as QuickPanelActivity's).
            tint = night ? new LinearGradient(0, 0, 0, h, 0xF22C2C30, 0xF71C1C1E, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, h, 0xF7FBFBFD, 0xF2EBEBF0, Shader.TileMode.CLAMP);
            sheen = new LinearGradient(0, 0, 0, h * 0.4f, night ? 0x1FFFFFFF : 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            cardRim = new LinearGradient(0, 0, w, h, new int[] {0xB3FFFFFF, 0x1AFFFFFF, 0x14FFFFFF, 0x66FFFFFF},
                    new float[] {0, 0.35f, 0.65f, 1}, Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = 46 * k;
            r.set(0, 0, w, h);
            fill.setColor(0xFF000000);   // a shader is still scaled by the paint's alpha, which the buttons left low
            fill.setShader(tint);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(sheen);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);
            rim.setShader(cardRim);
            r.inset(k, k);
            c.drawRoundRect(r, rad, rad, rim);
            rim.setShader(null);

            float x = PAD * k, right = w - PAD * k, y = 92 * k;
            c.drawText("TV Status", x, y, title);
            c.drawText(device, x, y + 40 * k, sub);
            y += 84 * k;
            y = stat(c, "Memory", memValue, memFrac, memNote, x, right, y);
            y = stat(c, "Storage", storeValue, storeFrac, null, x, right, y);
            y = stat(c, "CPU", cpuValue, cpuFrac, null, x, right, y);
            y = stat(c, "Up for", upValue, -1, null, x, right, y);
            y = stat(c, "Network", netValue, -1, null, x, right, y);

            float bw = (right - x - GAP * k) / 2;
            y += 4 * k;
            for (int i = 0; i < ACTIONS.length; i++) {
                float bx = x + (i % 2) * (bw + GAP * k), by = y + (i / 2) * (BTN_H + GAP) * k;
                r.set(bx, by, i == PERF ? right : bx + bw, by + BTN_H * k);
                boolean f = i == focus;
                int danger = i == 3 ? 0xFFFF453A : 0;
                fill.setColor(f ? (danger != 0 && rebootArmed ? danger : night ? 0xFFFFFFFF : 0xFF1D1D1F)
                        : night ? 0x1AFFFFFF : 0x12000000);
                c.drawRoundRect(r, 28 * k, 28 * k, fill);
                btn.setColor(f ? (danger != 0 && rebootArmed ? 0xFFFFFFFF : night ? 0xFF1D1D1F : 0xFFFFFFFF)
                        : danger != 0 ? danger : ink);
                String text = i == PERF ? ACTIONS[i] + ": " + (Prefs.performance() ? "On" : "Off") : ACTIONS[i];
                c.drawText(text, r.centerX(), r.centerY() + Ui.halfCap(btn), btn);
            }
            y += 3 * (BTN_H + GAP) * k + 30 * k;
            if (!message.isEmpty()) c.drawText(message, w / 2, y, msg);
        }

        /** One measurement: its name and value, a bar for a fraction (frac < 0: none), and a note under it. */
        private float stat(Canvas c, String name, String val, float frac, String extra, float x, float right, float y) {
            c.drawText(name, x, y, label);
            c.drawText(val, right, y, value);
            float yy = y + 22 * k;
            if (frac >= 0) {
                r.set(x, yy, right, yy + BAR_H * k);
                fill.setColor(night ? 0x26FFFFFF : 0x1A000000);
                c.drawRoundRect(r, BAR_H * k / 2, BAR_H * k / 2, fill);
                r.right = x + (right - x) * Math.max(0.02f, frac);
                fill.setColor(frac > 0.85f ? 0xFFFF453A : frac > 0.65f ? 0xFFFF9F0A : 0xFF30D158);
                c.drawRoundRect(r, BAR_H * k / 2, BAR_H * k / 2, fill);
                yy += BAR_H * k;
            }
            if (extra != null && !extra.isEmpty()) {   // a note under the bar takes a line of its own
                c.drawText(extra, x, yy + 28 * k, note);
                return y + (ROW + 26) * k;
            }
            return y + ROW * k;
        }
    }
}
