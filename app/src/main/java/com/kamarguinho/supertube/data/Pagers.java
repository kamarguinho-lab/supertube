package com.kamarguinho.supertube.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Listas sem fim: cada "paginador" entrega a próxima leva de vídeos a cada chamada.
 * Quando a fonte original acaba (fim da busca, fim das tendências), continua com
 * vídeos relacionados, como o YouTube faz.
 */
public final class Pagers {

    /** Entrega vídeos aos poucos. */
    public interface Pager {
        /** Próxima leva (pode vir vazia). Roda fora da thread principal. */
        List<StreamInfoItem> next() throws Exception;

        boolean hasMore();
    }

    /** Filtro opcional de vídeos. */
    public interface Filter {
        boolean accept(StreamInfoItem item);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private Pagers() {
    }

    /** Paginador para um texto de busca ou para os menus especiais (Em alta, Filmes). */
    public static Pager forQuery(final String query) {
        if (Trending.QUERY.equals(query)) {
            return new TrendingPager();
        }
        if (query.startsWith(Movies.QUERY)) {
            return Movies.pagerFor(query);
        }
        return new SearchPager(query);
    }

    static List<StreamInfoItem> streams(final List<? extends InfoItem> items,
                                        final Set<String> seen, final Filter filter) {
        final List<StreamInfoItem> out = new ArrayList<>();
        for (final InfoItem i : items) {
            if (i instanceof StreamInfoItem && seen.add(i.getUrl())
                    && (filter == null || filter.accept((StreamInfoItem) i))) {
                out.add((StreamInfoItem) i);
            }
        }
        return out;
    }

    // ================= Relacionados (nunca acaba) =================

    /** Pega um vídeo, busca os relacionados dele, e esses viram novas sementes. */
    public static class RelatedPager implements Pager {
        private final Deque<String> seeds = new ArrayDeque<>();
        private final Set<String> seen;
        private final Set<String> usedSeeds = new HashSet<>();
        private final Filter filter;

        public RelatedPager(final List<StreamInfoItem> start, final Set<String> seen,
                            final Filter filter) {
            this.seen = seen;
            this.filter = filter;
            for (final StreamInfoItem s : start) {
                seeds.add(s.getUrl());
                seen.add(s.getUrl());
            }
        }

        public RelatedPager(final List<StreamInfoItem> start) {
            this(start, new HashSet<>(), null);
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            Exception last = null;
            for (int tries = 0; tries < 4 && !seeds.isEmpty(); tries++) {
                final String seed = seeds.pollFirst();
                if (!usedSeeds.add(seed)) {
                    continue;
                }
                try {
                    final StreamInfo info = StreamInfo.getInfo(NewPipe.getServiceByUrl(seed),
                            seed);
                    final List<StreamInfoItem> items = streams(info.getRelatedItems(), seen,
                            filter);
                    for (final StreamInfoItem i : items) {
                        seeds.addLast(i.getUrl());
                    }
                    if (!items.isEmpty()) {
                        return items;
                    }
                } catch (final Exception e) {
                    last = e;
                }
            }
            if (last != null && seeds.isEmpty()) {
                throw last;
            }
            return Collections.emptyList();
        }

        @Override
        public synchronized boolean hasMore() {
            return !seeds.isEmpty();
        }
    }

    // ================= Busca =================

    public static class SearchPager implements Pager {
        private final String query;
        private final Set<String> seen = new HashSet<>();
        private final List<StreamInfoItem> all = new ArrayList<>();
        private SearchQueryHandler handler;
        private Page page;
        private boolean started = false;
        private RelatedPager fallback;

        public SearchPager(final String query) {
            this.query = query;
        }

