package com.kamarguinho.supertube.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.kamarguinho.supertube.util.Images;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Histórico de visualização, guardado só neste aparelho.
 * Também lembra onde cada vídeo parou, para continuar de lá e mostrar a barra vermelha.
 */
public final class History {

    public static final class Entry {
        public final int serviceId;
        public final String url;
        public final String title;
        @Nullable
        public final String uploader;
        @Nullable
        public final String uploaderUrl;
        @Nullable
        public final String thumbnail;
        public final long durationSec;
        public long positionMs;
        public long watchedAt;

        Entry(final int serviceId, final String url, final String title,
              @Nullable final String uploader, @Nullable final String uploaderUrl,
              @Nullable final String thumbnail, final long durationSec, final long positionMs,
              final long watchedAt) {
            this.serviceId = serviceId;
            this.url = url;
            this.title = title;
            this.uploader = uploader;
            this.uploaderUrl = uploaderUrl;
            this.thumbnail = thumbnail;
            this.durationSec = durationSec;
            this.positionMs = positionMs;
            this.watchedAt = watchedAt;
        }
    }

    private static final String PREFS = "history";
    private static final String KEY = "entries";
    private static final String KEY_PAUSED = "paused";
    private static final int MAX_ENTRIES = 500;

    /** Cache em memória (o histórico é lido toda hora para desenhar as barras). */
    private static Map<String, Entry> cache;

    private History() {
    }

    private static SharedPreferences prefs(final Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Pausar o histórico (como no YouTube): enquanto pausado, nada é gravado. */
    public static boolean isPaused(final Context c) {
        return prefs(c).getBoolean(KEY_PAUSED, false);
    }

    public static void setPaused(final Context c, final boolean paused) {
        prefs(c).edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    /** Registra que o vídeo começou a ser assistido (vai para o topo do histórico). */
    public static synchronized void record(final Context c, final StreamInfo info) {
        if (isPaused(c) || info.getStreamType() == StreamType.LIVE_STREAM) {
            return;
        }
        final Map<String, Entry> map = load(c);
        final String k = Favorites.key(info.getUrl());
        final Entry old = map.remove(k);
        final Map<String, Entry> out = new LinkedHashMap<>();
        out.put(k, new Entry(info.getServiceId(), info.getUrl(), info.getName(),
                info.getUploaderName(), info.getUploaderUrl(), Images.best(info.getThumbnails()),
                info.getDuration(), old != null ? old.positionMs : 0,
                System.currentTimeMillis()));
        int n = 1;
        for (final Map.Entry<String, Entry> e : map.entrySet()) {
            if (n++ >= MAX_ENTRIES) {
                break;
            }
            out.put(e.getKey(), e.getValue());
        }
        save(c, out);
    }

    /** Guarda onde o vídeo parou. */
    public static synchronized void savePosition(final Context c, final String url,
                                                 final long positionMs) {
        if (isPaused(c) || positionMs < 0) {
            return;
        }
        final Map<String, Entry> map = load(c);
        final Entry e = map.get(Favorites.key(url));
        if (e != null) {
            e.positionMs = positionMs;
            save(c, map);
        }
    }

    /**
     * Posição para continuar o vídeo, ou 0 para começar do início
     * (quando quase não assistiu ou quando já tinha terminado).
     */
    public static synchronized long resumePosition(final Context c, final String url) {
        final Entry e = load(c).get(Favorites.key(url));
        if (e == null || e.positionMs < 10_000) {
            return 0;
        }
        if (e.durationSec > 0 && e.positionMs > e.durationSec * 1000 - 30_000) {
            return 0;
        }
        return e.positionMs;
    }

    /** Quanto do vídeo já foi assistido (0 a 1), para a barra vermelha nos cards. */
    public static synchronized float progress(final Context c, final String url) {
        final Entry e = load(c).get(Favorites.key(url));
        if (e == null || e.durationSec <= 0 || e.positionMs <= 0) {
            return 0f;
        }
        return Math.min(1f, e.positionMs / (e.durationSec * 1000f));
    }

    public static synchronized List<Entry> all(final Context c) {
        return new ArrayList<>(load(c).values());
    }

    public static synchronized void remove(final Context c, final String url) {
        final Map<String, Entry> map = load(c);
        map.remove(Favorites.key(url));
        save(c, map);
    }

    public static synchronized void clear(final Context c) {
        save(c, new LinkedHashMap<>());
    }

    /** O histórico no formato das listas de vídeo do app. */
    public static List<StreamInfoItem> asItems(final Context c) {
        final List<StreamInfoItem> items = new ArrayList<>();
        for (final Entry e : all(c)) {
            final StreamInfoItem item = new StreamInfoItem(e.serviceId, e.url, e.title,
                    StreamType.VIDEO_STREAM);
            item.setUploaderName(e.uploader);
            item.setUploaderUrl(e.uploaderUrl);
            item.setDuration(e.durationSec);
            if (e.thumbnail != null) {
                item.setThumbnails(Collections.singletonList(new Image(e.thumbnail,
                        Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN,
                        Image.ResolutionLevel.UNKNOWN)));
            }
            items.add(item);
        }
        return items;
    }

    private static Map<String, Entry> load(final Context c) {
        if (cache != null) {
            return cache;
        }
        final Map<String, Entry> map = new LinkedHashMap<>();
        try {
            final JSONArray array = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                final JSONObject o = array.getJSONObject(i);
                final Entry e = new Entry(o.optInt("service", 0), o.getString("url"),
                        o.optString("title", ""),
                        o.isNull("uploader") ? null : o.optString("uploader", null),
                        o.isNull("uploaderUrl") ? null : o.optString("uploaderUrl", null),
                        o.isNull("thumb") ? null : o.optString("thumb", null),
                        o.optLong("duration", 0), o.optLong("position", 0),
                        o.optLong("at", 0));
                map.put(Favorites.key(e.url), e);
            }
        } catch (final JSONException ignored) {
            // histórico corrompido: começa vazio
        }
        cache = map;
        return map;
    }

    private static void save(final Context c, final Map<String, Entry> map) {
        cache = map;
        final JSONArray array = new JSONArray();
        try {
            for (final Entry e : map.values()) {
                final JSONObject o = new JSONObject();
                o.put("service", e.serviceId);
                o.put("url", e.url);
                o.put("title", e.title);
                o.put("uploader", e.uploader == null ? JSONObject.NULL : e.uploader);
                o.put("uploaderUrl", e.uploaderUrl == null ? JSONObject.NULL : e.uploaderUrl);
                o.put("thumb", e.thumbnail == null ? JSONObject.NULL : e.thumbnail);
                o.put("duration", e.durationSec);
                o.put("position", e.positionMs);
                o.put("at", e.watchedAt);
                array.put(o);
            }
        } catch (final JSONException ignored) {
            return;
        }
        prefs(c).edit().putString(KEY, array.toString()).apply();
    }
}
