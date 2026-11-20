package com.kamarguinho.supertube.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.kamarguinho.supertube.sync.Sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Playlists com nome criadas pela pessoa (ex.: "Músicas", "Ver depois"). */
public final class Playlists {

    public static final class Playlist {
        public final String id;
        public String name;
        public final List<Favorites.Video> videos = new ArrayList<>();

        Playlist(final String id, final String name) {
            this.id = id;
            this.name = name;
        }

        public boolean contains(final String url) {
            final String k = Favorites.key(url);
            for (final Favorites.Video v : videos) {
                if (Favorites.key(v.url).equals(k)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        public String cover() {
            return videos.isEmpty() ? null : videos.get(0).thumbnail;
        }

        public List<StreamInfoItem> asItems() {
            final List<StreamInfoItem> items = new ArrayList<>();
            for (final Favorites.Video v : videos) {
                final StreamInfoItem item = new StreamInfoItem(v.serviceId, v.url, v.title,
                        StreamType.VIDEO_STREAM);
                item.setUploaderName(v.uploader);
                if (v.thumbnail != null) {
                    item.setThumbnails(Collections.singletonList(new Image(v.thumbnail,
                            Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN,
                            Image.ResolutionLevel.UNKNOWN)));
                }
                items.add(item);
            }
            return items;
        }
    }

    public static final String PREFS = "playlists";
    public static final String KEY = "lists";

    private final Context context;
    private final SharedPreferences prefs;

    public Playlists(final Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Playlist> all() {
        final List<Playlist> out = new ArrayList<>();
        try {
            final JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                final Playlist p = new Playlist(o.getString("id"), o.optString("name", ""));
                final JSONArray vids = o.optJSONArray("videos");
                for (int j = 0; vids != null && j < vids.length(); j++) {
                    final JSONObject v = vids.getJSONObject(j);
                    p.videos.add(new Favorites.Video(v.optInt("service", 0), v.getString("url"),
                            v.optString("title", ""),
                            v.isNull("uploader") ? null : v.optString("uploader", null),
                            v.isNull("thumb") ? null : v.optString("thumb", null)));
                }
                out.add(p);
            }
        } catch (final JSONException ignored) {
            // lista corrompida: começa vazia
        }
        return out;
    }

    @Nullable
    public synchronized Playlist get(final String id) {
        for (final Playlist p : all()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public synchronized Playlist create(final String name) {
        final List<Playlist> list = all();
        final Playlist p = new Playlist(UUID.randomUUID().toString(), name.trim());
        list.add(0, p);
        save(list);
        return p;
    }

    public synchronized void rename(final String id, final String name) {
        final List<Playlist> list = all();
        for (final Playlist p : list) {
            if (p.id.equals(id)) {
                p.name = name.trim();
            }
        }
        save(list);
    }

    public synchronized void delete(final String id) {
        final List<Playlist> list = all();
        final List<Playlist> kept = new ArrayList<>();
        for (final Playlist p : list) {
            if (!p.id.equals(id)) {
                kept.add(p);
            }
        }
        save(kept);
    }

    /** Adiciona ou tira o vídeo da playlist. Retorna true se ficou na playlist. */
    public synchronized boolean toggle(final String id, final Favorites.Video video) {
        final List<Playlist> list = all();
        boolean inside = false;
        for (final Playlist p : list) {
            if (!p.id.equals(id)) {
                continue;
            }
            if (p.contains(video.url)) {
                final String k = Favorites.key(video.url);
                p.videos.removeIf(v -> Favorites.key(v.url).equals(k));
            } else {
                p.videos.add(video);
                inside = true;
            }
        }
        save(list);
        return inside;
    }

    public synchronized void remove(final String id, final String url) {
        final List<Playlist> list = all();
        final String k = Favorites.key(url);
        for (final Playlist p : list) {
            if (p.id.equals(id)) {
                p.videos.removeIf(v -> Favorites.key(v.url).equals(k));
            }
        }
        save(list);
    }

    private void save(final List<Playlist> list) {
        final JSONArray array = new JSONArray();
        try {
            for (final Playlist p : list) {
                final JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                final JSONArray vids = new JSONArray();
                for (final Favorites.Video v : p.videos) {
                    final JSONObject j = new JSONObject();
                    j.put("service", v.serviceId);
                    j.put("url", v.url);
                    j.put("title", v.title);
                    j.put("uploader", v.uploader == null ? JSONObject.NULL : v.uploader);
                    j.put("thumb", v.thumbnail == null ? JSONObject.NULL : v.thumbnail);
                    vids.put(j);
                }
                o.put("videos", vids);
                array.put(o);
            }
        } catch (final JSONException ignored) {
            return;
        }
        prefs.edit().putString(KEY, array.toString()).apply();
        Sync.changed(context, Sync.PLAYLISTS);
    }
}
