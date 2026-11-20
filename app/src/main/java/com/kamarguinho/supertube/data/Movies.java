package com.kamarguinho.supertube.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Só filmes completos: vídeos com mais de 1 hora, sem trailers, cortes ou Shorts. */
public final class Movies {

    /** Texto especial usado nos menus para pedir a lista de filmes. */
    public static final String QUERY = "__filmes_completos__";

    private static final long MIN_SECONDS = 60 * 60;
    private static final int MAX_PAGES_PER_QUERY = 4;
    private static final String[] QUERIES = {
            "filme completo dublado",
            "filme completo dublado em português",
            "filme completo legendado",
            "filme nacional completo",
            "filme completo de ação dublado",
            "filme completo de comédia dublado",
    };

    private Movies() {
    }

    public static List<StreamInfoItem> load(final int wanted) throws Exception {
        final StreamingService yt = ServiceList.YouTube;
        final Map<String, StreamInfoItem> found = new LinkedHashMap<>();
        Exception lastError = null;

        for (final String q : QUERIES) {
            if (found.size() >= wanted) {
                break;
            }
            try {
                final SearchQueryHandler handler = yt.getSearchQHFactory().fromQuery(q,
                        Collections.singletonList(YoutubeSearchQueryHandlerFactory.VIDEOS), "");
                final SearchInfo info = SearchInfo.getInfo(yt, handler);
                addMovies(info.getRelatedItems(), found);
                Page page = info.getNextPage();
                for (int p = 1; p < MAX_PAGES_PER_QUERY && Page.isValid(page)
                        && found.size() < wanted; p++) {
                    final ListExtractor.InfoItemsPage<InfoItem> more =
                            SearchInfo.getMoreItems(yt, handler, page);
                    addMovies(more.getItems(), found);
                    page = more.getNextPage();
                }
            } catch (final Exception e) {
                lastError = e;
            }
        }
        if (found.isEmpty() && lastError != null) {
            throw lastError;
        }
        return new ArrayList<>(found.values());
    }

    private static void addMovies(final List<InfoItem> items,
                                  final Map<String, StreamInfoItem> out) {
        for (final InfoItem i : items) {
            if (!(i instanceof StreamInfoItem)) {
                continue;
            }
            final StreamInfoItem s = (StreamInfoItem) i;
            if (s.getStreamType() != StreamType.VIDEO_STREAM
                    || s.getDuration() < MIN_SECONDS || isShort(s)) {
                continue;
            }
            final String name = s.getName() == null ? "" : s.getName().toLowerCase();
            if (name.contains("trailer") || name.contains("teaser")) {
                continue;
            }
            out.put(s.getUrl(), s);
        }
    }

    /** Vídeos feitos para celular (Shorts / verticais curtos). */
    public static boolean isShort(final StreamInfoItem s) {
        final String url = s.getUrl() == null ? "" : s.getUrl();
        return s.isShortFormContent() || url.contains("/shorts/")
                || (s.getDuration() > 0 && s.getDuration() <= 60);
    }
}
