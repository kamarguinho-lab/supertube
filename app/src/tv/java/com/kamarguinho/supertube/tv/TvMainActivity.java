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
            {"Tudo", null},
            {"Música", "música"},
            {"Jogos", "games gameplay"},
            {"Notícias", "notícias hoje"},
            {"Ao vivo", "ao vivo agora"},
            {"Podcasts", "podcast"},
            {"Filmes", Movies.QUERY},
            {"Esportes", "esportes melhores momentos"},
            {"Humor", "humor comédia"},
            {"Infantil", "desenho infantil"},
            {"Tecnologia", "tecnologia review"},
            {"Culinária", "receita fácil"},
    };

    /** Fileiras do "Tudo" (título, texto pesquisado). */
    private static final String[][] HOME_ROWS = {
            {"Em alta", "em alta hoje"},
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
    private View listActions;
    private TextView historyPause;
    private RecyclerView youSection;
    private final TvAdapters.RowsAdapter youRows = new TvAdapters.RowsAdapter();
    private RecyclerView channelsList;
    private RecyclerView listGrid;
    private final TvAdapters.VideoAdapter listAdapter = new TvAdapters.VideoAdapter(true);
    private final TvAdapters.ChannelAdapter channelAdapter = new TvAdapters.ChannelAdapter();
    private List<StreamInfoItem> feedCache = null;

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

        // Procura atualização sozinho (no máximo 2 vezes por dia)
        com.kamarguinho.supertube.update.Updater.checkAndPrompt(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        youSection.setVisibility(s == SECTION_YOU ? View.VISIBLE : View.GONE);
        message.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);

        switch (s) {
            case SECTION_HOME:
                if (chipSelected == 0 && !homeRowsLoaded) {
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
                    if (youSection.getChildCount() > 0) {
                        youSection.getChildAt(0).requestFocus();
                    }
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

        final LinearLayout chipsBox = findViewById(R.id.chips);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.tv_pill, chipsBox, false);
            chip.setText(CATEGORIES[i][0]);
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

        homeList.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        homeList.setAdapter(categoryGrid);
        categoryGrid.set(new ArrayList<>());
        progress.setVisibility(View.VISIBLE);
        final String query = CATEGORIES[index][1];
        Bg.run(() -> searchVideos(query), new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                if (token != homeToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                categoryGrid.set(result);
            }

            @Override
            public void onError(final Throwable error) {
                if (token != homeToken) {
                    return;
                }
                showError(error);
            }
        });
    }

    private void loadHomeRows() {
        homeRowsLoaded = true;
        final List<TvAdapters.Row> rows = new ArrayList<>();

        final List<Subscriptions.Channel> channels = new Subscriptions(this).all();
        if (!channels.isEmpty()) {
            final TvAdapters.Row row = new TvAdapters.Row(getString(R.string.tv_new_from_subs));
            rows.add(row);
            Bg.run(() -> Feed.load(channels), rowCallback(row, 30));
        }

        final List<StreamInfoItem> favs = new Favorites(this).asItems();
        if (!favs.isEmpty()) {
            final TvAdapters.Row row = new TvAdapters.Row(getString(R.string.tab_favorites));
            row.adapter.set(favs);
            row.loading = false;
            rows.add(row);
        }

        for (final String[] r : HOME_ROWS) {
            final TvAdapters.Row row = new TvAdapters.Row(r[0]);
            rows.add(row);
            Bg.run(() -> searchVideos(r[1]), rowCallback(row, 40));
        }
        rowsAdapter.set(rows);
    }

    private Bg.Callback<List<StreamInfoItem>> rowCallback(final TvAdapters.Row row,
                                                         final int max) {
        return new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                row.adapter.set(result.subList(0, Math.min(max, result.size())));
                rowsAdapter.loaded(row);
            }

            @Override
            public void onError(final Throwable error) {
                rowsAdapter.loaded(row);
            }
        };
    }

    private List<StreamInfoItem> searchVideos(final String query) throws Exception {
        if (Movies.QUERY.equals(query)) {
            return Movies.load(40);
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
        searchGrid.set(new ArrayList<>());
        progress.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
        Bg.run(() -> searchVideos(query), new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                if (token != searchToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                searchGrid.set(result);
                if (result.isEmpty()) {
                    message.setText(R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (token == searchToken) {
                    showError(error);
                }
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
        listAdapter.set(items);
        if (items.isEmpty()) {
            message.setText(R.string.history_empty);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
    }

    /** Você: fileiras de Histórico e Favoritos, como a Biblioteca do YouTube. */
    private void showYou() {
        final List<TvAdapters.Row> rows = new ArrayList<>();
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
            listAdapter.set(new ArrayList<>());
            message.setText(R.string.tv_no_subscriptions);
            message.setVisibility(View.VISIBLE);
            return;
        }
        if (feedCache != null) {
            listAdapter.set(feedCache);
            return;
        }
        listAdapter.set(new ArrayList<>());
        progress.setVisibility(View.VISIBLE);
        Bg.run(() -> Feed.load(channels), new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> result) {
                feedCache = result;
                if (section != SECTION_SUBS) {
                    return;
                }
                progress.setVisibility(View.GONE);
                listAdapter.set(result);
            }

            @Override
            public void onError(final Throwable error) {
                if (section == SECTION_SUBS) {
                    showError(error);
                }
            }
        });
    }

    private void showError(final Throwable error) {
        progress.setVisibility(View.GONE);
        message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
        message.setVisibility(View.VISIBLE);
    }
}
