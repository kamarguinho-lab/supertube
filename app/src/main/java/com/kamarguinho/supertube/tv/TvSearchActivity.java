package com.kamarguinho.supertube.tv;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;

import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.util.Bg;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** Busca na TV, com teclado na tela ou pelo microfone do controle. */
public class TvSearchActivity extends FragmentActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_fragment);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tv_container, new TvSearchFragment())
                    .commit();
        }
    }

    public static class TvSearchFragment extends SearchSupportFragment
            implements SearchSupportFragment.SearchResultProvider {

        private final ArrayObjectAdapter results = new ArrayObjectAdapter(new ListRowPresenter());
        private String lastQuery = null;
        private int token = 0;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setSearchResultProvider(this);
            setOnItemViewClickedListener((OnItemViewClickedListener)
                    (itemViewHolder, item, rowViewHolder, row) -> {
                        if (item instanceof StreamInfoItem) {
                            TvPlayerActivity.open(requireContext(),
                                    ((StreamInfoItem) item).getUrl());
                        }
                    });
        }

        @Override
        public ObjectAdapter getResultsAdapter() {
            return results;
        }

        @Override
        public boolean onQueryTextChange(final String newQuery) {
            // Só pesquisa ao confirmar, para não gastar requisições a cada letra
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(final String query) {
            if (TextUtils.isEmpty(query) || query.equals(lastQuery)) {
                return true;
            }
            lastQuery = query;
            final int current = ++token;
            results.clear();
            final StreamingService yt = ServiceList.YouTube;
            Bg.run(() -> TvBrowseFragment.streamsOnly(SearchInfo.getInfo(yt,
                            yt.getSearchQHFactory().fromQuery(query)).getRelatedItems()),
                    new Bg.Callback<List<StreamInfoItem>>() {
                        @Override
                        public void onResult(final List<StreamInfoItem> items) {
                            if (current != token || !isAdded()) {
                                return;
                            }
                            final ArrayObjectAdapter row =
                                    new ArrayObjectAdapter(new CardPresenter());
                            row.addAll(0, items);
                            results.clear();
                            results.add(new ListRow(new HeaderItem(0,
                                    getString(R.string.tv_results_for, query)), row));
                        }

                        @Override
                        public void onError(final Throwable error) {
                        }
                    });
            return true;
        }
    }
}
