package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [custom] The owner's addition (see ENHANCED.md phase 6): folders of apps, as on Apple TV. A folder is a name and its
 * apps' packages; in All Apps it takes the place of its apps (HomeApps.others), and it can sit on the shelf too (its key,
 * "folder:<id>", in the shelf's list). Its tile is a 2×2 preview of its first apps on a dark rounded card, made when the
 * lists are made (never while drawing). Saved in Prefs as JSON.
 */
final class Folders {
    private Folders() {}

    static final String PREFIX = "folder:";

    static final class Folder {
        String id, name;
        final List<String> apps = new ArrayList<>();
    }

    static List<Folder> all() {
        List<Folder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.foldersJson());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Folder f = new Folder();
                f.id = o.getString("id");
                f.name = o.optString("name", "Folder");
                JSONArray apps = o.optJSONArray("apps");
                for (int j = 0; apps != null && j < apps.length(); j++) f.apps.add(apps.getString(j));
                out.add(f);
            }
        } catch (Exception e) {
            AppLog.w("folders: " + e.getMessage());
        }
        return out;
    }

    static void save(List<Folder> folders) {
        JSONArray a = new JSONArray();
        try {
            for (Folder f : folders) {
                JSONArray apps = new JSONArray();
                for (String p : f.apps) apps.put(p);
                a.put(new JSONObject().put("id", f.id).put("name", f.name).put("apps", apps));
            }
        } catch (Exception e) {
            AppLog.w("folders: " + e.getMessage());
        }
        Prefs.setFoldersJson(a.toString());
    }

    static Folder byKey(List<Folder> folders, String key) {
        if (key == null || !key.startsWith(PREFIX)) return null;
        for (Folder f : folders) if (key.equals(PREFIX + f.id)) return f;
        return null;
    }

    /** Every app that's in a folder (All Apps shows the folder instead). */
    static Set<String> foldered(List<Folder> folders) {
        Set<String> out = new HashSet<>();
        for (Folder f : folders) out.addAll(f.apps);
        return out;
    }

    /** A new folder holding `pkgs`, named `name`; saved. */
    static Folder create(String name, List<String> pkgs) {
        List<Folder> folders = all();
        Folder f = new Folder();
        f.id = Long.toString(System.currentTimeMillis(), 36);
        f.name = name.trim().isEmpty() ? "Folder" : name.trim();
        for (Folder other : folders) other.apps.removeAll(pkgs);   // an app is in one folder at a time
        f.apps.addAll(pkgs);
        folders.removeIf(other -> other.apps.isEmpty());
        folders.add(f);
        save(folders);
        return f;
    }

    /** Puts `pkgs` in folder `id` (out of any other; a folder left empty goes). */
    static void addAll(String id, List<String> pkgs) {
        List<Folder> folders = all();
        for (Folder f : folders) {
            f.apps.removeAll(pkgs);
            if (f.id.equals(id)) f.apps.addAll(pkgs);
        }
        folders.removeIf(f -> f.apps.isEmpty());
        save(folders);
    }

    /** Takes `pkg` out of its folder; a folder left empty goes. */
    static void remove(String pkg) {
        List<Folder> folders = all();
        for (Folder f : folders) f.apps.remove(pkg);
        folders.removeIf(f -> f.apps.isEmpty());
        save(folders);
    }

    static void rename(String id, String name) {
        List<Folder> folders = all();
        for (Folder f : folders) if (f.id.equals(id) && !name.trim().isEmpty()) f.name = name.trim();
        save(folders);
    }

    /** The folder goes, its apps back to All Apps (and off the shelf with it). */
    static void delete(String id) {
        List<Folder> folders = all();
        folders.removeIf(f -> f.id.equals(id));
        save(folders);
    }

    /** The folder's tile: its first four apps' tiles in a 2×2 grid on a dark rounded card, at the apps' tile size. */
    static Bitmap tile(Context c, Folder f) {
        int w = HomeApps.TILE_W, h = HomeApps.TILE_H;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(0xFF2C2C30);
        canvas.drawRect(0, 0, w, h, p);
        float pad = 18, gap = 12, cw = (w - 2 * pad - gap) / 2f, ch = (h - 2 * pad - gap) / 2f;
        RectF cell = new RectF();
        for (int i = 0; i < Math.min(4, f.apps.size()); i++) {
            Bitmap t = HomeApps.tileFor(c, f.apps.get(i));
            if (t == null) continue;
            float x = pad + (i % 2) * (cw + gap), y = pad + (i / 2) * (ch + gap);
            cell.set(x, y, x + cw, y + ch);
            canvas.save();
            android.graphics.Path round = new android.graphics.Path();
            round.addRoundRect(cell, 10, 10, android.graphics.Path.Direction.CW);
            canvas.clipPath(round);
            canvas.drawBitmap(t, new Rect(0, 0, t.getWidth(), t.getHeight()), cell, p);
            canvas.restore();
        }
        return out;
    }
}
