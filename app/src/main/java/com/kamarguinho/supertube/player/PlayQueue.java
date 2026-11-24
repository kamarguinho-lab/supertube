package com.kamarguinho.supertube.player;

import androidx.annotation.Nullable;

import com.kamarguinho.supertube.data.Pagers;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Fila de episódios: quando você assiste uma série, o próximo vídeo é o próximo episódio
 * (e não um vídeo relacionado).
 */
public final class PlayQueue {

    private static final List<String> URLS = new ArrayList<>();
    private static final List<StreamInfoItem> ITEMS = new ArrayList<>();
    private static Pagers.Pager more;
    private static String title;

    private PlayQueue() {
    }

    public static synchronized void set(final String queueTitle,
                                        final List<StreamInfoItem> episodes,
                                        @Nullable final Pagers.Pager pager) {
        URLS.clear();
        ITEMS.clear();
        for (final StreamInfoItem e : episodes) {
            URLS.add(e.getUrl());
            ITEMS.add(e);
        }
        more = pager;
        title = queueTitle;
    }

    public static synchronized void clear() {
        URLS.clear();
        ITEMS.clear();
        more = null;
        title = null;
    }

    public static synchronized boolean active() {
        return !URLS.isEmpty();
    }

    @Nullable
    public static synchronized String title() {
        return title;
    }

    /** Vídeos da fila (para mostrar a lista no player). */
    public static synchronized List<StreamInfoItem> items() {
        return new ArrayList<>(ITEMS);
    }

    /** Posição do vídeo na fila (0 = primeiro), ou -1. */
    public static synchronized int position(final String url) {
        return indexOf(url);
    }

    private static int indexOf(final String url) {
        final String key = com.kamarguinho.supertube.data.Favorites.key(url);
        for (int i = 0; i < URLS.size(); i++) {
            if (com.kamarguinho.supertube.data.Favorites.key(URLS.get(i)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Próximo episódio depois de {@code url}. Pode buscar mais episódios na internet,
     * então chame fora da thread principal. Retorna null se acabou ou se não há fila.
     */
    @Nullable
    public static String nextAfter(final String url) throws Exception {
        final Pagers.Pager pager;
        synchronized (PlayQueue.class) {
            final int i = indexOf(url);
            if (i < 0) {
                return null;
            }
            if (i + 1 < URLS.size()) {
                return URLS.get(i + 1);
            }
            pager = more;
        }
        if (pager == null || !pager.hasMore()) {
            return null;
        }
        final List<StreamInfoItem> extra = pager.next();
        synchronized (PlayQueue.class) {
            for (final StreamInfoItem e : extra) {
                URLS.add(e.getUrl());
                ITEMS.add(e);
            }
            final int i = indexOf(url);
            return i >= 0 && i + 1 < URLS.size() ? URLS.get(i + 1) : null;
        }
    }

    /** Este vídeo faz parte da fila atual? */
    public static synchronized boolean contains(final String url) {
        return indexOf(url) >= 0;
    }
}
