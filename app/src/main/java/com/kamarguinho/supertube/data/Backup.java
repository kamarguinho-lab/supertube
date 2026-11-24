package com.kamarguinho.supertube.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cópia de segurança de tudo que fica salvo no aparelho: playlists, favoritos,
 * inscrições, histórico e escolhas de qualidade, legenda e áudio.
 */
public final class Backup {

    private static final String[] PREFS = {"playlists", "favorites", "subscriptions", "history",
            "quality", "subtitles", "audio"};

    private Backup() {
    }

    public static String fileName() {
        return "SuperTube-backup.json";
    }

    public static String toJson(final Context c) throws Exception {
        final JSONObject all = new JSONObject();
        for (final String name : PREFS) {
            final JSONObject p = new JSONObject();
            for (final Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .getAll().entrySet()) {
                final Object v = e.getValue();
                final JSONObject item = new JSONObject();
                if (v instanceof String) {
                    item.put("t", "s").put("v", v);
                } else if (v instanceof Set) {
                    final JSONArray a = new JSONArray();
                    for (final Object o : (Set<?>) v) {
                        a.put(String.valueOf(o));
                    }
                    item.put("t", "set").put("v", a);
                } else if (v instanceof Long) {
                    item.put("t", "l").put("v", v);
                } else if (v instanceof Integer) {
                    item.put("t", "i").put("v", v);
                } else if (v instanceof Boolean) {
                    item.put("t", "b").put("v", v);
                } else if (v instanceof Float) {
                    item.put("t", "f").put("v", ((Float) v).doubleValue());
                } else {
                    continue;
                }
                p.put(e.getKey(), item);
            }
            all.put(name, p);
        }
        return new JSONObject().put("app", "SuperTube").put("version", 1).put("prefs", all)
                .toString();
    }

    /** Troca os dados do aparelho pelos do backup. Devolve quantos itens voltaram. */
    public static int restore(final Context c, final String json) throws Exception {
        final JSONObject root = new JSONObject(json.trim());
        if (!"SuperTube".equals(root.optString("app"))) {
            throw new IllegalArgumentException("Este arquivo não é um backup do SuperTube.");
        }
        final JSONObject all = root.getJSONObject("prefs");
        int count = 0;
        for (final String name : PREFS) {
            final JSONObject p = all.optJSONObject(name);
            if (p == null) {
                continue;
            }
            final SharedPreferences.Editor ed = c.getSharedPreferences(name,
                    Context.MODE_PRIVATE).edit().clear();
            final java.util.Iterator<String> keys = p.keys();
            while (keys.hasNext()) {
                final String k = keys.next();
                final JSONObject item = p.getJSONObject(k);
                switch (item.getString("t")) {
                    case "s":
                        ed.putString(k, item.getString("v"));
                        break;
                    case "set":
                        final JSONArray a = item.getJSONArray("v");
                        final Set<String> s = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) {
                            s.add(a.getString(i));
                        }
                        ed.putStringSet(k, s);
                        break;
                    case "l":
                        ed.putLong(k, item.getLong("v"));
                        break;
                    case "i":
                        ed.putInt(k, item.getInt("v"));
                        break;
                    case "b":
                        ed.putBoolean(k, item.getBoolean("v"));
                        break;
                    case "f":
                        ed.putFloat(k, (float) item.getDouble("v"));
                        break;
                    default:
                        continue;
                }
                count++;
            }
            ed.commit();
        }
        History.invalidate();
        return count;
    }
}
