package com.kamarguinho.supertube.tv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Favorites;
import com.kamarguinho.supertube.data.Feed;
import com.kamarguinho.supertube.data.History;
import com.kamarguinho.supertube.data.Movies;
import com.kamarguinho.supertube.data.Pagers;
import com.kamarguinho.supertube.data.Trending;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela principal da TV: menu lateral à esquerda (Busca no topo, Início, Inscrições,
 * Favoritos) e o conteúdo à direita, no estilo do YouTube para TV.
 */
public class TvMainActivity extends AppCompatActivity {

    private static final int SECTION_SEARCH = 0;
    private static final int SECTION_HOME = 1;
    private static final int SECTION_SUBS = 2;
    private static final int SECTION_HISTORY = 3;
    private static final int SECTION_YOU = 4;

    private static final int GRID_COLUMNS = 4;

    /** Categorias da tela inicial (nome, texto pesquisado). "Tudo" mostra as fileiras. */
    private static final String[][] CATEGORIES = {
            {"Em alta", Trending.QUERY},
            {"Tudo", null},
            {"Música", com.kamarguinho.supertube.data.Series.MUSIC},
            {"Jogos", "games gameplay"},
            {"Notícias", "notícias hoje"},
            {"Ao vivo", "ao vivo agora"},
            {"Podcasts", "podcast"},
            {"Filmes", Movies.QUERY},
            {"Séries", com.kamarguinho.supertube.data.Series.QUERY},
            {"Esportes", "esportes melhores momentos"},
            {"Humor", "humor comédia"},
            {"Infantil", "desenho infantil"},
            {"Tecnologia", "tecnologia review"},
            {"Culinária", "receita fácil"},
    };

    /** Fileiras do "Tudo" (título, texto pesquisado). */
    private static final String[][] HOME_ROWS = {
            {"Em alta", Trending.QUERY},
            {"Música", "música"},
            {"Ao vivo", "ao vivo agora"},
            {"Jogos", "games gameplay"},
            {"Notícias", "notícias hoje"},
            {"Podcasts", "podcast"},
            {"Filmes completos", Movies.QUERY},
            {"Esportes", "esportes melhores momentos"},
            {"Humor", "humor comédia"},
            {"Tecnologia", "tecnologia review"},
    };

    private final StreamingService yt = ServiceList.YouTube;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout rail;
    private final View[] railItems = new View[5];
    private int section = SECTION_HOME;

    private View homeSection;
    private View searchSection;
    private View listSection;
    private ProgressBar progress;
    private TextView message;

    // Início
    private RecyclerView homeList;
    private final List<TextView> chips = new ArrayList<>();
    private TvAdapters.RowsAdapter rowsAdapter;
    private final TvAdapters.RowsAdapter moviesRows = new TvAdapters.RowsAdapter();
    private final TvAdapters.SeriesRowsAdapter seriesRows = new TvAdapters.SeriesRowsAdapter();
    private final TvAdapters.SeriesRowsAdapter musicRows = new TvAdapters.SeriesRowsAdapter();
    private final TvAdapters.VideoAdapter categoryGrid = new TvAdapters.VideoAdapter(true);
    private int chipSelected = 0;
    private int homeToken = 0;
    private boolean homeRowsLoaded = false;

    // Busca
    private EditText searchInput;
    private final TvAdapters.VideoAdapter searchGrid = new TvAdapters.VideoAdapter(true);
    private TvAdapters.SuggestionAdapter suggestions;
    private int searchToken = 0;
    private int suggestToken = 0;
    private String lastSearch = null;

