package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Favorites;
import com.kamarguinho.supertube.data.History;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.player.MediaResolver;
import com.kamarguinho.supertube.player.Subtitles;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/**
 * Player em tela cheia. Controle remoto: OK mostra os controles, play/pause funciona,
 * Menu favorita, e o próximo vídeo começa sozinho no final.
 */
public class TvPlayerActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final int MAX_HEIGHT = 1080;

    private ExoPlayer player;
    private StyledPlayerView playerView;
    private View overlay;
    private TextView titleView;
    private TextView subView;
    private TextView hintView;
    private ImageView avatar;
    private ProgressBar progress;
    private MediaResolver resolver;
    private StreamInfo current;
    private int token = 0;

    // Painel "A seguir" (seta para baixo)
    private View panel;
    private View downHint;
    private View actionFavorite;
    private View actionChannel;
    private View actionSubscribe;
    private View actionNext;
    private View actionSubtitles;
    private Subtitles.Option currentSubtitle;
    private View panelBox;
    private View panelScrim;
    private View actionsRow;
    private RecyclerView panelRows;
    private final TvAdapters.RowsAdapter rowsAdapter = new TvAdapters.RowsAdapter();
    private boolean panelExpanded = false;
    private boolean loadingMore = false;
    private int extraRows = 0;
    private final java.util.Set<String> usedSeeds = new java.util.HashSet<>();
    private static final int MAX_EXTRA_ROWS = 20;

    public static void open(final Context context, final String url) {
        context.startActivity(new Intent(context, TvPlayerActivity.class)
                .putExtra(EXTRA_URL, url));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = findViewById(R.id.player_view);
        overlay = findViewById(R.id.overlay_top);
        titleView = findViewById(R.id.tv_title);
        subView = findViewById(R.id.tv_sub);
        hintView = findViewById(R.id.tv_hint);
        avatar = findViewById(R.id.avatar);
        progress = findViewById(R.id.progress);
        resolver = new MediaResolver(this);
        setupPanel();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(final int state) {
                if (state == Player.STATE_ENDED) {
                    if (current != null) {
                        History.savePosition(TvPlayerActivity.this, current.getUrl(),
                                current.getDuration() * 1000);
                    }
                    playNext();
                }
            }

            @Override
            public void onPlayerError(@NonNull final PlaybackException error) {
                Toast.makeText(TvPlayerActivity.this,
                        getString(R.string.error_prefix, error.getErrorCodeName()),
                        Toast.LENGTH_LONG).show();
            }
        });
        playerView.setPlayer(player);
        playerView.setShowSubtitleButton(false);
        Subtitles.applyStyle(playerView.getSubtitleView(), 1.15f);
        playerView.setControllerVisibilityListener(
                (StyledPlayerView.ControllerVisibilityListener) visibility ->
                {
                    overlay.setVisibility(visibility);
                    downHint.setVisibility(panel.getVisibility() == View.VISIBLE
                            ? View.GONE : visibility);
                });
        playerView.requestFocus();

        load(getIntent().getStringExtra(EXTRA_URL));
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        load(intent.getStringExtra(EXTRA_URL));
    }

    private void load(final String url) {
        if (url == null) {
            finish();
            return;
        }
        saveProgress();
        final int t = ++token;
        hidePanel();
        rowsAdapter.clear();
        usedSeeds.clear();
        extraRows = 0;
        loadingMore = false;
        progress.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        titleView.setText("");
        subView.setText("");
        player.stop();
        Bg.run(() -> {
            final StreamInfo info = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url);
            final Subtitles.Option sub = Subtitles.pick(info,
                    Subtitles.mode(TvPlayerActivity.this));
            return new Object[]{info, resolver.resolve(info, MAX_HEIGHT, false, sub), sub};
        }, new Bg.Callback<Object[]>() {
            @Override
            public void onResult(final Object[] r) {
                if (t != token) {
                    return;
                }
                progress.setVisibility(View.GONE);
                current = (StreamInfo) r[0];
                titleView.setText(current.getName());
                final StringBuilder sub = new StringBuilder(current.getUploaderName());
                if (current.getViewCount() >= 0) {
                    sub.append(" • ").append(TvAdapters.formatCount(current.getViewCount()))
                            .append(" de visualizações");
                }
                if (current.getTextualUploadDate() != null) {
                    sub.append(" • ").append(current.getTextualUploadDate());
                }
                subView.setText(sub);
                Glide.with(TvPlayerActivity.this)
                        .load(Images.best(current.getUploaderAvatars()))
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.avatar_placeholder).into(avatar);
                updateHint();
                updateActions();
                buildPanelRows(current, t);
                currentSubtitle = (Subtitles.Option) r[2];
                Subtitles.select(player, currentSubtitle);
                // Continua de onde parou e grava no histórico
                final long resume = History.resumePosition(TvPlayerActivity.this, url);
                History.record(TvPlayerActivity.this, current);
                if (resume > 0) {
                    player.setMediaSource((MediaSource) r[1], resume);
                } else {
                    player.setMediaSource((MediaSource) r[1]);
                }
                player.prepare();
                player.play();
            }

            @Override
            public void onError(final Throwable error) {
                if (t != token) {
                    return;
                }
                progress.setVisibility(View.GONE);
                titleView.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
            }
        });
    }

    /** No fim do vídeo, toca o primeiro relacionado (como no YouTube da TV). */
    private void playNext() {
        if (current == null) {
            return;
        }
        for (final InfoItem item : current.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                load(item.getUrl());
                return;
            }
        }
    }

    private void updateHint() {
        final boolean fav = current != null && new Favorites(this).contains(current.getUrl());
        hintView.setText(fav ? R.string.tv_hint_favorited : R.string.tv_hint);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BOOKMARK
                || keyCode == KeyEvent.KEYCODE_PROG_RED) && current != null) {
            toggleFavorite();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            playNext();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ================= Painel "A seguir" =================

    private void setupPanel() {
        panel = findViewById(R.id.panel);
        downHint = findViewById(R.id.down_hint);
        actionFavorite = findViewById(R.id.action_favorite);
        actionChannel = findViewById(R.id.action_channel);
        actionSubscribe = findViewById(R.id.action_subscribe);
        actionNext = findViewById(R.id.action_next);
        actionSubtitles = findViewById(R.id.action_subtitles);
        setAction(actionSubtitles, R.drawable.ic_subtitles, R.string.subtitles);
        TvAdapters.focusZoom(actionSubtitles, 1.06f);
        actionSubtitles.setOnClickListener(v -> chooseSubtitle());

        setAction(actionChannel, R.drawable.ic_account_outline, R.string.tv_go_channel);
        setAction(actionNext, R.drawable.ic_skip_next, R.string.tv_next);
        for (final View v : new View[]{actionFavorite, actionChannel, actionSubscribe,
                actionNext}) {
            TvAdapters.focusZoom(v, 1.06f);
        }

        actionFavorite.setOnClickListener(v -> toggleFavorite());
        actionSubscribe.setOnClickListener(v -> toggleSubscription());
        actionNext.setOnClickListener(v -> playNext());
        actionChannel.setOnClickListener(v -> {
            if (current != null && current.getUploaderUrl() != null) {
                player.pause();
                TvChannelActivity.open(this, current.getServiceId(), current.getUploaderUrl());
            }
        });

        panelBox = findViewById(R.id.panel_box);
        panelScrim = findViewById(R.id.panel_scrim);
        actionsRow = findViewById(R.id.actions_row);
        panelRows = findViewById(R.id.panel_rows);
        panelRows.setLayoutManager(new LinearLayoutManager(this));
        panelRows.setItemAnimator(null);
        panelRows.setAdapter(rowsAdapter);

        // Descendo nas fileiras, o painel cresce até a tela toda e carrega mais fileiras
        panel.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (!panelVisible() || newFocus == null) {
                return;
            }
            final RecyclerView.ViewHolder vh = panelRows.findContainingViewHolder(newFocus);
            if (vh != null) {
                final int pos = vh.getBindingAdapterPosition();
                setPanelExpanded(pos >= 1);
                if (pos >= rowsAdapter.getItemCount() - 2) {
                    loadMoreRows();
                }
            } else {
                setPanelExpanded(false);
            }
        });
    }

    private void setPanelExpanded(final boolean expanded) {
        if (expanded == panelExpanded) {
            return;
        }
        panelExpanded = expanded;
        final float d = getResources().getDisplayMetrics().density;
        final android.widget.FrameLayout.LayoutParams boxLp =
                (android.widget.FrameLayout.LayoutParams) panelBox.getLayoutParams();
        boxLp.height = expanded ? ViewGroup.LayoutParams.MATCH_PARENT
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        panelBox.setLayoutParams(boxLp);
        final android.widget.LinearLayout.LayoutParams rowsLp =
                (android.widget.LinearLayout.LayoutParams) panelRows.getLayoutParams();
        rowsLp.height = expanded ? 0 : (int) (270 * d);
        rowsLp.weight = expanded ? 1 : 0;
        panelRows.setLayoutParams(rowsLp);
        panelBox.setPadding(0, (int) ((expanded ? 24 : 64) * d), 0, 0);
        actionsRow.setVisibility(expanded ? View.GONE : View.VISIBLE);
        overlay.setVisibility(expanded ? View.GONE : View.VISIBLE);
        panelScrim.animate().alpha(expanded ? 1f : 0f).setDuration(200).start();
    }

    /** Primeiras fileiras: A seguir, Mais do canal e Populares. */
    private void buildPanelRows(final StreamInfo info, final int t) {
        final TvAdapters.Row next = new TvAdapters.Row(getString(R.string.tv_related));
        next.adapter.set(TvAdapters.streamsOnly(info.getRelatedItems()));
        next.loading = false;
        rowsAdapter.add(next);
        usedSeeds.add(info.getUrl());

        if (info.getUploaderUrl() != null) {
            final TvAdapters.Row channelRow = new TvAdapters.Row(
                    getString(R.string.tv_more_from, info.getUploaderName()));
            rowsAdapter.add(channelRow);
            Bg.run(() -> TvAdapters.streamsOnly(com.kamarguinho.supertube.data.Feed
                            .channelVideos(org.schabi.newpipe.extractor.channel.ChannelInfo
                                    .getInfo(NewPipe.getService(info.getServiceId()),
                                            info.getUploaderUrl()))),
                    rowCallback(channelRow, t));
        }

        final TvAdapters.Row popular = new TvAdapters.Row(getString(R.string.tv_popular_now));
        rowsAdapter.add(popular);
        final org.schabi.newpipe.extractor.StreamingService yt =
                org.schabi.newpipe.extractor.ServiceList.YouTube;
        Bg.run(() -> TvAdapters.streamsOnly(org.schabi.newpipe.extractor.search.SearchInfo
                        .getInfo(yt, yt.getSearchQHFactory().fromQuery("em alta hoje"))
                        .getRelatedItems()),
                rowCallback(popular, t));
    }

    private Bg.Callback<java.util.List<StreamInfoItem>> rowCallback(final TvAdapters.Row row,
                                                                   final int t) {
        return new Bg.Callback<java.util.List<StreamInfoItem>>() {
            @Override
            public void onResult(final java.util.List<StreamInfoItem> result) {
                if (t != token) {
                    return;
                }
                row.adapter.set(result);
                rowsAdapter.loaded(row);
            }

            @Override
            public void onError(final Throwable error) {
                if (t == token) {
                    rowsAdapter.loaded(row);
                }
            }
        };
    }

    /**
     * Fileiras sem fim: pega um vídeo das fileiras de cima que ainda não foi usado
     * e cria uma fileira "Mais como ..." com os relacionados dele.
     */
    private void loadMoreRows() {
        if (loadingMore || extraRows >= MAX_EXTRA_ROWS) {
            return;
        }
        StreamInfoItem seed = null;
        for (int r = 0; r < rowsAdapter.getItemCount() && seed == null; r++) {
            for (final StreamInfoItem item : rowsAdapter.get(r).adapter.items()) {
                if (!usedSeeds.contains(item.getUrl())) {
                    seed = item;
                    break;
                }
            }
        }
        if (seed == null) {
            return;
        }
        final StreamInfoItem chosen = seed;
        usedSeeds.add(chosen.getUrl());
        loadingMore = true;
        extraRows++;
        final int t = token;
        final TvAdapters.Row row = new TvAdapters.Row(
                getString(R.string.tv_more_like, chosen.getName()));
        rowsAdapter.add(row);
        Bg.run(() -> TvAdapters.streamsOnly(StreamInfo.getInfo(
                        NewPipe.getServiceByUrl(chosen.getUrl()), chosen.getUrl())
                        .getRelatedItems()),
                new Bg.Callback<java.util.List<StreamInfoItem>>() {
                    @Override
                    public void onResult(final java.util.List<StreamInfoItem> result) {
                        loadingMore = false;
                        if (t != token) {
                            return;
                        }
                        row.adapter.set(result);
                        rowsAdapter.loaded(row);
                    }

                    @Override
                    public void onError(final Throwable error) {
                        loadingMore = false;
                        if (t == token) {
                            rowsAdapter.loaded(row);
                        }
                    }
                });
    }

    private static void setAction(final View action, final int icon, final int label) {
        ((ImageView) action.findViewById(R.id.action_icon)).setImageResource(icon);
        ((TextView) action.findViewById(R.id.action_label)).setText(label);
    }

    private void updateActions() {
        final boolean fav = current != null && new Favorites(this).contains(current.getUrl());
        setAction(actionFavorite, fav ? R.drawable.ic_favorite_filled
                : R.drawable.ic_favorite_outline, fav ? R.string.favorited : R.string.favorite);
        final boolean hasChannel = current != null && current.getUploaderUrl() != null;
        actionChannel.setVisibility(hasChannel ? View.VISIBLE : View.GONE);
        actionSubscribe.setVisibility(hasChannel ? View.VISIBLE : View.GONE);
        final boolean sub = hasChannel
                && new Subscriptions(this).isSubscribed(current.getUploaderUrl());
        setAction(actionSubscribe, sub ? R.drawable.ic_subscriptions_filled
                : R.drawable.ic_subscriptions_outline,
                sub ? R.string.unsubscribe : R.string.subscribe);
    }

    private void toggleFavorite() {
        if (current == null) {
            return;
        }
        final Favorites favorites = new Favorites(this);
        if (favorites.contains(current.getUrl())) {
            favorites.remove(current.getUrl());
            Toast.makeText(this, R.string.tv_unfavorited, Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(new Favorites.Video(current.getServiceId(), current.getUrl(),
                    current.getName(), current.getUploaderName(),
                    Images.best(current.getThumbnails())));
            Toast.makeText(this, R.string.tv_favorited, Toast.LENGTH_SHORT).show();
        }
        updateHint();
        updateActions();
    }

    private void toggleSubscription() {
        if (current == null || current.getUploaderUrl() == null) {
            return;
        }
        final Subscriptions subs = new Subscriptions(this);
        if (subs.isSubscribed(current.getUploaderUrl())) {
            subs.remove(current.getUploaderUrl());
        } else {
            subs.add(new Subscriptions.Channel(current.getServiceId(),
                    current.getUploaderUrl(), current.getUploaderName(),
                    Images.best(current.getUploaderAvatars())));
            Toast.makeText(this, R.string.subscribed_toast, Toast.LENGTH_SHORT).show();
        }
        updateActions();
    }

    private void chooseSubtitle() {
        final StreamInfo info = current;
        if (info == null) {
            return;
        }
        Subtitles.showMenu(this, info, currentSubtitle, chosen -> {
            currentSubtitle = chosen;
            final long position = player.getCurrentPosition();
            final int t = token;
            Bg.run(() -> resolver.resolve(info, MAX_HEIGHT, false, chosen),
                    new Bg.Callback<MediaSource>() {
                        @Override
                        public void onResult(final MediaSource source) {
                            if (t != token) {
                                return;
                            }
                            Subtitles.select(player, chosen);
                            player.setMediaSource(source, position);
                            player.prepare();
                            player.play();
                            hidePanel();
                        }

                        @Override
                        public void onError(final Throwable error) {
                            Toast.makeText(TvPlayerActivity.this, Images.errorMessage(error),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private boolean panelVisible() {
        return panel.getVisibility() == View.VISIBLE;
    }

    private void showPanel() {
        playerView.hideController();
        playerView.setUseController(false);
        overlay.setVisibility(View.VISIBLE);
        downHint.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        updateActions();
        actionFavorite.requestFocus();
    }

    private void hidePanel() {
        if (panel == null || !panelVisible()) {
            return;
        }
        setPanelExpanded(false);
        panel.setVisibility(View.GONE);
        overlay.setVisibility(View.GONE);
        playerView.setUseController(true);
        playerView.requestFocus();
    }

    /**
     * Seta para baixo abre o painel (como no YouTube da TV). Com o painel aberto,
     * Voltar ou seta para cima nos botões fecham o painel.
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            final int k = event.getKeyCode();
            if (panelVisible()) {
                if (k == KeyEvent.KEYCODE_BACK) {
                    hidePanel();
                    return true;
                }
                final View focus = getCurrentFocus();
                final boolean onActions = focus == actionFavorite || focus == actionChannel
                        || focus == actionSubscribe || focus == actionNext
                        || focus == actionSubtitles;
                if (k == KeyEvent.KEYCODE_DPAD_UP && onActions) {
                    hidePanel();
                    return true;
                }
            } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && current != null) {
                showPanel();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void saveProgress() {
        if (current != null && player != null && player.getPlaybackState() != Player.STATE_IDLE) {
            History.savePosition(this, current.getUrl(), player.getCurrentPosition());
        }
    }

    @Override
    protected void onStop() {
        saveProgress();
        super.onStop();
        player.pause();
    }

    @Override
    protected void onDestroy() {
        playerView.setPlayer(null);
        player.release();
        super.onDestroy();
    }
}
