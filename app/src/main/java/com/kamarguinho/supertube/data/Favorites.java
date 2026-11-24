package com.kamarguinho.supertube.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Vídeos favoritos guardados só no celular. */
public final class Favorites {

    public static final class Video {
        public final int serviceId;
        public final String url;
        public final String title;
        @Nullable
        public final String uploader;
        @Nullable
        public final String thumbnail;

        public Video(final int serviceId, final String url, final String title,
                     @Nullable final String uploader, @Nullable final String thumbnail) {
            this.serviceId = serviceId;
            this.url = url;
            this.title = title;
            this.uploader = uploader;
            this.thumbnail = thumbnail;
        }
    }

    private static final String PREFS = "favorites";
    private static final String KEY = "videos";
    private static final Pattern YT_ID = Pattern.compile("(?:v=|youtu\\.be/|shorts/)([\\w-]{11})");

    private final SharedPreferences prefs;
    private final Context context;

    public Favorites(final Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Link padrão do YouTube a partir do ID de 11 letras. */
    public static String youtubeUrl(final String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    public static String youtubeThumb(final String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    /** Usa o ID do vídeo como chave, para não duplicar links diferentes do mesmo vídeo. */
    public static String key(final String url) {
        final Matcher m = YT_ID.matcher(url);
        return m.find() ? "yt:" + m.group(1) : url;
    }

    public synchronized List<Video> all() {
        return new ArrayList<>(load().values());
    }

    public synchronized boolean contains(final String url) {
        return load().containsKey(key(url));
    }

    public synchronized void add(final Video video) {
        final Map<String, Video> map = load();
        final Map<String, Video> out = new LinkedHashMap<>();
        // Os mais novos ficam em cima
        out.put(key(video.url), video);
        for (final Map.Entry<String, Video> e : map.entrySet()) {
            if (!e.getKey().equals(key(video.url))) {
                out.put(e.getKey(), e.getValue());
            }
        }
        save(out);
    }

    /** Adiciona vários (importação) sem apagar os que já existem. Retorna quantos são novos. */
    public synchronized int addAll(final List<Video> videos) {
        final Map<String, Video> map = load();
        int added = 0;
        for (final Video v : videos) {
            final String k = key(v.url);
            if (!map.containsKey(k)) {
                map.put(k, v);
                added++;
            }
        }
        save(map);
        return added;
    }

    /** Atualiza título/canal de um vídeo importado só com o ID. */
    public synchronized void update(final Video video) {
        final Map<String, Video> map = load();
        final String k = key(video.url);
        if (map.containsKey(k)) {
            map.put(k, video);
            save(map);
        }
    }

    public synchronized void remove(final String url) {
        final Map<String, Video> map = load();
        map.remove(key(url));
        save(map);
    }

    public List<StreamInfoItem> asItems() {
        final List<StreamInfoItem> items = new ArrayList<>();
        for (final Video v : all()) {
            final StreamInfoItem item = new StreamInfoItem(v.serviceId, v.url,
                    com.kamarguinho.supertube.util.Lang.t(v.title), StreamType.VIDEO_STREAM);
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

    private Map<String, Video> load() {
        final Map<String, Video> map = new LinkedHashMap<>();
        try {
            final JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                final Video v = new Video(o.optInt("service", 0), o.getString("url"),
                        o.optString("title", ""),
                        o.isNull("uploader") ? null : o.optString("uploader", null),
                        o.isNull("thumb") ? null : o.optString("thumb", null));
                map.put(key(v.url), v);
            }
        } catch (final JSONException ignored) {
            // lista corrompida: começa vazia
        }
        return map;
    }

    private void save(final Map<String, Video> map) {
        final JSONArray array = new JSONArray();
        try {
            for (final Video v : map.values()) {
                final JSONObject o = new JSONObject();
                o.put("service", v.serviceId);
                o.put("url", v.url);
                o.put("title", v.title);
                o.put("uploader", v.uploader == null ? JSONObject.NULL : v.uploader);
                o.put("thumb", v.thumbnail == null ? JSONObject.NULL : v.thumbnail);
                array.put(o);
            }
        } catch (final JSONException ignored) {
            return;
        }
        prefs.edit().putString(KEY, array.toString()).apply();
        com.kamarguinho.supertube.sync.Sync.changed(context,
                com.kamarguinho.supertube.sync.Sync.FAVORITES);
    }
}
