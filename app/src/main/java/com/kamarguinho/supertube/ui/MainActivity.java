package com.kamarguinho.supertube.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Feed;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final StreamingService service = ServiceList.YouTube;

    private View searchBar;
    private EditText searchInput;
    private ProgressBar progress;
    private TextView message;
    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private RecyclerView channelsRow;

    private final ItemAdapter searchAdapter = new ItemAdapter();
    private final ItemAdapter feedAdapter = new ItemAdapter();
    private final ChannelRowAdapter channelsAdapter = new ChannelRowAdapter();

    private Subscriptions subscriptions;
    private boolean feedMode = false;

    private SearchQueryHandler currentQuery;
    private Page nextPage;
    private boolean loadingMore = false;
    private int searchToken = 0;

    private List<StreamInfoItem> feedCache = null;
    private String feedCacheKey = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        subscriptions = new Subscriptions(this);

        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        progress = findViewById(R.id.progress);
        message = findViewById(R.id.message);
        swipe = findViewById(R.id.swipe);
        list = findViewById(R.id.list);
        channelsRow = findViewById(R.id.channels_row);

        final LinearLayoutManager lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        list.setAdapter(searchAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull final RecyclerView rv, final int dx, final int dy) {
                if (!feedMode && dy > 0
                        && lm.findLastVisibleItemPosition() >= searchAdapter.getItemCount() - 4) {
                    loadMore();
                }
            }
        });

        channelsRow.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        channelsRow.setAdapter(channelsAdapter);

        findViewById(R.id.search_button).setOnClickListener(v -> search());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                search();
                return true;
            }
            return false;
        });

        swipe.setOnRefreshListener(() -> {
            if (feedMode) {
                feedCache = null;
                loadFeed();
            } else {
                swipe.setRefreshing(false);
                if (currentQuery != null) {
                    search();
                }
            }
        });

        final BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_subscriptions) {
                showFeed();
            } else {
                showSearch();
            }
            return true;
        });

        message.setText(R.string.search_hint_message);
        message.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (feedMode) {
            showFeed();
        }
    }

    // ---------- Busca ----------

    private void showSearch() {
        feedMode = false;
        searchBar.setVisibility(View.VISIBLE);
        channelsRow.setVisibility(View.GONE);
        list.setAdapter(searchAdapter);
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        if (searchAdapter.getItemCount() == 0) {
            message.setText(R.string.search_hint_message);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
    }

    private void search() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            return;
        }
        hideKeyboard();
        final int token = ++searchToken;
        searchAdapter.clear();
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        nextPage = null;

        Bg.run(() -> {
            final SearchQueryHandler handler = service.getSearchQHFactory().fromQuery(q);
            return new Object[]{handler, SearchInfo.getInfo(service, handler)};
        }, new Bg.Callback<Object[]>() {
            @Override
            public void onResult(final Object[] r) {
                if (token != searchToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                currentQuery = (SearchQueryHandler) r[0];
                final SearchInfo info = (SearchInfo) r[1];
                nextPage = info.getNextPage();
                searchAdapter.set(info.getRelatedItems());
                if (searchAdapter.getItemCount() == 0) {
                    message.setText(R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (token != searchToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadMore() {
        if (loadingMore || currentQuery == null || !Page.isValid(nextPage)) {
            return;
        }
        loadingMore = true;
        final int token = searchToken;
        final SearchQueryHandler query = currentQuery;
        final Page page = nextPage;
        Bg.run(() -> SearchInfo.getMoreItems(service, query, page),
                new Bg.Callback<ListExtractor.InfoItemsPage<InfoItem>>() {
                    @Override
                    public void onResult(final ListExtractor.InfoItemsPage<InfoItem> result) {
                        loadingMore = false;
                        if (token != searchToken) {
                            return;
                        }
                        nextPage = result.getNextPage();
                        searchAdapter.add(result.getItems());
                    }

                    @Override
                    public void onError(final Throwable error) {
                        loadingMore = false;
                    }
                });
    }

    private void hideKeyboard() {
        final InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }

    // ---------- Inscrições ----------

    private void showFeed() {
        feedMode = true;
        searchBar.setVisibility(View.GONE);
        list.setAdapter(feedAdapter);
        final List<Subscriptions.Channel> channels = subscriptions.all();
        channelsAdapter.set(channels);
        channelsRow.setVisibility(channels.isEmpty() ? View.GONE : View.VISIBLE);

        if (channels.isEmpty()) {
            feedAdapter.clear();
            progress.setVisibility(View.GONE);
            message.setText(R.string.no_subscriptions);
            message.setVisibility(View.VISIBLE);
            return;
        }
        final String key = keyOf(channels);
        if (feedCache != null && key.equals(feedCacheKey)) {
            feedAdapter.set(feedCache);
            message.setVisibility(feedCache.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        loadFeed();
    }

    private void loadFeed() {
        final List<Subscriptions.Channel> channels = subscriptions.all();
        final String key = keyOf(channels);
        message.setVisibility(View.GONE);
        if (!swipe.isRefreshing()) {
            progress.setVisibility(View.VISIBLE);
        }
        Bg.run(() -> Feed.load(channels), new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                feedCache = result;
                feedCacheKey = key;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                if (!feedMode) {
                    return;
                }
                feedAdapter.set(result);
                if (result.isEmpty()) {
                    message.setText(R.string.feed_empty);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private static String keyOf(final List<Subscriptions.Channel> channels) {
        final StringBuilder sb = new StringBuilder();
        for (final Subscriptions.Channel c : channels) {
            sb.append(c.url).append('|');
        }
        return sb.toString();
    }

    /** Fileira com as fotos dos canais inscritos. */
    static class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowAdapter.Holder> {
        private final List<Subscriptions.Channel> channels = new ArrayList<>();

        void set(final List<Subscriptions.Channel> list) {
            channels.clear();
            channels.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_channel_small, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, final int position) {
            final Subscriptions.Channel c = channels.get(position);
            h.name.setText(c.name);
            Glide.with(h.itemView).load(c.avatar)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.color.thumb_placeholder).into(h.avatar);
            h.itemView.setOnClickListener(v ->
                    ChannelActivity.open(v.getContext(), c.serviceId, c.url));
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;

            Holder(final View v) {
                super(v);
                avatar = v.findViewById(R.id.avatar);
                name = v.findViewById(R.id.name);
            }
        }
    }
}
