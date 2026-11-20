package com.kamarguinho.supertube.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;

import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Favorites;
import com.kamarguinho.supertube.data.Feed;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Bg;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/** Tela inicial da TV: menu lateral e fileiras de vídeos, no estilo das TVs. */
public class TvBrowseFragment extends BrowseSupportFragment {

    /** Fileiras de categorias (nome, texto pesquisado). */
    static final String[][] CATEGORIES = {
            {"Em alta", "em alta hoje"},
            {"Música", "música"},
            {"Jogos", "games gameplay"},
            {"Notícias", "notícias hoje"},
            {"Ao vivo", "ao vivo agora"},
            {"Podcasts", "podcast"},
            {"Filmes e trailers", "trailer filme"},
            {"Esportes", "esportes melhores momentos"},
            {"Humor", "humor comédia"},
            {"Infantil", "desenho infantil"},
            {"Tecnologia", "tecnologia review"},
            {"Culinária", "receita fácil"},
    };

    private ArrayObjectAdapter rows;
    private ArrayObjectAdapter subscriptionsRow;
    private ArrayObjectAdapter favoritesRow;
    private boolean loaded = false;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.app_name));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setBrandColor(ContextCompat.getColor(requireContext(), R.color.background));
        setSearchAffordanceColor(ContextCompat.getColor(requireContext(), R.color.brand));
        setOnSearchClickedListener(v ->
                startActivity(new Intent(requireContext(), TvSearchActivity.class)));

        setOnItemViewClickedListener((OnItemViewClickedListener)
                (itemViewHolder, item, rowViewHolder, row) -> {
                    if (item instanceof StreamInfoItem) {
                        TvPlayerActivity.open(requireContext(), ((StreamInfoItem) item).getUrl());
                    }
                });

        rows = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(rows);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!loaded) {
            loaded = true;
            buildRows();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFavorites();
    }

    private void buildRows() {
        final CardPresenter cards = new CardPresenter();
        long id = 0;

        final List<Subscriptions.Channel> channels =
                new Subscriptions(requireContext()).all();
        if (!channels.isEmpty()) {
            subscriptionsRow = new ArrayObjectAdapter(cards);
            rows.add(new ListRow(new HeaderItem(id++, getString(R.string.tab_subscriptions)),
                    subscriptionsRow));
            Bg.run(() -> Feed.load(channels), new Bg.Callback<List<StreamInfoItem>>() {
                @Override
                public void onResult(final List<StreamInfoItem> result) {
                    subscriptionsRow.addAll(0, result.subList(0, Math.min(40, result.size())));
                }

                @Override
                public void onError(final Throwable error) {
                }
            });
        }

        favoritesRow = new ArrayObjectAdapter(cards);
        rows.add(new ListRow(new HeaderItem(id++, getString(R.string.tab_favorites)),
                favoritesRow));
        refreshFavorites();

        final StreamingService yt = ServiceList.YouTube;
        for (final String[] category : CATEGORIES) {
            final ArrayObjectAdapter row = new ArrayObjectAdapter(cards);
            rows.add(new ListRow(new HeaderItem(id++, category[0]), row));
            Bg.run(() -> streamsOnly(SearchInfo.getInfo(yt,
                            yt.getSearchQHFactory().fromQuery(category[1])).getRelatedItems()),
                    new Bg.Callback<List<StreamInfoItem>>() {
                        @Override
                        public void onResult(final List<StreamInfoItem> result) {
                            row.addAll(0, result);
                        }

                        @Override
                        public void onError(final Throwable error) {
                        }
                    });
        }
    }

    private void refreshFavorites() {
        if (favoritesRow == null || getContext() == null) {
            return;
        }
        favoritesRow.clear();
        favoritesRow.addAll(0, new Favorites(requireContext()).asItems());
    }

    static List<StreamInfoItem> streamsOnly(final List<InfoItem> items) {
        final List<StreamInfoItem> out = new ArrayList<>();
        for (final InfoItem i : items) {
            if (i instanceof StreamInfoItem) {
                out.add((StreamInfoItem) i);
            }
        }
        return out;
    }
}
