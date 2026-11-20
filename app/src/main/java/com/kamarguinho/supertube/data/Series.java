package com.kamarguinho.supertube.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Séries: playlists públicas do YouTube com episódios completos, separadas por gênero.
 * Cada série abre uma página com os episódios em ordem.
 */
public final class Series {

    /** Texto especial dos menus (ex.: "__series__" ou "__series__:Anime"). */
    public static final String QUERY = "__series__";

    private static final int MIN_EPISODES = 3;
    private static final int BATCH = 10;

    /** Gênero: nome mostrado e as buscas de playlists. */
    public static final String[][] GENRES = {
            {"Séries dubladas", "série completa dublada", "série dublada todos os episódios",
                    "temporada completa dublada"},
            {"Ação e aventura", "série de ação dublada episódios completos",
                    "série de aventura dublada completa"},
            {"Comédia", "série de comédia dublada episódios", "sitcom dublado episódios completos"},
            {"Suspense e mistério", "série de suspense dublada completa",
                    "série de mistério dublada episódios"},
            {"Terror", "série de terror dublada episódios completos"},
            {"Drama", "série de drama dublada completa"},
            {"Animes", "anime dublado episódios completos", "anime dublado todos os episódios"},
            {"Desenhos", "desenho animado dublado episódios completos",
                    "desenho infantil episódios completos"},
            {"Novelas", "novela completa capítulos", "novela mexicana dublada capítulos completos"},
            {"Doramas", "dorama dublado episódios completos", "dorama legendado completo"},
            {"Séries nacionais", "série brasileira completa episódios", "série nacional completa"},
            {"Fé e família", "série cristã dublada episódios", "série bíblica dublada completa"},
            {"Documentários", "série documental completa", "série documentário dublada episódios"},
            {"Em inglês", "full tv series episodes", "full season episodes"},
    };

    private Series() {
    }

    public static String genreQuery(final String genre) {
        return QUERY + ":" + genre;
    }

    /** Entrega séries (playlists) aos poucos, sem fim enquanto houver buscas. */
    public interface Pager {
        List<PlaylistInfoItem> next() throws Exception;

        boolean hasMore();
    }

    public static Pager pagerFor(final String query) {
        if (query.startsWith(QUERY + ":")) {
            final String name = query.substring(QUERY.length() + 1);
            for (final String[] g : GENRES) {
                if (g[0].equals(name)) {
                    final String[] q = new String[g.length - 1];
                    System.arraycopy(g, 1, q, 0, q.length);
                    return new SearchPager(q);
                }
            }
        }
        // "Todas": junta a primeira busca de cada gênero
        final String[] all = new String[GENRES.length];
        for (int i = 0; i < GENRES.length; i++) {
            all[i] = GENRES[i][1];
        }
        return new SearchPager(all);
    }

    private static final class SearchPager implements Pager {
        private final String[] queries;
        private final Set<String> seen = new HashSet<>();
        private int index = 0;
        private boolean started = false;
        private SearchQueryHandler handler;
        private Page page;

        SearchPager(final String[] queries) {
            this.queries = queries;
        }

        @Override
        public synchronized List<PlaylistInfoItem> next() throws Exception {
            final StreamingService yt = ServiceList.YouTube;
            final List<PlaylistInfoItem> out = new ArrayList<>();
            Exception last = null;
            int requests = 0;
            while (out.size() < BATCH && requests < 6 && index < queries.length) {
                requests++;
                try {
                    if (!started) {
                        started = true;
                        handler = yt.getSearchQHFactory().fromQuery(queries[index],
                                Collections.singletonList(
                                        YoutubeSearchQueryHandlerFactory.PLAYLISTS), "");
                        final SearchInfo info = SearchInfo.getInfo(yt, handler);
                        page = info.getNextPage();
                        add(info.getRelatedItems(), out);
                    } else if (Page.isValid(page)) {
                        final ListExtractor.InfoItemsPage<InfoItem> more =
                                SearchInfo.getMoreItems(yt, handler, page);
                        page = more.getNextPage();
                        add(more.getItems(), out);
                    } else {
                        index++;
                        started = false;
                    }
                } catch (final Exception e) {
                    last = e;
                    index++;
                    started = false;
                }
            }
            if (out.isEmpty() && last != null && seen.isEmpty()) {
                throw last;
            }
            return out;
        }

        private void add(final List<InfoItem> items, final List<PlaylistInfoItem> out) {
            for (final InfoItem i : items) {
                if (i instanceof PlaylistInfoItem && seen.add(i.getUrl())) {
                    final PlaylistInfoItem p = (PlaylistInfoItem) i;
                    if (p.getStreamCount() < 0 || p.getStreamCount() >= MIN_EPISODES) {
                        out.add(p);
                    }
                }
            }
        }

        @Override
        public synchronized boolean hasMore() {
            return index < queries.length;
        }
    }

    /** Episódios de uma série, em ordem, página por página. */
    public static final class EpisodesPager implements Pagers.Pager {
        private final String url;
        private PlaylistInfo info;
        private Page page;

        public EpisodesPager(final String url) {
            this.url = url;
        }

        /** Informações da série (depois do primeiro next()). */
        public PlaylistInfo info() {
            return info;
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            final StreamingService service = NewPipe.getServiceByUrl(url);
            if (info == null) {
                info = PlaylistInfo.getInfo(service, url);
                page = info.getNextPage();
                return new ArrayList<>(info.getRelatedItems());
            }
            if (!Page.isValid(page)) {
                return Collections.emptyList();
            }
            final ListExtractor.InfoItemsPage<StreamInfoItem> more =
                    PlaylistInfo.getMoreItems(service, url, page);
            page = more.getNextPage();
            return new ArrayList<>(more.getItems());
        }

        @Override
        public synchronized boolean hasMore() {
            return info == null || Page.isValid(page);
        }
    }
}
