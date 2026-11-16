package io.freemytv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * [custom] The owner's addition (see ENHANCED.md): the TV's on-screen settings out and in over adb, to copy one TV's
 * setup to another (Prefs can't be read or written from outside: not debuggable, allowBackup=false). Only adb's shell
 * may, as for Secrets. Runs in :home, the process that holds the settings.
 *
 *   export:  adb shell content query --uri content://io.freemytv.backup/settings      (one row: json=…)
 *   import:  adb exec-in content write --uri content://io.freemytv.backup/settings < settings.json
 *            adb shell content call --uri content://io.freemytv.backup --method apply
 *
 * apply replaces every setting with the file's and restarts the home screen (its process ends; Home brings it back).
 * The JSON is {key: {"t": s|i|l|b|f|set, "v": value}}.
 */
public class Backup extends ContentProvider {
    private static final int SHELL_UID = 2000;

    @Override public boolean onCreate() {
        return true;
    }

    private static void shellOnly() {
        int caller = Binder.getCallingUid();
        if (caller != SHELL_UID && caller != 0) throw new SecurityException("only adb");
    }

    private SharedPreferences sp() {
        return getContext().getSharedPreferences("settings", android.content.Context.MODE_MULTI_PROCESS);
    }

    private File incoming() {
        return new File(getContext().getFilesDir(), "settings-import.json");
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        shellOnly();
        JSONObject out = new JSONObject();
        try {
            for (Map.Entry<String, ?> e : sp().getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject one = new JSONObject();
                if (v instanceof String) one.put("t", "s").put("v", v);
                else if (v instanceof Integer) one.put("t", "i").put("v", v);
                else if (v instanceof Long) one.put("t", "l").put("v", v);
                else if (v instanceof Boolean) one.put("t", "b").put("v", v);
                else if (v instanceof Float) one.put("t", "f").put("v", (double) (Float) v);
                else if (v instanceof Set) one.put("t", "set").put("v", new JSONArray((Set<?>) v));
                else continue;
                out.put(e.getKey(), one);
            }
        } catch (Exception ex) {
            AppLog.w("backup export: " + ex.getMessage());
        }
        MatrixCursor c = new MatrixCursor(new String[] {"json"});
        c.addRow(new Object[] {out.toString()});
        return c;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        shellOnly();
        if (!mode.contains("w")) throw new FileNotFoundException("write only");
        return ParcelFileDescriptor.open(incoming(), ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE
                | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        shellOnly();
        Bundle out = new Bundle();
        if (!"apply".equals(method)) {
            out.putString("result", "unknown method");
            return out;
        }
        try {
            JSONObject in = new JSONObject(new String(Files.readAllBytes(incoming().toPath()), StandardCharsets.UTF_8));
            SharedPreferences.Editor ed = sp().edit().clear();
            int n = 0;
            for (Iterator<String> it = in.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject one = in.getJSONObject(key);
                switch (one.getString("t")) {
                    case "s": ed.putString(key, one.getString("v")); break;
                    case "i": ed.putInt(key, one.getInt("v")); break;
                    case "l": ed.putLong(key, one.getLong("v")); break;
                    case "b": ed.putBoolean(key, one.getBoolean("v")); break;
                    case "f": ed.putFloat(key, (float) one.getDouble("v")); break;
                    case "set": {
                        Set<String> set = new HashSet<>();
                        JSONArray a = one.getJSONArray("v");
                        for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                        ed.putStringSet(key, set);
                        break;
                    }
                    default: continue;
                }
                n++;
            }
            ed.commit();
            //noinspection ResultOfMethodCallIgnored
            incoming().delete();
            out.putString("result", "applied " + n + " settings; the home screen restarts");
            // The home screen and its settings start over with them (Home, or the system, brings them back).
            new Handler(Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 500);
        } catch (Exception e) {
            out.putString("result", "failed: " + e.getMessage());
        }
        return out;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
