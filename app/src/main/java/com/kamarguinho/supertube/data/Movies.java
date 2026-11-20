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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Só filmes completos: vídeos com mais de 1 hora, sem trailers, cortes ou Shorts. */
public final class Movies {

    /** Texto especial usado nos menus para pedir a lista de filmes. */
    public static final String QUERY = "__filmes_completos__";

    private static final long MIN_SECONDS = 60 * 60;
    private static final int BATCH = 12;
    private static final String[] QUERIES = {
            "filme completo dublado",
            "filme completo dublado em português",
            "filme completo legendado",
            "filme nacional completo",
            "filme completo de ação dublado",
            "filme completo de comédia dublado",
            "filme completo de terror dublado",
            "filme completo de suspense dublado",
            "filme completo de drama dublado",
            "filme completo de aventura dublado",
            "filme completo de ficção científica dublado",
            "filme completo de romance dublado",
            "filme completo antigo dublado",
            "filme evangélico completo dublado",
            "filme completo infantil dublado",
            "full movie english",
    };

    private Movies() {
    }

    /** O filtro de "filme completo". */
    public static boolean isMovie(final StreamInfoItem s) {
        if (s.getStreamType() != StreamType.VIDEO_STREAM
                || s.getDuration() < MIN_SECONDS || isShort(s)) {
            return false;
        }
        final String name = s.getName() == null ? "" : s.getName().toLowerCase();
        return !name.contains("trailer") && !name.contains("teaser");
    }

    public static Pagers.Pager pager() {
        return new MoviePager();
    }

    /** Lista de filmes de uma vez (usado nas fileiras). */
    public static List<StreamInfoItem> load(final int wanted) throws Exception {
        final MoviePager pager = new MoviePager();
        final List<StreamInfoItem> out = new ArrayList<>();
        for (int i = 0; i < 6 && out.size() < wanted && pager.hasMore(); i++) {
            out.addAll(pager.next());
        }
        return out;
    }

    /** Percorre as buscas de filmes página por página; no fim, continua por relacionados. */
    private static final class MoviePager implements Pagers.Pager {
        private final Set<String> seen = new HashSet<>();
        private final List<StreamInfoItem> found = new ArrayList<>();
        private int queryIndex = 0;
        private SearchQueryHandler handler;
        private Page page;
        private boolean queryStarted = false;
        private Pagers.RelatedPager related;

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            final StreamingService yt = ServiceList.YouTube;
            final List<StreamInfoItem> out = new ArrayList<>();
            Exception lastError = null;
            int requests = 0;

            while (out.size() < BATCH && requests < 8 && queryIndex < QUERIES.length) {
                requests++;
                try {
                    if (!queryStarted) {
                        queryStarted = true;
                        handler = yt.getSearchQHFactory().fromQuery(QUERIES[queryIndex],
                                Collections.singletonList(
                                        YoutubeSearchQueryHandlerFactory.VIDEOS), "");
                        final SearchInfo info = SearchInfo.getInfo(yt, handler);
                        page = info.getNextPage();
                        out.addAll(Pagers.streams(info.getRelatedItems(), seen,
                                Movies::isMovie));
                    } else if (Page.isValid(page)) {
                        final ListExtractor.InfoItemsPage<InfoItem> more =
                                SearchInfo.getMoreItems(yt, handler, page);
                        page = more.getNextPage();
                        out.addAll(Pagers.streams(more.getItems(), seen, Movies::isMovie));
                    } else {
                        queryIndex++;
                        queryStarted = false;
                    }
                } catch (final Exception e) {
                    lastError = e;
                    queryIndex++;
                    queryStarted = false;
                }
            }

            if (out.isEmpty() && queryIndex >= QUERIES.length) {
                // Acabaram as buscas: continua com filmes relacionados aos já mostrados
                if (related == null) {
                    related = new Pagers.RelatedPager(new ArrayList<>(found), seen,
                            Movies::isMovie);
                }
                out.addAll(related.next());
            }
            if (out.isEmpty() && lastError != null && found.isEmpty()) {
                throw lastError;
            }
            found.addAll(out);
            return out;
        }

        @Override
        public synchronized boolean hasMore() {
            return queryIndex < QUERIES.length || related == null || related.hasMore();
        }
    }

    /** Vídeos feitos para celular (Shorts / verticais curtos). */
    public static boolean isShort(final StreamInfoItem s) {
        final String url = s.getUrl() == null ? "" : s.getUrl();
        return s.isShortFormContent() || url.contains("/shorts/")
                || (s.getDuration() > 0 && s.getDuration() <= 60);
    }
}