        public String query() {
            return query;
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            final StreamingService yt = ServiceList.YouTube;
            if (!started) {
                started = true;
                handler = yt.getSearchQHFactory().fromQuery(query);
                final SearchInfo info = SearchInfo.getInfo(yt, handler);
                page = info.getNextPage();
                return keep(streams(info.getRelatedItems(), seen, null));
            }
            for (int tries = 0; tries < 3 && Page.isValid(page); tries++) {
                final ListExtractor.InfoItemsPage<InfoItem> more =
                        SearchInfo.getMoreItems(yt, handler, page);
                page = more.getNextPage();
                final List<StreamInfoItem> items = streams(more.getItems(), seen, null);
                if (!items.isEmpty()) {
                    return keep(items);
                }
            }
            // A busca acabou: continua com vídeos relacionados aos resultados
            if (fallback == null) {
                fallback = new RelatedPager(new ArrayList<>(all), seen, null);
            }
            return fallback.next();
        }

        private List<StreamInfoItem> keep(final List<StreamInfoItem> items) {
            all.addAll(items);
            return items;
        }

        @Override
        public synchronized boolean hasMore() {
            return !started || Page.isValid(page) || fallback == null || fallback.hasMore();
        }
    }

    // ================= Em alta =================

    public static class TrendingPager implements Pager {
        private final Set<String> seen = new HashSet<>();
        private RelatedPager more;

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            if (more == null) {
                final List<StreamInfoItem> first = Trending.load();
                for (final StreamInfoItem i : first) {
                    seen.add(i.getUrl());
                }
                more = new RelatedPager(first, seen, item -> !Movies.isShort(item));
                return first;
            }
            return more.next();
        }

        @Override
        public synchronized boolean hasMore() {
            return more == null || more.hasMore();
        }
    }

    // ================= Canal =================

    /** Vídeos de um canal, página por página (acaba quando o canal acaba). */
    public static class ChannelPager implements Pager {
        private final ChannelInfo channel;
        private final Set<String> seen = new HashSet<>();
        private ListLinkHandler tab;
        private Page page;
        private boolean started = false;

        public ChannelPager(final ChannelInfo channel) {
            this.channel = channel;
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            final StreamingService service = NewPipe.getService(channel.getServiceId());
            if (!started) {
                started = true;
                tab = videosTab(channel);
                if (tab == null) {
                    return Collections.emptyList();
                }
                final ChannelTabInfo info = ChannelTabInfo.getInfo(service, tab);
                page = info.getNextPage();
                return streams(info.getRelatedItems(), seen, null);
            }
            if (tab == null || !Page.isValid(page)) {
                return Collections.emptyList();
            }
            final ListExtractor.InfoItemsPage<InfoItem> more =
                    ChannelTabInfo.getMoreItems(service, tab, page);
            page = more.getNextPage();
            return streams(more.getItems(), seen, null);
        }

        @Override
        public synchronized boolean hasMore() {
            return !started || (tab != null && Page.isValid(page));
        }
    }

    static ListLinkHandler videosTab(final ChannelInfo channel) {
        for (final ListLinkHandler t : channel.getTabs()) {
            if (t.getContentFilters().contains(ChannelTabs.VIDEOS)) {
                return t;
            }
        }
        return channel.getTabs().isEmpty() ? null : channel.getTabs().get(0);
    }

    /** Pega um canal pela URL e pagina os vídeos dele. */
    public static class ChannelUrlPager implements Pager {
        private final int serviceId;
        private final String url;
        private ChannelPager inner;

        public ChannelUrlPager(final int serviceId, final String url) {
            this.serviceId = serviceId;
            this.url = url;
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            if (inner == null) {
                inner = new ChannelPager(ChannelInfo.getInfo(NewPipe.getService(serviceId), url));
            }
            return inner.next();
        }

        @Override
        public synchronized boolean hasMore() {
            return inner == null || inner.hasMore();
        }
    }

    // ================= Inscrições =================

    /** Vídeos de todos os canais inscritos, página por página, mais novos primeiro. */
    public static class FeedPager implements Pager {
        private final List<Subscriptions.Channel> channels;
        private final List<ListLinkHandler> tabs = new ArrayList<>();
        private final List<Page> pages = new ArrayList<>();
        private final List<Integer> serviceIds = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private boolean started = false;

        public FeedPager(final List<Subscriptions.Channel> channels) {
            this.channels = channels;
        }

