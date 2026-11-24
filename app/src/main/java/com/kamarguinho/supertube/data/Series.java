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

    /** Texto especial da Música (playlists com músicas separadas). */
    public static final String MUSIC = "__musica__";

    /** Estilos de música: cada playlist tem as músicas separadas, para pular uma a uma. */
    public static final String[][] MUSIC_GENRES = {
            {"Mais tocadas", "playlist músicas mais tocadas 2025", "top 50 brasil playlist"},
            {"Sertanejo", "playlist sertanejo 2025", "sertanejo universitário playlist"},
            {"Funk", "playlist funk 2025", "funk mais tocados playlist"},
            {"Pagode e samba", "playlist pagode", "playlist samba"},
            {"Forró e piseiro", "playlist forró 2025", "playlist piseiro"},
            {"Gospel", "playlist gospel 2025", "louvores playlist"},
            {"MPB", "playlist mpb", "mpb clássicos playlist"},
            {"Pop", "pop hits playlist 2025", "playlist pop internacional"},
            {"Rock", "rock classics playlist", "playlist rock nacional"},
            {"Eletrônica", "playlist música eletrônica", "edm hits playlist"},
            {"Rap e hip-hop", "playlist rap nacional", "hip hop hits playlist"},
            {"Românticas", "playlist músicas românticas", "love songs playlist"},
            {"Anos 80 e 90", "playlist anos 80", "playlist anos 90"},
            {"Reggae", "playlist reggae"},
            {"Infantil", "playlist músicas infantis"},
            {"Para relaxar", "playlist música para relaxar", "lofi playlist"},
    };

    /** Estilos para o app em inglês: músicas de várias culturas do mundo. */
    public static final String[][] MUSIC_GENRES_EN = {
            {"Top hits", "top 50 global playlist", "most streamed songs playlist"},
            {"Pop", "pop hits playlist", "best pop songs playlist"},
            {"Hip-hop & rap", "hip hop hits playlist", "rap playlist"},
            {"R&B & soul", "r&b hits playlist", "soul classics playlist"},
            {"Rock", "rock classics playlist", "best rock songs playlist"},
            {"Country", "country hits playlist", "best country songs playlist"},
            {"Latin & reggaeton", "reggaeton hits playlist", "latin pop playlist"},
            {"K-pop", "k-pop hits playlist", "best kpop songs playlist"},
            {"Afrobeats", "afrobeats hits playlist", "amapiano playlist"},
            {"Electronic & dance", "edm hits playlist", "dance music playlist"},
            {"Bollywood", "bollywood hits playlist", "best hindi songs playlist"},
            {"J-pop & anime", "j-pop hits playlist", "anime songs playlist"},
            {"Arabic", "arabic hits playlist", "best arabic songs playlist"},
            {"Irish & Celtic", "irish music playlist", "celtic music playlist"},
            {"Jazz", "jazz classics playlist", "smooth jazz playlist"},
            {"Blues", "blues classics playlist"},
            {"Classical", "classical music playlist", "best of mozart beethoven playlist"},
            {"Reggae", "reggae classics playlist", "bob marley playlist"},
            {"Metal", "metal classics playlist", "heavy metal playlist"},
            {"Indie & alternative", "indie hits playlist", "alternative rock playlist"},
            {"80s & 90s", "80s hits playlist", "90s hits playlist"},
            {"Love songs", "love songs playlist", "romantic songs playlist"},
            {"Gospel & worship", "worship songs playlist", "gospel music playlist"},
            {"Kids", "kids songs playlist"},
            {"Chill & lofi", "lofi playlist", "chill music playlist"},
    };

    /** Estilos de música do idioma atual (português: Brasil; inglês: mundo). */
    public static String[][] musicGenres() {
        return com.kamarguinho.supertube.util.Lang.english() ? MUSIC_GENRES_EN : MUSIC_GENRES;
    }

    /** Estilos que continuam com busca em português mesmo com o app em inglês. */
    private static final java.util.Set<String> BRAZILIAN = new java.util.HashSet<>(
            java.util.Arrays.asList("Sertanejo", "Funk", "Pagode e samba", "Forró e piseiro",
                    "MPB", "Novelas", "Séries nacionais", "Séries dubladas"));

    private Series() {
    }

    public static String genreQuery(final String genre) {
        return QUERY + ":" + genre;
    }

    public static String musicQuery(final String genre) {
        return MUSIC + ":" + genre;
    }

    /** Entrega séries (playlists) aos poucos, sem fim enquanto houver buscas. */
    public interface Pager {
        List<PlaylistInfoItem> next() throws Exception;

        boolean hasMore();
    }

    public static Pager pagerFor(final String query) {
        final boolean music = query.startsWith(MUSIC);
        final String prefix = music ? MUSIC : QUERY;
        final String[][] genres = music ? musicGenres() : GENRES;
        final boolean english = com.kamarguinho.supertube.util.Lang.english();
        if (query.startsWith(prefix + ":")) {
            final String name = query.substring(prefix.length() + 1);
            for (final String[] g : genres) {
                // Em inglês: buscas em inglês (menos nos estilos brasileiros)
                if (english && !music && g[0].equals(name) && !BRAZILIAN.contains(name)) {
                    final String en = com.kamarguinho.supertube.util.Lang.t(name).toLowerCase();
                    return new SearchPager(music
                            ? new String[]{en + " playlist", "best " + en + " songs playlist"}
                            : new String[]{en + " full episodes", en + " series full season"});
                }
                if (g[0].equals(name)) {
                    final String[] q = new String[g.length - 1];
                    System.arraycopy(g, 1, q, 0, q.length);
                    return new SearchPager(q);
                }
            }
        }
        // "Todas": junta a primeira busca de cada gênero
        final String[] all = new String[genres.length];
        for (int i = 0; i < genres.length; i++) {
            all[i] = genres[i][1];
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
