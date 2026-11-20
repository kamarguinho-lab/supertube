package com.kamarguinho.supertube.ui;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Favorites;
import com.kamarguinho.supertube.data.Feed;
import com.kamarguinho.supertube.data.History;
import com.kamarguinho.supertube.data.Movies;
import com.kamarguinho.supertube.data.Pagers;
import com.kamarguinho.supertube.data.Trending;
import com.kamarguinho.supertube.data.Importer;
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
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final StreamingService service = ServiceList.YouTube;

    private View searchBar;
    private View brandBar;
    private View chipsScroll;
    private View genreScroll;
    private Chip firstGenreChip;
    private BottomNavigationView nav;
    private boolean searchOpen = false;
    private String lastChipQuery = null;

    /** Categorias da tela inicial (nome mostrado, texto pesquisado). */
    private static final String[][] CATEGORIES = {
            {"Em alta", Trending.QUERY},
            {"Música", "música"},
            {"Jogos", "games gameplay"},
            {"Notícias", "notícias hoje"},
            {"Ao vivo", "ao vivo agora"},
            {"Podcasts", "podcast"},
            {"Filmes", Movies.QUERY},
            {"Esportes", "esportes melhores momentos"},
            {"Humor", "humor comédia"},
            {"Aprender", "como fazer tutorial"},
            {"Tecnologia", "tecnologia review"},
            {"Culinária", "receita fácil"},
    };
    private EditText searchInput;
    private ProgressBar progress;
    private TextView message;
    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private RecyclerView channelsRow;

    private final ItemAdapter searchAdapter = new ItemAdapter();
    private final ItemAdapter feedAdapter = new ItemAdapter();
    private final ChannelRowAdapter channelsAdapter = new ChannelRowAdapter();

    private static final String YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly";

    private final ItemAdapter favoritesAdapter = new ItemAdapter();
    private Subscriptions subscriptions;
    private Favorites favorites;
    private boolean feedMode = false;
    private boolean favMode = false;
    private boolean accountMode = false;
    private boolean historyMode = false;
    private final ItemAdapter historyAdapter = new ItemAdapter();
    private View historyHeader;

    private View accountPanel;
    private ProgressBar importProgress;
    private TextView importStatus;
    private boolean fillingTitles = false;

    /** Tela de escolha de conta / permissão do Google. */
    private final ActivityResultLauncher<IntentSenderRequest> googleAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        try {
                            final AuthorizationResult auth = Identity
                                    .getAuthorizationClient(this)
                                    .getAuthorizationResultFromIntent(result.getData());
                            importWithToken(auth.getAccessToken());
                        } catch (final Exception e) {
                            importProgress.setVisibility(View.GONE);
                            importStatus.setText(result.getResultCode() == RESULT_OK
                                    ? getString(R.string.login_not_configured,
                                    Images.errorMessage(e))
                                    : getString(R.string.login_cancelled));
                        }
                    });

    /** Seletor de arquivo do Google Takeout (.zip ou .csv). */
    private final ActivityResultLauncher<String[]> takeoutPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importTakeout(uri);
                }
            });

    private Pagers.Pager searchPager;
    private String lastQueryText;
    private boolean loadingMore = false;
    private Pagers.Pager feedPager;
    private boolean feedLoading = false;
    private int searchToken = 0;

    private List<StreamInfoItem> feedCache = null;
    private String feedCacheKey = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        subscriptions = new Subscriptions(this);
        favorites = new Favorites(this);
        accountPanel = findViewById(R.id.account_panel);
        importProgress = findViewById(R.id.import_progress);
        importStatus = findViewById(R.id.import_status);
        findViewById(R.id.google_login).setOnClickListener(v -> googleLogin());
        historyHeader = findViewById(R.id.history_header);
        findViewById(R.id.open_history).setOnClickListener(v -> showHistory());
        findViewById(R.id.open_favorites).setOnClickListener(v ->
                nav.setSelectedItemId(R.id.nav_favorites));
        final com.google.android.material.button.MaterialButton pauseButton =
                findViewById(R.id.history_pause);
        pauseButton.setOnClickListener(v -> {
            final boolean paused = !History.isPaused(this);
            History.setPaused(this, paused);
            pauseButton.setText(paused ? R.string.history_resume : R.string.history_pause);
            if (paused) {
                android.widget.Toast.makeText(this, R.string.history_paused_msg,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.history_clear).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.history_clear)
                        .setMessage(R.string.history_clear_confirm)
                        .setPositiveButton(R.string.history_clear, (d, w) -> {
                            History.clear(this);
                            showHistory();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
        findViewById(R.id.update_app).setOnClickListener(v ->
                com.kamarguinho.supertube.update.Updater.checkAndPrompt(this, false));
        ((TextView) findViewById(R.id.version_text)).setText(getString(R.string.version_label,
                com.kamarguinho.supertube.update.Updater.currentVersion()));
        findViewById(R.id.takeout_import).setOnClickListener(v ->
                takeoutPicker.launch(new String[]{"application/zip", "text/csv",
                        "text/comma-separated-values", "text/plain",
                        "application/octet-stream", "application/x-zip-compressed"}));
        findViewById(R.id.takeout_site).setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://takeout.google.com/"))));

        searchBar = findViewById(R.id.search_bar);
        brandBar = findViewById(R.id.brand_bar);
        chipsScroll = findViewById(R.id.chips_scroll);
        findViewById(R.id.open_search).setOnClickListener(v -> openSearch());
        findViewById(R.id.close_search).setOnClickListener(v -> closeSearch());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (historyMode) {
                    showAccount();
                } else if (searchOpen) {
                    closeSearch();
                } else if (nav.getSelectedItemId() != R.id.nav_search) {
                    nav.setSelectedItemId(R.id.nav_search);
                } else {
                    finish();
                }
            }
        });
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
                final RecyclerView.Adapter<?> a = rv.getAdapter();
                if (dy <= 0 || a == null
                        || lm.findLastVisibleItemPosition() < a.getItemCount() - 4) {
                    return;
                }
                // Rolagem sem fim: busca/Em alta/Filmes e Inscrições
                if (feedMode) {
                    feedMore();
                } else if (!favMode && !accountMode && !historyMode) {
                    loadMore();
                }
            }
        });

        channelsRow.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        channelsRow.setAdapter(channelsAdapter);

        findViewById(R.id.search_button).setOnClickListener(v -> searchTyped());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                searchTyped();
                return true;
            }
            return false;
        });

        swipe.setOnRefreshListener(() -> {
            if (feedMode) {
                feedCache = null;
                loadFeed();
            } else if (favMode) {
                swipe.setRefreshing(false);
                showFavorites();
            } else {
                swipe.setRefreshing(false);
                if (lastQueryText != null) {
                    search(lastQueryText);
                } else if (lastChipQuery != null) {
                    search(lastChipQuery);
                }
            }
        });

        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_subscriptions) {
                showFeed();
            } else if (item.getItemId() == R.id.nav_favorites) {
                showFavorites();
            } else if (item.getItemId() == R.id.nav_account) {
                showAccount();
            } else {
                showSearch();
            }
            return true;
        });

        final ChipGroup chips = findViewById(R.id.chips);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.chip_category, chips, false);
            chip.setId(View.generateViewId());
            chip.setText(CATEGORIES[i][0]);
            chip.setTag(CATEGORIES[i][1]);
            chips.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
            }
        }
        // Gêneros que aparecem dentro de "Filmes"
        genreScroll = findViewById(R.id.genre_scroll);
        final ChipGroup genres = findViewById(R.id.genre_chips);
        final String[][] genreList = new String[Movies.GENRES.length + 1][];
        genreList[0] = new String[]{getString(R.string.movies_all), Movies.QUERY};
        for (int i = 0; i < Movies.GENRES.length; i++) {
            genreList[i + 1] = new String[]{Movies.GENRES[i][0],
                    Movies.genreQuery(Movies.GENRES[i][0])};
        }
        for (int i = 0; i < genreList.length; i++) {
            final Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.chip_category, genres, false);
            chip.setId(View.generateViewId());
            chip.setText(genreList[i][0]);
            chip.setTag(genreList[i][1]);
            genres.addView(chip);
            if (i == 0) {
                chip.setChecked(true);
                firstGenreChip = chip;
            }
        }
        genres.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            final Chip chip = group.findViewById(ids.get(0));
            if (chip != null) {
                lastChipQuery = (String) chip.getTag();
                search(lastChipQuery);
            }
        });

        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            final Chip chip = group.findViewById(ids.get(0));
            if (chip != null) {
                closeSearch();
                lastChipQuery = (String) chip.getTag();
                final boolean movies = Movies.QUERY.equals(lastChipQuery);
                genreScroll.setVisibility(movies ? View.VISIBLE : View.GONE);
                if (movies && !firstGenreChip.isChecked()) {
                    // volta para "Todos os filmes" (o listener do gênero faz a busca)
                    firstGenreChip.setChecked(true);
                    return;
                }
                search(lastChipQuery);
            }
        });

        showSearch();
        lastChipQuery = CATEGORIES[0][1];
        search(lastChipQuery);

        // Procura atualização sozinho (no máximo 2 vezes por dia, sem incomodar)
        com.kamarguinho.supertube.update.Updater.checkAndPrompt(this, true);

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
        } else if (favMode) {
            showFavorites();
        } else if (historyMode) {
            showHistory();
        } else if (searchAdapter.getItemCount() > 0) {
            // atualiza as barras vermelhas de progresso
            searchAdapter.notifyDataSetChanged();
        }
    }

    // ---------- Histórico ----------

    private void showHistory() {
        resetModes();
        historyMode = true;
        historyHeader.setVisibility(View.VISIBLE);
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.history_pause))
                .setText(History.isPaused(this) ? R.string.history_resume
                        : R.string.history_pause);
        list.setAdapter(historyAdapter);
        final List<StreamInfoItem> items = History.asItems(this);
        historyAdapter.set(items);
        if (items.isEmpty()) {
            message.setText(R.string.history_empty);
            message.setVisibility(View.VISIBLE);
        }
    }

    /** Volta a tela ao estado comum antes de trocar de aba. */
    private void resetModes() {
        feedMode = false;
        favMode = false;
        accountMode = false;
        historyMode = false;
        historyHeader.setVisibility(View.GONE);
        accountPanel.setVisibility(View.GONE);
        swipe.setVisibility(View.VISIBLE);
        channelsRow.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        brandBar.setVisibility(View.VISIBLE);
        chipsScroll.setVisibility(View.GONE);
        genreScroll.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        progress.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
    }

    // ---------- Favoritos ----------

    private void showFavorites() {
        resetModes();
        favMode = true;
        list.setAdapter(favoritesAdapter);
        final List<StreamInfoItem> items = favorites.asItems();
        favoritesAdapter.set(items);
        if (items.isEmpty()) {
            message.setText(R.string.no_favorites);
            message.setVisibility(View.VISIBLE);
        }
        fillTitlesInBackground();
    }

    /** Vídeos importados do Takeout chegam só com o ID: busca os títulos aos poucos. */
    private void fillTitlesInBackground() {
        if (fillingTitles) {
            return;
        }
        boolean pending = false;
        for (final Favorites.Video v : favorites.all()) {
            if (Importer.PENDING_TITLE.equals(v.title)) {
                pending = true;
                break;
            }
        }
        if (!pending) {
            return;
        }
        fillingTitles = true;
        Bg.run(() -> Importer.fillMissingTitles(this, 40), new Bg.Callback<Integer>() {
            @Override
            public void onResult(final Integer done) {
                fillingTitles = false;
                if (favMode) {
                    favoritesAdapter.set(favorites.asItems());
                    if (done > 0) {
                        fillTitlesInBackground();
                    }
                }
            }

            @Override
            public void onError(final Throwable error) {
                fillingTitles = false;
            }
        });
    }

    // ---------- Conta / importação ----------

    private void showAccount() {
        resetModes();
        accountMode = true;
        swipe.setVisibility(View.GONE);
        accountPanel.setVisibility(View.VISIBLE);
    }

    private void googleLogin() {
        importProgress.setVisibility(View.VISIBLE);
        importStatus.setText(R.string.importing);
        final AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(YOUTUBE_READONLY)))
                .build();
        Identity.getAuthorizationClient(this).authorize(request)
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        final PendingIntent pi = result.getPendingIntent();
                        if (pi != null) {
                            googleAuthLauncher.launch(
                                    new IntentSenderRequest.Builder(pi.getIntentSender()).build());
                            return;
                        }
                    }
                    importWithToken(result.getAccessToken());
                })
                .addOnFailureListener(e -> {
                    importProgress.setVisibility(View.GONE);
                    importStatus.setText(getString(R.string.login_not_configured,
                            Images.errorMessage(e)));
                });
    }

    private void importWithToken(final String token) {
        if (token == null) {
            importProgress.setVisibility(View.GONE);
            importStatus.setText(getString(R.string.login_not_configured, "sem token"));
            return;
        }
        importProgress.setVisibility(View.VISIBLE);
        importStatus.setText(R.string.importing);
        Bg.run(() -> Importer.importFromAccount(this, token), importCallback());
    }

    private void importTakeout(final Uri uri) {
        importProgress.setVisibility(View.VISIBLE);
        importStatus.setText(R.string.importing);
        Bg.run(() -> Importer.importTakeout(this, uri), importCallback());
    }

    private Bg.Callback<Importer.Result> importCallback() {
        return new Bg.Callback<Importer.Result>() {
            @Override
            public void onResult(final Importer.Result r) {
                importProgress.setVisibility(View.GONE);
                feedCache = null;
                if (r.channelsFound == 0 && r.videosFound == 0) {
                    importStatus.setText(R.string.import_nothing);
                } else {
                    importStatus.setText(getString(R.string.import_result, r.channelsAdded,
                            r.channelsFound, r.videosAdded, r.videosFound));
                }
            }

            @Override
            public void onError(final Throwable error) {
                importProgress.setVisibility(View.GONE);
                importStatus.setText(getString(R.string.error_prefix,
                        Images.errorMessage(error)));
            }
        };
    }

    // ---------- Busca ----------

    private void showSearch() {
        resetModes();
        searchBar.setVisibility(searchOpen ? View.VISIBLE : View.GONE);
        brandBar.setVisibility(searchOpen ? View.GONE : View.VISIBLE);
        chipsScroll.setVisibility(View.VISIBLE);
        genreScroll.setVisibility(lastChipQuery != null && lastChipQuery.startsWith(Movies.QUERY)
                ? View.VISIBLE : View.GONE);
        list.setAdapter(searchAdapter);
        if (searchAdapter.getItemCount() == 0) {
            message.setText(R.string.search_hint_message);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
    }

    private void openSearch() {
        if (nav.getSelectedItemId() != R.id.nav_search) {
            nav.setSelectedItemId(R.id.nav_search);
        }
        searchOpen = true;
        brandBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        final InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void closeSearch() {
        if (!searchOpen) {
            return;
        }
        searchOpen = false;
        hideKeyboard();
        searchBar.setVisibility(View.GONE);
        brandBar.setVisibility(View.VISIBLE);
    }

    private void searchTyped() {
        final String q = searchInput.getText().toString().trim();
        if (!q.isEmpty()) {
            search(q);
        }
    }

    private void search(final String q) {
        hideKeyboard();
        final int token = ++searchToken;
        searchAdapter.clear();
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        lastQueryText = q;
        // Busca normal, "Em alta" ou "Filmes": todas sem fim (vão carregando ao rolar)
        final Pagers.Pager pager = Pagers.forQuery(q);
        searchPager = pager;
        loadingMore = true;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                if (token != searchToken) {
                    return;
                }
                loadingMore = false;
                searchAdapter.set(result);
                if (feedMode || favMode || accountMode || historyMode) {
                    return;
                }
                progress.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    message.setText(R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (token != searchToken) {
                    return;
                }
                loadingMore = false;
                if (feedMode || favMode || accountMode || historyMode) {
                    return;
                }
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadMore() {
        final Pagers.Pager pager = searchPager;
        if (loadingMore || pager == null || !pager.hasMore()) {
            return;
        }
        loadingMore = true;
        final int token = searchToken;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                if (token != searchToken) {
                    return;
                }
                loadingMore = false;
                searchAdapter.add(result);
            }

            @Override
            public void onError(final Throwable error) {
                if (token == searchToken) {
                    loadingMore = false;
                }
            }
        });
    }

    /** Inscrições: vai carregando vídeos mais antigos dos canais ao rolar. */
    private void feedMore() {
        final Pagers.Pager pager = feedPager;
        if (feedLoading || pager == null || !pager.hasMore()) {
            return;
        }
        feedLoading = true;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                feedLoading = false;
                if (pager == feedPager) {
                    feedAdapter.add(result);
                    if (feedCache != null) {
                        feedCache.addAll(result);
                    }
                }
            }

            @Override
            public void onError(final Throwable error) {
                feedLoading = false;
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
        resetModes();
        feedMode = true;
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
        final Pagers.Pager pager = new Pagers.FeedPager(channels);
        feedPager = pager;
        feedLoading = true;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                feedLoading = false;
                feedCache = result;
                feedCacheKey = key;
                if (!feedMode) {
                    return;
                }
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                feedAdapter.set(result);
                if (result.isEmpty()) {
                    message.setText(R.string.feed_empty);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                feedLoading = false;
                if (!feedMode) {
                    return;
                }
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