        @Override
        public synchronized List<StreamInfoItem> next() throws Exception {
            final List<Future<List<InfoItem>>> futures = new ArrayList<>();
            if (!started) {
                started = true;
                final List<Future<Object[]>> first = new ArrayList<>();
                for (final Subscriptions.Channel c : channels) {
                    first.add(POOL.submit(() -> {
                        final StreamingService service = NewPipe.getService(c.serviceId);
                        final ChannelInfo info = ChannelInfo.getInfo(service, c.url);
                        final ListLinkHandler tab = videosTab(info);
                        if (tab == null) {
                            return null;
                        }
                        final ChannelTabInfo tabInfo = ChannelTabInfo.getInfo(service, tab);
                        return new Object[]{c.serviceId, tab, tabInfo.getNextPage(),
                                tabInfo.getRelatedItems()};
                    }));
                }
                final List<StreamInfoItem> out = new ArrayList<>();
                for (final Future<Object[]> f : first) {
                    try {
                        final Object[] r = f.get();
                        if (r == null) {
                            continue;
                        }
                        serviceIds.add((Integer) r[0]);
                        tabs.add((ListLinkHandler) r[1]);
                        pages.add((Page) r[2]);
                        @SuppressWarnings("unchecked")
                        final List<InfoItem> items = (List<InfoItem>) r[3];
                        out.addAll(streams(items, seen, null));
                    } catch (final Exception ignored) {
                        // um canal com erro não derruba o feed
                    }
                }
                return sorted(out);
            }

            final List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < tabs.size(); i++) {
                final Page p = pages.get(i);
                if (!Page.isValid(p)) {
                    continue;
                }
                final int n = i;
                idx.add(n);
                futures.add(POOL.submit(() -> {
                    final ListExtractor.InfoItemsPage<InfoItem> more = ChannelTabInfo
                            .getMoreItems(NewPipe.getService(serviceIds.get(n)),
                                    tabs.get(n), p);
                    synchronized (pages) {
                        pages.set(n, more.getNextPage());
                    }
                    return more.getItems();
                }));
            }
            final List<StreamInfoItem> out = new ArrayList<>();
            for (int k = 0; k < futures.size(); k++) {
                try {
                    out.addAll(streams(futures.get(k).get(), seen, null));
                } catch (final Exception e) {
                    pages.set(idx.get(k), null);
                }
            }
            return sorted(out);
        }

        private static List<StreamInfoItem> sorted(final List<StreamInfoItem> list) {
            Collections.sort(list, (a, b) -> {
                final long ta = a.getUploadDate() == null ? 0
                        : a.getUploadDate().getInstant().toEpochMilli();
                final long tb = b.getUploadDate() == null ? 0
                        : b.getUploadDate().getInstant().toEpochMilli();
                return Long.compare(tb, ta);
            });
            return list;
        }

        @Override
        public synchronized boolean hasMore() {
            if (!started) {
                return true;
            }
            for (final Page p : pages) {
                if (Page.isValid(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Entrega primeiro uma lista pronta e depois continua com os relacionados dela. */
    public static Pager startingWith(final List<StreamInfoItem> first) {
        return new Pager() {
            private boolean gaveFirst = false;
            private final Set<String> seen = new HashSet<>();
            private RelatedPager more;

            @Override
            public synchronized List<StreamInfoItem> next() throws Exception {
                if (!gaveFirst) {
                    gaveFirst = true;
                    final List<StreamInfoItem> out = streams(first, seen, null);
                    more = new RelatedPager(out, seen, null);
                    return out;
                }
                return more.next();
            }

            @Override
            public synchronized boolean hasMore() {
                return !gaveFirst || more.hasMore();
            }
        };
    }

    // ================= Lista fixa =================

    /** Uma lista já pronta (histórico, favoritos): entrega tudo de uma vez. */
    public static class FixedPager implements Pager {
        private List<StreamInfoItem> items;

        public FixedPager(final List<StreamInfoItem> items) {
            this.items = items;
        }

        @Override
        public synchronized List<StreamInfoItem> next() {
            final List<StreamInfoItem> out = items == null ? Collections.emptyList() : items;
            items = null;
            return out;
        }

        @Override
        public synchronized boolean hasMore() {
            return items != null;
        }
    }
}