    // Inscrições / Favoritos
    private TextView listTitle;
    private ViewGroup listActions;
    private TextView historyPause;
    private RecyclerView youSection;
    private final TvAdapters.RowsAdapter youRows = new TvAdapters.RowsAdapter();
    private RecyclerView channelsList;
    private RecyclerView listGrid;
    private final TvAdapters.VideoAdapter listAdapter = new TvAdapters.VideoAdapter(true);
    private final TvAdapters.ChannelAdapter channelAdapter = new TvAdapters.ChannelAdapter();
    private String feedKey = null;
    private TvAdapters.PagedList categoryPaged;
    private TvAdapters.PagedList searchPaged;
    private TvAdapters.PagedList listPaged;
    private final java.util.Set<String> homeSeeds = new java.util.HashSet<>();
    private int extraHomeRows = 0;

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getData() == null) {
                            return;
                        }
                        final ArrayList<String> texts = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (texts != null && !texts.isEmpty()) {
                            searchInput.setText(texts.get(0));
                            search(texts.get(0));
                        }
                    });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        rail = findViewById(R.id.rail);
        homeSection = findViewById(R.id.section_home);
        searchSection = findViewById(R.id.section_search);
        listSection = findViewById(R.id.section_list);
        progress = findViewById(R.id.progress);
        message = findViewById(R.id.message);

        setupRail();
        setupHome();
        setupSearch();
        setupList();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Voltar: primeiro vai para o menu; no menu, volta ao Início; depois sai
                if (!rail.hasFocus()) {
                    railItems[section].requestFocus();
                } else if (section != SECTION_HOME) {
                    showSection(SECTION_HOME);
                    railItems[SECTION_HOME].requestFocus();
                } else {
                    finish();
                }
            }
        });

        showSection(SECTION_HOME);
        // Sempre abre no "Em alta"
        selectChip(0);

        // Procura atualização sozinho (no máximo 2 vezes por dia)
        com.kamarguinho.supertube.update.Updater.checkAndPrompt(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Puxa as mudanças feitas no celular
        syncNow(false);
        // Volta do player: atualiza histórico e barras de progresso
        if (section == SECTION_HISTORY) {
            showHistory();
        } else if (section == SECTION_YOU) {
            showYou();
        } else if (section == SECTION_SUBS) {
            listAdapter.notifyDataSetChanged();
        }
    }

    // ================= Menu lateral =================

    private void setupRail() {
        final int[] ids = {R.id.rail_search, R.id.rail_home, R.id.rail_subs,
                R.id.rail_history, R.id.rail_you};
        final int[] labels = {R.string.search, R.string.tab_search, R.string.tab_subscriptions,
                R.string.history, R.string.tab_you};
        for (int i = 0; i < ids.length; i++) {
            final View item = findViewById(ids[i]);
            railItems[i] = item;
            ((TextView) item.findViewById(R.id.rail_label)).setText(labels[i]);
            final int s = i;
            item.setOnClickListener(v -> {
                showSection(s);
                focusContent();
            });
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (s != section) {
                        showSection(s);
                    }
                    focusContent();
                    return true;
                }
                return false;
            });
        }
        updateRailIcons();

        // Idioma (Português / English)
        final View language = findViewById(R.id.rail_language);
        ((ImageView) language.findViewById(R.id.rail_icon)).setImageResource(R.drawable.ic_dub);
        ((TextView) language.findViewById(R.id.rail_label)).setText(getString(
                R.string.language_current,
                com.kamarguinho.supertube.util.LangUi.currentLabel(this)));
        language.setOnClickListener(v -> com.kamarguinho.supertube.util.LangUi.show(this));

        // Backup: o celular abre um endereço da TV no navegador (só na rede de casa)
        final View backup = findViewById(R.id.rail_backup);
        ((ImageView) backup.findViewById(R.id.rail_icon)).setImageResource(R.drawable.ic_download);
        ((TextView) backup.findViewById(R.id.rail_label)).setText(R.string.backup_title);
        backup.setOnClickListener(v -> showBackup());

        // Botão Atualizar, no fim do menu
        final View update = findViewById(R.id.rail_update);
        ((ImageView) update.findViewById(R.id.rail_icon)).setImageResource(R.drawable.ic_update);
        ((TextView) update.findViewById(R.id.rail_label)).setText(R.string.update_button);
        update.setOnClickListener(v ->
                com.kamarguinho.supertube.update.Updater.checkAndPrompt(this, false));
        ((TextView) findViewById(R.id.rail_version)).setText(
                com.kamarguinho.supertube.update.Updater.currentVersion());

        // O menu abre (mostra os nomes) quando recebe o foco e fecha quando sai
        rail.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) ->
                setRailExpanded(newFocus != null && isInside(newFocus, rail)));
    }

    private static boolean isInside(View v, final View parent) {
        while (v != null) {
            if (v == parent) {
                return true;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    private boolean railExpanded = false;

    private void setRailExpanded(final boolean expanded) {
        if (expanded == railExpanded) {
            return;
        }
        railExpanded = expanded;
        final float d = getResources().getDisplayMetrics().density;
        final ViewGroup.LayoutParams lp = rail.getLayoutParams();
        lp.width = (int) ((expanded ? 260 : 72) * d);
        rail.setLayoutParams(lp);
        // O menu abre por cima do conteúdo, que fica escurecido (como no YouTube)
        rail.setBackgroundColor(expanded ? 0x00000000 : getColor(R.color.background));
        findViewById(R.id.rail_scrim).animate().alpha(expanded ? 1f : 0f)
                .setDuration(180).start();
        findViewById(R.id.rail_brand).setVisibility(expanded ? View.VISIBLE : View.GONE);
        findViewById(R.id.rail_version).setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
        for (final View item : railItems) {
            item.findViewById(R.id.rail_label).setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        findViewById(R.id.rail_update).findViewById(R.id.rail_label)
                .setVisibility(expanded ? View.VISIBLE : View.GONE);
        findViewById(R.id.rail_language).findViewById(R.id.rail_label)
                .setVisibility(expanded ? View.VISIBLE : View.GONE);
        findViewById(R.id.rail_backup).findViewById(R.id.rail_label)
                .setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void updateRailIcons() {
        final int[][] icons = {
                {R.drawable.ic_search, R.drawable.ic_search},
                {R.drawable.ic_home_outline, R.drawable.ic_home_filled},
                {R.drawable.ic_subscriptions_outline, R.drawable.ic_subscriptions_filled},
                {R.drawable.ic_history, R.drawable.ic_history},
                {R.drawable.ic_account_outline, R.drawable.ic_account_filled},
        };
        for (int i = 0; i < railItems.length; i++) {
            final boolean active = i == section;
            ((ImageView) railItems[i].findViewById(R.id.rail_icon))
                    .setImageResource(icons[i][active ? 1 : 0]);
            railItems[i].setSelected(active);
        }
    }

    private void showSection(final int s) {
        section = s;
        updateRailIcons();
        homeSection.setVisibility(s == SECTION_HOME ? View.VISIBLE : View.GONE);
        searchSection.setVisibility(s == SECTION_SEARCH ? View.VISIBLE : View.GONE);
        listSection.setVisibility(s == SECTION_SUBS || s == SECTION_HISTORY
                ? View.VISIBLE : View.GONE);
        findViewById(R.id.section_you_box).setVisibility(s == SECTION_YOU
                ? View.VISIBLE : View.GONE);
        message.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);

        switch (s) {
            case SECTION_HOME:
                if (CATEGORIES[chipSelected][1] == null && !homeRowsLoaded) {
                    loadHomeRows();
                }
                break;
            case SECTION_SUBS:
                showSubscriptions();
                break;
            case SECTION_HISTORY:
                showHistory();
                break;
            case SECTION_YOU:
                showYou();
                break;
            default:
                break;
        }
    }

    /** Leva o foco do menu para o conteúdo da seção. */
    private void focusContent() {
        handler.post(() -> {
            switch (section) {
                case SECTION_SEARCH:
                    searchInput.requestFocus();
                    showKeyboard();
                    break;
                case SECTION_HOME:
                    chips.get(chipSelected).requestFocus();
                    break;
                case SECTION_YOU:
                    findViewById(R.id.you_login).requestFocus();
                    break;
                default:
                    if (listActions.getVisibility() == View.VISIBLE
                            && listGrid.getChildCount() == 0) {
                        listActions.getChildAt(0).requestFocus();
                        break;
                    }
                    if (channelsList.getVisibility() == View.VISIBLE
                            && channelsList.getChildCount() > 0) {
                        channelsList.getChildAt(0).requestFocus();
                    } else if (listGrid.getChildCount() > 0) {
                        listGrid.getChildAt(0).requestFocus();
                    }
                    break;
            }
        });
    }

    // ================= Início =================

    private void setupHome() {
        homeList = findViewById(R.id.home_list);
        homeList.setLayoutManager(new LinearLayoutManager(this));
        homeList.setItemAnimator(null);
        rowsAdapter = new TvAdapters.RowsAdapter();
        homeList.setAdapter(rowsAdapter);
        categoryPaged = new TvAdapters.PagedList(categoryGrid, null);
        // Rolagem para baixo sem fim: na grade carrega mais vídeos; nas fileiras, mais fileiras
        TvAdapters.endless(homeList, () -> {
            if (homeList.getAdapter() == categoryGrid) {
                categoryPaged.more();
            } else if (homeList.getAdapter() == rowsAdapter) {
                addMoreHomeRow();
            }
        });

        final LinearLayout chipsBox = findViewById(R.id.chips);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.tv_pill, chipsBox, false);
            chip.setText(com.kamarguinho.supertube.util.Lang.t(CATEGORIES[i][0]));
            final int index = i;
            chip.setOnClickListener(v -> selectChip(index));
            chipsBox.addView(chip);
            chips.add(chip);
        }
        chips.get(0).setSelected(true);
    }

    private void selectChip(final int index) {
        chips.get(chipSelected).setSelected(false);
        chipSelected = index;
        chips.get(index).setSelected(true);
        final int token = ++homeToken;

        if (CATEGORIES[index][1] == null) {
            homeList.setLayoutManager(new LinearLayoutManager(this));
            homeList.setAdapter(rowsAdapter);
            if (!homeRowsLoaded) {
                loadHomeRows();
            }
            return;
        }

        if (com.kamarguinho.supertube.data.Series.MUSIC.equals(CATEGORIES[index][1])) {
            // Música: playlists por estilo (cada uma com as músicas separadas)
            homeList.setLayoutManager(new LinearLayoutManager(this));
            homeList.setAdapter(musicRows);
            if (musicRows.size() == 0) {
                final List<TvAdapters.SeriesRow> rows = new ArrayList<>();
                for (final String[] g : com.kamarguinho.supertube.data.Series.musicGenres()) {
                    rows.add(new TvAdapters.SeriesRow(com.kamarguinho.supertube.util.Lang.t(g[0]),
                            com.kamarguinho.supertube.data.Series.pagerFor(
                                    com.kamarguinho.supertube.data.Series.musicQuery(g[0]))));
                }
                musicRows.set(rows);
            }
            progress.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
            return;
        }
        if (com.kamarguinho.supertube.data.Series.QUERY.equals(CATEGORIES[index][1])) {
            // Séries: uma fileira por gênero
            homeList.setLayoutManager(new LinearLayoutManager(this));
            homeList.setAdapter(seriesRows);
            if (seriesRows.size() == 0) {
                final List<TvAdapters.SeriesRow> rows = new ArrayList<>();
                rows.add(new TvAdapters.SeriesRow(getString(R.string.series_all),
                        com.kamarguinho.supertube.data.Series.pagerFor(
                                com.kamarguinho.supertube.data.Series.QUERY)));
                for (final String[] g : com.kamarguinho.supertube.data.Series.GENRES) {
                    rows.add(new TvAdapters.SeriesRow(com.kamarguinho.supertube.util.Lang.t(g[0]),
                            com.kamarguinho.supertube.data.Series.pagerFor(
                                    com.kamarguinho.supertube.data.Series.genreQuery(g[0]))));
                }
                seriesRows.set(rows);
            }
            progress.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
            return;
        }
        if (Movies.QUERY.equals(CATEGORIES[index][1])) {
            // Filmes: uma fileira por gênero (cada uma sem fim para o lado)
            homeList.setLayoutManager(new LinearLayoutManager(this));
            homeList.setAdapter(moviesRows);
            if (moviesRows.size() == 0) {
                final List<TvAdapters.Row> rows = new ArrayList<>();
                rows.add(new TvAdapters.Row(getString(R.string.movies_all),
                        Pagers.forQuery(Movies.QUERY)));
                for (final String[] g : Movies.GENRES) {
                    rows.add(new TvAdapters.Row(com.kamarguinho.supertube.util.Lang.t(g[0]),
                            Pagers.forQuery(Movies.genreQuery(g[0]))));
                }
                moviesRows.set(rows);
            }
            progress.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
            return;
        }

        homeList.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        homeList.setAdapter(categoryGrid);
        progress.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
        categoryPaged.start(Pagers.forQuery(CATEGORIES[index][1]), (items, error) -> {
            if (token != homeToken) {
                return;
            }
            progress.setVisibility(View.GONE);
            if (error != null) {
                showError(error);
            }
        });
    }

    private void loadHomeRows() {
        homeRowsLoaded = true;
        final List<TvAdapters.Row> rows = new ArrayList<>();

        final List<Subscriptions.Channel> channels = new Subscriptions(this).all();
        if (!channels.isEmpty()) {
            rows.add(new TvAdapters.Row(getString(R.string.tv_new_from_subs),
                    new Pagers.FeedPager(channels)));
        }

        final List<StreamInfoItem> favs = new Favorites(this).asItems();
        if (!favs.isEmpty()) {
            final TvAdapters.Row row = new TvAdapters.Row(getString(R.string.tab_favorites));
            row.adapter.set(favs);
            row.loading = false;
            rows.add(row);
        }

        for (final String[] r : HOME_ROWS) {
            rows.add(new TvAdapters.Row(com.kamarguinho.supertube.util.Lang.t(r[0]), Pagers.forQuery(r[1])));
        }
        rowsAdapter.set(rows);
        for (final TvAdapters.Row row : rows) {
            rowsAdapter.loadMore(row);
        }
    }

    /** Fileiras sem fim no "Tudo": cria "Mais como ..." a partir de vídeos já mostrados. */
    private void addMoreHomeRow() {
        final int n = rowsAdapter.size();
        if (n == 0 || extraHomeRows >= 30 || rowsAdapter.get(n - 1).loading) {
            return;
        }
        StreamInfoItem seed = null;
        for (int r = 0; r < n && seed == null; r++) {
            for (final StreamInfoItem item : rowsAdapter.get(r).adapter.items()) {
                if (homeSeeds.add(item.getUrl())) {
                    seed = item;
                    break;
                }
            }
        }
        if (seed == null) {
            return;
        }
        extraHomeRows++;
        rowsAdapter.start(new TvAdapters.Row(getString(R.string.tv_more_like, seed.getName()),
                new Pagers.RelatedPager(java.util.Collections.singletonList(seed))));
    }

    private List<StreamInfoItem> searchVideos(final String query) throws Exception {
        if (Movies.QUERY.equals(query)) {
            return Movies.load(40);
        }
        if (Trending.QUERY.equals(query)) {
            return Trending.load();
        }
        return TvAdapters.streamsOnly(SearchInfo.getInfo(yt,
                yt.getSearchQHFactory().fromQuery(query)).getRelatedItems());
    }

    // ================= Busca =================

    private void setupSearch() {
        searchInput = findViewById(R.id.search_input);
        final RecyclerView results = findViewById(R.id.search_results);
        results.setLayoutManager(new GridLayoutManager(this, 3));
        results.setAdapter(searchGrid);
        searchPaged = new TvAdapters.PagedList(searchGrid, results);

        final RecyclerView suggestionList = findViewById(R.id.suggestions);
        suggestionList.setLayoutManager(new LinearLayoutManager(this));
        suggestions = new TvAdapters.SuggestionAdapter(text -> {
            searchInput.setText(text);
            search(text);
        });
        suggestionList.setAdapter(suggestions);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                search(searchInput.getText().toString());
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int a, final int b,
                                          final int c) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int a, final int b,
                                      final int c) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                final String q = s.toString().trim();
                final int token = ++suggestToken;
                handler.postDelayed(() -> {
                    if (token == suggestToken) {
                        loadSuggestions(q);
                    }
                }, 300);
            }
        });

        findViewById(R.id.mic).setOnClickListener(v -> {
            try {
                voiceLauncher.launch(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.tv_voice)));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(this, R.string.tv_no_voice, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadSuggestions(final String query) {
        if (query.isEmpty()) {
            suggestions.set(new ArrayList<>());
            return;
        }
        final int token = suggestToken;
        Bg.run(() -> yt.getSuggestionExtractor().suggestionList(query),
                new Bg.Callback<List<String>>() {
                    @Override
                    public void onResult(final List<String> result) {
                        if (token == suggestToken) {
                            suggestions.set(result.subList(0, Math.min(10, result.size())));
                        }
                    }

                    @Override
                    public void onError(final Throwable error) {
                    }
                });
    }

    private void search(final String raw) {
        final String query = raw.trim();
        if (query.isEmpty() || query.equals(lastSearch)) {
            return;
        }
        lastSearch = query;
        hideKeyboard();
        final int token = ++searchToken;
        progress.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
        searchPaged.start(new Pagers.SearchPager(query), (items, error) -> {
            if (token != searchToken) {
                return;
            }
            progress.setVisibility(View.GONE);
            if (error != null) {
                showError(error);
            } else if (items.isEmpty()) {
                message.setText(R.string.no_results);
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showKeyboard() {
        final InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        final InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    // ================= Inscrições / Favoritos =================

    private void setupList() {
        listTitle = findViewById(R.id.list_title);
        channelsList = findViewById(R.id.channels);
        channelsList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        channelsList.setAdapter(channelAdapter);
        listGrid = findViewById(R.id.list_grid);
        listGrid.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        listGrid.setAdapter(listAdapter);
        listPaged = new TvAdapters.PagedList(listAdapter, listGrid);

        listActions = findViewById(R.id.list_actions);
        historyPause = findViewById(R.id.history_pause);
        findViewById(R.id.history_clear).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.history_clear)
                        .setMessage(R.string.history_clear_confirm)
                        .setPositiveButton(R.string.history_clear, (d, w) -> {
                            History.clear(this);
                            Toast.makeText(this, R.string.history_cleared,
                                    Toast.LENGTH_SHORT).show();
                            showHistory();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
        historyPause.setOnClickListener(v -> {
            final boolean paused = !History.isPaused(this);
            History.setPaused(this, paused);
            if (paused) {
                Toast.makeText(this, R.string.history_paused_msg, Toast.LENGTH_LONG).show();
            }
            updatePauseLabel();
        });

        youSection = findViewById(R.id.section_you);
        youSection.setLayoutManager(new LinearLayoutManager(this));
        youSection.setItemAnimator(null);
        youSection.setAdapter(youRows);
        findViewById(R.id.you_login).setOnClickListener(v -> {
            if (com.kamarguinho.supertube.sync.Account.loggedIn(this)) {
                com.kamarguinho.supertube.sync.Account.signOut(this);
                showYou();
            } else {
                pairDialog();
            }
        });
        findViewById(R.id.you_sync).setOnClickListener(v -> syncNow(true));
        com.kamarguinho.supertube.sync.Sync.setListener(this::refreshAfterSync);
    }

    private void updatePauseLabel() {
        historyPause.setText(History.isPaused(this) ? R.string.history_resume
                : R.string.history_pause);
    }

    private void showHistory() {
        listTitle.setText(R.string.history);
        channelsList.setVisibility(View.GONE);
        listActions.setVisibility(View.VISIBLE);
        updatePauseLabel();
        final List<StreamInfoItem> items = History.asItems(this);
        feedKey = null;
        listPaged.fixed(items);
        if (items.isEmpty()) {
            message.setText(R.string.history_empty);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
    }

    // ================= Conta =================

    private void updateAccountHeader() {
        final TextView account = findViewById(R.id.you_account);
        final TextView login = findViewById(R.id.you_login);
        final boolean in = com.kamarguinho.supertube.sync.Account.loggedIn(this);
        if (!com.kamarguinho.supertube.sync.Account.configured()) {
            account.setText(R.string.account_not_configured);
            login.setVisibility(View.GONE);
        } else if (in) {
            account.setText(getString(R.string.account_connected,
                    com.kamarguinho.supertube.sync.Account.email(this)));
            login.setText(R.string.account_logout);
            login.setVisibility(View.VISIBLE);
        } else {
            account.setText(R.string.account_desc);
            login.setText(R.string.account_login);
            login.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.you_sync).setVisibility(in ? View.VISIBLE : View.GONE);
    }

    /** Mostra o código de 6 números e espera o celular aprovar. */
    private void pairDialog() {
        final TextView code = new TextView(this);
        code.setTextSize(56);
        code.setTextColor(getColor(R.color.text_primary));
        code.setGravity(android.view.Gravity.CENTER);
        code.setLetterSpacing(0.2f);
        code.setText("······");
        final TextView steps = new TextView(this);
        steps.setText(R.string.tv_login_steps);
        steps.setTextColor(getColor(R.color.text_secondary));
        steps.setTextSize(16);
        final TextView wait = new TextView(this);
        wait.setText(R.string.tv_login_waiting);
        wait.setTextColor(getColor(R.color.text_secondary));
        wait.setGravity(android.view.Gravity.CENTER);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(steps);
        box.addView(code);
        box.addView(wait);

        final String[] current = {null};
        final boolean[] open = {true};
        final androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.tv_login_title)
                        .setView(box)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setOnDismissListener(d -> {
                            open[0] = false;
                            if (current[0] != null) {
                                final String c = current[0];
                                Bg.run(() -> {
                                    if (!com.kamarguinho.supertube.sync.Account.loggedIn(
                                            this)) {
                                        com.kamarguinho.supertube.sync.Account
                                                .cancelPairing(this, c);
                                    }
                                    return true;
                                }, new Bg.Callback<Boolean>() {
                                    @Override
                                    public void onResult(final Boolean r) {
                                    }

                                    @Override
                                    public void onError(final Throwable e) {
                                    }
                                });
                            }
                        })
                        .show();

        Bg.run(() -> com.kamarguinho.supertube.sync.Account.startPairing(this),
                new Bg.Callback<String>() {
                    @Override
                    public void onResult(final String c) {
                        current[0] = c;
                        code.setText(c.substring(0, 3) + " " + c.substring(3));
                        poll(c, dialog, open);
                    }

                    @Override
                    public void onError(final Throwable error) {
                        wait.setText(getString(R.string.error_prefix,
                                Images.errorMessage(error)));
                    }
                });
    }

    private void poll(final String code, final androidx.appcompat.app.AlertDialog dialog,
                      final boolean[] open) {
        handler.postDelayed(() -> {
            if (!open[0]) {
                return;
            }
            Bg.run(() -> com.kamarguinho.supertube.sync.Account.checkPairing(this, code),
                    new Bg.Callback<Boolean>() {
                        @Override
                        public void onResult(final Boolean ok) {
                            if (ok) {
                                dialog.dismiss();
                                Toast.makeText(TvMainActivity.this, getString(
                                        R.string.tv_login_done,
                                        com.kamarguinho.supertube.sync.Account.email(
                                                TvMainActivity.this)),
                                        Toast.LENGTH_LONG).show();
                                syncNow(false);
                                showYou();
                            } else {
                                poll(code, dialog, open);
                            }
                        }

                        @Override
                        public void onError(final Throwable error) {
                            poll(code, dialog, open);
                        }
                    });
        }, 3000);
    }

    private void syncNow(final boolean toast) {
        com.kamarguinho.supertube.sync.Sync.syncAll(this, new Bg.Callback<Integer>() {
            @Override
            public void onResult(final Integer received) {
                if (toast) {
                    Toast.makeText(TvMainActivity.this, R.string.account_synced,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (toast) {
                    Toast.makeText(TvMainActivity.this, Images.errorMessage(error),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Chegaram dados do celular: atualiza a tela aberta. */
    private void refreshAfterSync() {
        feedKey = null;
        homeRowsLoaded = false;
        if (section == SECTION_YOU) {
            showYou();
        } else if (section == SECTION_HISTORY) {
            showHistory();
        } else if (section == SECTION_SUBS) {
            showSubscriptions();
        }
    }

    /** Você: conta, playlists, histórico e favoritos, como a Biblioteca do YouTube. */
    private void showYou() {
        updateAccountHeader();
        final List<TvAdapters.Row> rows = new ArrayList<>();
        for (final com.kamarguinho.supertube.data.Playlists.Playlist p
                : new com.kamarguinho.supertube.data.Playlists(this).all()) {
            if (p.videos.isEmpty()) {
                continue;
            }
            final TvAdapters.Row row = new TvAdapters.Row(p.name);
            final List<StreamInfoItem> items = p.asItems();
            row.adapter.set(items);
            row.loading = false;
            // Tocando um vídeo da playlist, os próximos seguem em ordem
            row.adapter.clickOverride = item -> {
                com.kamarguinho.supertube.player.PlayQueue.set(p.name, items, null);
                TvPlayerActivity.open(this, item.getUrl());
            };
            rows.add(row);
        }
        final List<StreamInfoItem> history = History.asItems(this);
        if (!history.isEmpty()) {
            final TvAdapters.Row row = new TvAdapters.Row(getString(R.string.history));
            row.adapter.set(history.subList(0, Math.min(40, history.size())));
            row.loading = false;
            rows.add(row);
        }
        final List<StreamInfoItem> favs = new Favorites(this).asItems();
        if (!favs.isEmpty()) {
            final TvAdapters.Row row = new TvAdapters.Row(getString(R.string.tab_favorites));
            row.adapter.set(favs);
            row.loading = false;
            rows.add(row);
        }
        youRows.set(rows);
        if (rows.isEmpty()) {
            message.setText(R.string.tv_no_favorites);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
    }

    private void showSubscriptions() {
        listTitle.setText(R.string.tab_subscriptions);
        listActions.setVisibility(View.GONE);
        final List<Subscriptions.Channel> channels = new Subscriptions(this).all();
        channelAdapter.set(channels);
        channelsList.setVisibility(channels.isEmpty() ? View.GONE : View.VISIBLE);
        if (channels.isEmpty()) {
            feedKey = null;
            listPaged.fixed(new ArrayList<>());
            message.setText(R.string.tv_no_subscriptions);
            message.setVisibility(View.VISIBLE);
            return;
        }
        final StringBuilder key = new StringBuilder();
        for (final Subscriptions.Channel c : channels) {
            key.append(c.url).append('|');
        }
        if (key.toString().equals(feedKey)) {
            listAdapter.notifyDataSetChanged();
            return;
        }
        feedKey = key.toString();
        progress.setVisibility(View.VISIBLE);
        listPaged.start(new Pagers.FeedPager(channels), (items, error) -> {
            if (section != SECTION_SUBS) {
                return;
            }
            progress.setVisibility(View.GONE);
            if (error != null) {
                showError(error);
            }
        });
    }

    private void showError(final Throwable error) {
        progress.setVisibility(View.GONE);
        message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
        message.setVisibility(View.VISIBLE);
    }

    // ---------- Backup pela rede de casa ----------

    private void showBackup() {
        final BackupServer[] holder = new BackupServer[1];
        final BackupServer server = new BackupServer(this, new BackupServer.Listener() {
            @Override
            public void onRestored(final int count) {
                Toast.makeText(TvMainActivity.this, getString(R.string.backup_restored, count),
                        Toast.LENGTH_LONG).show();
                holder[0].stop();
                recreate();
            }

            @Override
            public void onError(final String message) {
                Toast.makeText(TvMainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        holder[0] = server;
        final String address;
        try {
            address = server.start();
        } catch (final Exception e) {
            server.stop();
            Toast.makeText(this, R.string.backup_no_wifi, Toast.LENGTH_LONG).show();
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_title)
                .setMessage(getString(R.string.backup_tv_info, address))
                .setPositiveButton(R.string.close, null)
                .setOnDismissListener(d -> server.stop())
                .show();
    }
}
