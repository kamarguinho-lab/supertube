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
import com.kamarguinho.supertube.player.AudioTracks;
import com.kamarguinho.supertube.player.MediaResolver;
import com.kamarguinho.supertube.player.Quality;
import com.kamarguinho.supertube.player.RelatedPlayer;
import com.kamarguinho.supertube.player.SeekHelper;
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
    private int exactHeight = 0;
    private int playingHeight = 0;

    private ExoPlayer player;
    private RelatedPlayer relatedPlayer;
    private SeekHelper seekHelper;
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
    private View actionQuality;
    private View actionAudio;
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
    private static final int MAX_EXTRA_ROWS = 40;

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
        // Controles como no YouTube da TV: play/pausa no centro e barra vermelha;
        // setas esquerda/direita voltam/avançam 10 s (segurando, acelera)
        relatedPlayer = new RelatedPlayer(player, this::playNext);
        playerView.setPlayer(relatedPlayer);
        playerView.setShowRewindButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowPreviousButton(true);
        playerView.setShowNextButton(true);
        seekHelper = new SeekHelper(player, findViewById(R.id.seek_left),
                findViewById(R.id.seek_right));
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
        exactHeight = 0;
        resolver.audioTrackId = null;
        resolver.audioLanguage = AudioTracks.preferred(this);
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
            return new Object[]{info, resolver.resolve(info, targetHeight(), false, sub), sub};
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
                relatedPlayer.setHasNext(!current.getRelatedItems().isEmpty());
                currentSubtitle = (Subtitles.Option) r[2];
                Subtitles.select(player, currentSubtitle);
                playingHeight = resolver.lastHeight;
                updateQualityLabel();
                updateAudioLabel();
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

    /**
     * Próximo vídeo: o próximo episódio quando está assistindo uma série;
     * senão, o primeiro relacionado (como no YouTube da TV).
     */
    private void playNext() {
        if (current == null) {
            return;
        }
        final StreamInfo now = current;
        if (com.kamarguinho.supertube.player.PlayQueue.contains(now.getUrl())) {
            Bg.run(() -> com.kamarguinho.supertube.player.PlayQueue.nextAfter(now.getUrl()),
                    new Bg.Callback<String>() {
                        @Override
                        public void onResult(final String next) {
                            if (next != null) {
                                load(next);
                            } else {
                                playRelated(now);
                            }
                        }

                        @Override
                        public void onError(final Throwable error) {
                            playRelated(now);
                        }
                    });
            return;
        }
        playRelated(now);
    }

    private void playRelated(final StreamInfo now) {
        if (now != current) {
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
        final View actionSave = findViewById(R.id.action_save);
        setAction(actionSave, R.drawable.ic_playlist_add, R.string.playlist_save);
        TvAdapters.focusZoom(actionSave, 1.06f);
        actionSave.setOnClickListener(v -> {
            if (current != null) {
                com.kamarguinho.supertube.data.PlaylistUi.saveTo(this,
                        new Favorites.Video(current.getServiceId(), current.getUrl(),
                                current.getName(), current.getUploaderName(),
                                Images.best(current.getThumbnails())), null);
            }
        });
        actionSubscribe = findViewById(R.id.action_subscribe);
        actionNext = findViewById(R.id.action_next);
        actionSubtitles = findViewById(R.id.action_subtitles);
        setAction(actionSubtitles, R.drawable.ic_subtitles, R.string.subtitles);
        TvAdapters.focusZoom(actionSubtitles, 1.06f);
        actionSubtitles.setOnClickListener(v -> chooseSubtitle());
        actionQuality = findViewById(R.id.action_quality);
        setAction(actionQuality, R.drawable.ic_settings, R.string.quality);
        TvAdapters.focusZoom(actionQuality, 1.06f);
        actionQuality.setOnClickListener(v -> chooseQuality());
        actionAudio = findViewById(R.id.action_audio);
        setAction(actionAudio, R.drawable.ic_dub, R.string.audio_dub_short);
        TvAdapters.focusZoom(actionAudio, 1.06f);
        actionAudio.setOnClickListener(v -> chooseAudio());

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
        TvAdapters.endless(panelRows, this::loadMoreRows);

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

    /** Primeiras fileiras: A seguir, Mais do canal e Populares (cada uma sem fim para o lado). */
    private void buildPanelRows(final StreamInfo info, final int t) {
        usedSeeds.add(info.getUrl());
        rowsAdapter.start(new TvAdapters.Row(getString(R.string.tv_related),
                com.kamarguinho.supertube.data.Pagers.startingWith(
                        TvAdapters.streamsOnly(info.getRelatedItems()))));
        if (info.getUploaderUrl() != null) {
            rowsAdapter.start(new TvAdapters.Row(
                    getString(R.string.tv_more_from, info.getUploaderName()),
                    new com.kamarguinho.supertube.data.Pagers.ChannelUrlPager(
                            info.getServiceId(), info.getUploaderUrl())));
        }
        rowsAdapter.start(new TvAdapters.Row(getString(R.string.tv_popular_now),
                new com.kamarguinho.supertube.data.Pagers.TrendingPager()));
    }

    /**
     * Fileiras sem fim para baixo: pega um vídeo das fileiras de cima que ainda não foi
     * usado e cria uma fileira "Mais como ..." (que também não tem fim para o lado).
     */
    private void loadMoreRows() {
        final int n = rowsAdapter.getItemCount();
        if (n == 0 || extraRows >= MAX_EXTRA_ROWS || rowsAdapter.get(n - 1).loading) {
            return;
        }
        StreamInfoItem seed = null;
        for (int r = 0; r < n && seed == null; r++) {
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
        usedSeeds.add(seed.getUrl());
        extraRows++;
        rowsAdapter.start(new TvAdapters.Row(getString(R.string.tv_more_like, seed.getName()),
                new com.kamarguinho.supertube.data.Pagers.RelatedPager(
                        java.util.Collections.singletonList(seed))));
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

    private int targetHeight() {
        return exactHeight > 0 ? exactHeight
                : Quality.maxHeight(this, Quality.mode(this), true);
    }

    private void updateQualityLabel() {
        ((TextView) actionQuality.findViewById(R.id.action_label)).setText(
                playingHeight > 0 ? getString(R.string.quality) + " · " + playingHeight + "p"
                        : getString(R.string.quality));
    }

    private void updateAudioLabel() {
        final boolean many = current != null && AudioTracks.tracks(current).size() > 1;
        ((TextView) actionAudio.findViewById(R.id.action_label)).setText(many
                ? getString(R.string.audio_dub_short) + " · "
                + AudioTracks.shortLabel(resolver.lastTrack)
                : getString(R.string.audio_dub_short));
    }

    private void chooseAudio() {
        final StreamInfo info = current;
        if (info == null) {
            return;
        }
        AudioTracks.showMenu(this, info, resolver.lastTrack, track -> {
            resolver.audioTrackId = track.id;
            resolver.audioLanguage = AudioTracks.preferred(this);
            final long position = player.getCurrentPosition();
            final int t = token;
            Bg.run(() -> resolver.resolve(info, targetHeight(), false, currentSubtitle),
                    new Bg.Callback<MediaSource>() {
                        @Override
                        public void onResult(final MediaSource source) {
                            if (t != token) {
                                return;
                            }
                            updateAudioLabel();
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

    private void chooseQuality() {
        final StreamInfo info = current;
        if (info == null) {
            return;
        }
        Quality.showMenu(this, info, true, playingHeight, exactHeight > 0, (height, exact) -> {
            exactHeight = exact ? height : 0;
            final long position = player.getCurrentPosition();
            final int t = token;
            Bg.run(() -> resolver.resolve(info, targetHeight(), false, currentSubtitle),
                    new Bg.Callback<MediaSource>() {
                        @Override
                        public void onResult(final MediaSource source) {
                            if (t != token) {
                                return;
                            }
                            playingHeight = resolver.lastHeight;
                            updateQualityLabel();
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

    private void chooseSubtitle() {
        final StreamInfo info = current;
        if (info == null) {
            return;
        }
        Subtitles.showMenu(this, info, currentSubtitle, chosen -> {
            currentSubtitle = chosen;
            final long position = player.getCurrentPosition();
            final int t = token;
            Bg.run(() -> resolver.resolve(info, targetHeight(), false, chosen),
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
                        || (focus != null && focus.getId() == R.id.action_save)
                        || focus == actionSubscribe || focus == actionNext
                        || focus == actionSubtitles || focus == actionQuality
                        || focus == actionAudio;
                if (k == KeyEvent.KEYCODE_DPAD_UP && onActions) {
                    hidePanel();
                    return true;
                }
            } else if (k == KeyEvent.KEYCODE_DPAD_DOWN && current != null) {
                showPanel();
                return true;
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                    || k == KeyEvent.KEYCODE_MEDIA_REWIND
                    || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                // Segurando a seta, pula cada vez mais (10 s, 15 s, 20 s... até 60 s)
                final int dir = (k == KeyEvent.KEYCODE_DPAD_LEFT
                        || k == KeyEvent.KEYCODE_MEDIA_REWIND) ? -1 : 1;
                final long step = Math.min(60_000, 10_000 + event.getRepeatCount() * 5_000L);
                seekHelper.seek(dir, step);
                playerView.showController();
                return true;
            } else if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)
                    && !playerView.isControllerFullyVisible()) {
                // OK pausa/continua direto, como no YouTube da TV
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                playerView.showController();
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
