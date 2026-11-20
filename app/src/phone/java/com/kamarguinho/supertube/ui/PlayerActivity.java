package com.kamarguinho.supertube.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Downloads;
import com.kamarguinho.supertube.data.Favorites;
import com.kamarguinho.supertube.data.History;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.player.MediaResolver;
import com.kamarguinho.supertube.player.AudioTracks;
import com.kamarguinho.supertube.player.PlayerService;
import com.kamarguinho.supertube.player.Quality;
import com.kamarguinho.supertube.player.RelatedPlayer;
import com.kamarguinho.supertube.player.SeekHelper;
import com.kamarguinho.supertube.player.Subtitles;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    /** Resolução escolhida em "Avançado" (vale só para o vídeo atual). */
    private int exactHeight = 0;
    private int playingHeight = 0;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    /** Último vídeo carregado (para voltar à tela sem recarregar). */
    private static StreamInfo currentInfo;
    private static boolean audioOnly = false;
    private static Subtitles.Option currentSubtitle;

    private StyledPlayerView playerView;
    private View playerBox;
    private RelatedPlayer relatedPlayer;
    private SeekHelper seekHelper;
    private ExoPlayer player;
    private View details;
    private ProgressBar progress;
    private TextView title;
    private TextView uploader;
    private TextView meta;
    private TextView uploaderSubs;
    private TextView description;
    private boolean descriptionExpanded = false;
    private ImageView uploaderAvatar;
    private MaterialButton subscribeButton;
    private MaterialButton audioButton;
    private MaterialButton favoriteButton;
    private Favorites favorites;
    private final ItemAdapter related = new ItemAdapter();
    private Subscriptions subscriptions;
    private MediaResolver resolver;
    private int loadToken = 0;

    private final Player.Listener errorListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(final int state) {
            if (state == Player.STATE_ENDED && currentInfo != null) {
                History.savePosition(PlayerActivity.this, currentInfo.getUrl(),
                        currentInfo.getDuration() * 1000);
            }
        }

        @Override
        public void onPlayerError(@NonNull final PlaybackException error) {
            Toast.makeText(PlayerActivity.this,
                    getString(R.string.error_prefix, error.getErrorCodeName()),
                    Toast.LENGTH_LONG).show();
        }
    };

    public static void open(final Context context, final String url) {
        context.startActivity(new Intent(context, PlayerActivity.class)
                .putExtra(EXTRA_URL, url));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        subscriptions = new Subscriptions(this);
        favorites = new Favorites(this);
        resolver = new MediaResolver(this);

        playerView = findViewById(R.id.player_view);
        details = findViewById(R.id.details);
        // Descendo na lista de relacionados, sempre carrega mais (sem fim)
        ((androidx.core.widget.NestedScrollView) details).setOnScrollChangeListener(
                (androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                        (v, x, y, oldX, oldY) -> {
                            final View child = v.getChildAt(0);
                            if (child != null
                                    && y + v.getHeight() >= child.getHeight() - 1500) {
                                loadMoreRelated();
                            }
                        });
        progress = findViewById(R.id.progress);
        title = findViewById(R.id.title);
        uploader = findViewById(R.id.uploader);
        meta = findViewById(R.id.meta);
        uploaderSubs = findViewById(R.id.uploader_subs);
        description = findViewById(R.id.description);
        description.setOnClickListener(v -> toggleDescription());
        title.setOnClickListener(v -> toggleDescription());
        findViewById(R.id.share).setOnClickListener(v -> share());
        findViewById(R.id.subtitles).setOnClickListener(v -> chooseSubtitle());
        findViewById(R.id.quality).setOnClickListener(v -> chooseQuality());
        findViewById(R.id.audio_dub).setOnClickListener(v -> chooseAudio());
        uploaderAvatar = findViewById(R.id.uploader_avatar);
        subscribeButton = findViewById(R.id.subscribe);
        audioButton = findViewById(R.id.audio_only);
        favoriteButton = findViewById(R.id.favorite);
        favoriteButton.setOnClickListener(v -> toggleFavorite());

        final RecyclerView list = findViewById(R.id.related);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(related);

        player = PlayerService.getPlayer(this);
        player.addListener(errorListener);
        playerBox = findViewById(R.id.player_box);
        // Controles como no YouTube: anterior, play/pausa e próximo (vídeo relacionado);
        // toque duplo nas laterais avança/volta 10 segundos
        relatedPlayer = new RelatedPlayer(player, this::playNextRelated);
        playerView.setPlayer(relatedPlayer);
        playerView.setShowRewindButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowPreviousButton(true);
        playerView.setShowNextButton(true);
        seekHelper = new SeekHelper(player, findViewById(R.id.seek_left),
                findViewById(R.id.seek_right));
        setupGestures();
        playerView.setShowSubtitleButton(false);
        Subtitles.applyStyle(playerView.getSubtitleView(), 1f);
        playerView.setFullscreenButtonClickListener(isFullscreen -> setRequestedOrientation(
                isFullscreen ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));

        findViewById(R.id.download).setOnClickListener(v -> download());
        subscribeButton.setOnClickListener(v -> toggleSubscription());
        audioButton.setOnClickListener(v -> toggleAudioOnly());
        findViewById(R.id.uploader_row).setOnClickListener(v -> {
            if (currentInfo != null && currentInfo.getUploaderUrl() != null) {
                ChannelActivity.open(this, currentInfo.getServiceId(),
                        currentInfo.getUploaderUrl());
            }
        });

        applyOrientation(getResources().getConfiguration().orientation);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        String url = intent.getStringExtra(EXTRA_URL);
        if (url == null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            final Uri data = intent.getData();
            url = data != null ? data.toString() : null;
        }
        if (url == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final Matcher m = URL_PATTERN.matcher(text);
                if (m.find()) {
                    url = m.group();
                }
            }
        }

        if (url == null) {
            // Aberto pela notificação: mostra o que já está tocando
            if (currentInfo != null) {
                showInfo(currentInfo);
            } else {
                finish();
            }
            return;
        }
        if (currentInfo != null && url.equals(PlayerService.nowUrl)
                && player.getMediaItemCount() > 0) {
            showInfo(currentInfo);
            return;
        }
        load(url);
    }

    private void load(final String url) {
        saveProgress();
        exactHeight = 0;
        resolver.audioTrackId = null;
        resolver.audioLanguage = AudioTracks.preferred(this);
        final int token = ++loadToken;
        progress.setVisibility(View.VISIBLE);
        title.setText("");
        uploader.setText("");
        meta.setText("");
        uploaderSubs.setText("");
        description.setVisibility(View.GONE);
        related.clear();
        ((androidx.core.widget.NestedScrollView) details).scrollTo(0, 0);
        player.stop();

        Bg.run(() -> {
            final StreamingService service = NewPipe.getServiceByUrl(url);
            final StreamInfo info = StreamInfo.getInfo(service, url);
            final Subtitles.Option sub = Subtitles.pick(info,
                    Subtitles.mode(PlayerActivity.this));
            final MediaSource source = resolver.resolve(info, targetHeight(), audioOnly, sub);
            return new Object[]{info, source, sub};
        }, new Bg.Callback<Object[]>() {
            @Override
            public void onResult(final Object[] r) {
                if (token != loadToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                final StreamInfo info = (StreamInfo) r[0];
                currentInfo = info;
                PlayerService.nowUrl = url;
                PlayerService.nowTitle = info.getName();
                PlayerService.nowUploader = info.getUploaderName();
                PlayerService.nowThumbnail = Images.best(info.getThumbnails());
                showInfo(info);

                currentSubtitle = (Subtitles.Option) r[2];
                Subtitles.select(player, currentSubtitle);
                playingHeight = resolver.lastHeight;
                updateQualityLabel();
                updateAudioLabel();
                // Continua de onde parou e grava no histórico
                final long resume = History.resumePosition(PlayerActivity.this, url);
                History.record(PlayerActivity.this, info);
                if (resume > 0) {
                    player.setMediaSource((MediaSource) r[1], resume);
                } else {
                    player.setMediaSource((MediaSource) r[1]);
                }
                player.prepare();
                player.play();
                PlayerService.start(PlayerActivity.this);
            }

            @Override
            public void onError(final Throwable error) {
                if (token != loadToken) {
                    return;
                }
                progress.setVisibility(View.GONE);
                title.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
            }
        });
    }

    private com.kamarguinho.supertube.data.Pagers.RelatedPager relatedPager;
    private boolean relatedLoading = false;

    private void loadMoreRelated() {
        final com.kamarguinho.supertube.data.Pagers.RelatedPager p = relatedPager;
        if (p == null || relatedLoading || !p.hasMore()) {
            return;
        }
        relatedLoading = true;
        Bg.run(p::next, new Bg.Callback<List<org.schabi.newpipe.extractor.stream.StreamInfoItem>>() {
            @Override
            public void onResult(
                    final List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items) {
                if (p != relatedPager) {
                    return;
                }
                relatedLoading = false;
                related.add(items);
            }

            @Override
            public void onError(final Throwable error) {
                if (p == relatedPager) {
                    relatedLoading = false;
                }
            }
        });
    }

    private void setupGestures() {
        final android.view.GestureDetector detector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull final android.view.MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(
                            @NonNull final android.view.MotionEvent e) {
                        if (playerView.isControllerFullyVisible()) {
                            playerView.hideController();
                        } else {
                            playerView.showController();
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull final android.view.MotionEvent e) {
                        final float x = e.getX();
                        final float w = playerView.getWidth();
                        if (x < w * 0.4f) {
                            seekHelper.seek(-1, 10_000);
                        } else if (x > w * 0.6f) {
                            seekHelper.seek(1, 10_000);
                        } else if (player.isPlaying()) {
                            player.pause();
                        } else {
                            player.play();
                        }
                        return true;
                    }
                });
        playerView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return true;
        });
    }

    /** Botão "próximo" dos controles: toca o primeiro vídeo relacionado. */
    private void playNextRelated() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        for (final org.schabi.newpipe.extractor.InfoItem item : info.getRelatedItems()) {
            if (item instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                load(item.getUrl());
                return;
            }
        }
    }

    private void showInfo(final StreamInfo info) {
        relatedPlayer.setHasNext(!info.getRelatedItems().isEmpty());
        progress.setVisibility(View.GONE);
        title.setText(info.getName());
        uploader.setText(info.getUploaderName());

        final StringBuilder m = new StringBuilder();
        if (info.getViewCount() >= 0) {
            m.append(getString(R.string.views, ItemAdapter.formatCount(info.getViewCount())));
        }
        if (info.getTextualUploadDate() != null) {
            m.append("   ").append(info.getTextualUploadDate());
        }
        if (info.getLikeCount() > 0) {
            m.append("   👍 ").append(ItemAdapter.formatCount(info.getLikeCount()));
        }
        meta.setText(m);
        uploaderSubs.setText(info.getUploaderSubscriberCount() > 0
                ? getString(R.string.subs_count,
                ItemAdapter.formatCount(info.getUploaderSubscriberCount())) : "");

        final Description d = info.getDescription();
        if (d != null && !d.content().trim().isEmpty()) {
            description.setText(d.type() == Description.Type.HTML
                    ? Html.fromHtml(d.content(), Html.FROM_HTML_MODE_COMPACT)
                    : d.content());
            descriptionExpanded = false;
            description.setMaxLines(3);
            description.setMovementMethod(null);
            description.setVisibility(View.VISIBLE);
        } else {
            description.setVisibility(View.GONE);
        }
        Glide.with(this).load(Images.best(info.getUploaderAvatars()))
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.color.thumb_placeholder).into(uploaderAvatar);
        related.set(info.getRelatedItems());
        final List<org.schabi.newpipe.extractor.stream.StreamInfoItem> start =
                new java.util.ArrayList<>();
        for (final org.schabi.newpipe.extractor.InfoItem i : info.getRelatedItems()) {
            if (i instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                start.add((org.schabi.newpipe.extractor.stream.StreamInfoItem) i);
            }
        }
        relatedPager = new com.kamarguinho.supertube.data.Pagers.RelatedPager(start);
        relatedLoading = false;
        updateSubscribeButton();
        updateFavoriteButton();
        audioButton.setText(audioOnly ? R.string.video_mode : R.string.audio_only);
    }

    // ---------- Só áudio ----------

    private void toggleAudioOnly() {
        if (currentInfo == null) {
            return;
        }
        audioOnly = !audioOnly;
        audioButton.setText(audioOnly ? R.string.video_mode : R.string.audio_only);
        final long position = player.getCurrentPosition();
        final StreamInfo info = currentInfo;
        Bg.run(() -> resolver.resolve(info, targetHeight(), audioOnly, currentSubtitle),
                new Bg.Callback<MediaSource>() {
                    @Override
                    public void onResult(final MediaSource source) {
                        player.setMediaSource(source, position);
                        player.prepare();
                        player.play();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        Toast.makeText(PlayerActivity.this, Images.errorMessage(error),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void toggleDescription() {
        if (description.getVisibility() != View.VISIBLE) {
            return;
        }
        descriptionExpanded = !descriptionExpanded;
        description.setMaxLines(descriptionExpanded ? Integer.MAX_VALUE : 3);
        title.setMaxLines(descriptionExpanded ? 6 : 2);
        // Links da descrição só ficam clicáveis com ela aberta
        description.setMovementMethod(descriptionExpanded
                ? LinkMovementMethod.getInstance() : null);
    }

    private void share() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        final Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, info.getName())
                .putExtra(Intent.EXTRA_TEXT, info.getUrl());
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    // ---------- Qualidade ----------

    private int targetHeight() {
        return exactHeight > 0 ? exactHeight
                : Quality.maxHeight(this, Quality.mode(this), false);
    }

    private void updateQualityLabel() {
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.quality))
                .setText(playingHeight > 0 ? getString(R.string.quality) + " · "
                        + playingHeight + "p" : getString(R.string.quality));
    }

    private void chooseQuality() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        Quality.showMenu(this, info, false, playingHeight, exactHeight > 0,
                (height, exact) -> {
                    exactHeight = exact ? height : 0;
                    if (audioOnly) {
                        audioOnly = false;
                        audioButton.setText(R.string.audio_only);
                    }
                    final long position = player.getCurrentPosition();
                    final boolean playing = player.getPlayWhenReady();
                    Bg.run(() -> resolver.resolve(info, targetHeight(), false, currentSubtitle),
                            new Bg.Callback<MediaSource>() {
                                @Override
                                public void onResult(final MediaSource source) {
                                    playingHeight = resolver.lastHeight;
                                    updateQualityLabel();
                                    player.setMediaSource(source, position);
                                    player.prepare();
                                    player.setPlayWhenReady(playing);
                                }

                                @Override
                                public void onError(final Throwable error) {
                                    Toast.makeText(PlayerActivity.this,
                                            Images.errorMessage(error),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                });
    }

    // ---------- Áudio / Dublagem ----------

    private void updateAudioLabel() {
        final AudioTracks.Track t = resolver.lastTrack;
        final boolean many = currentInfo != null && AudioTracks.tracks(currentInfo).size() > 1;
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.audio_dub))
                .setText(many ? getString(R.string.audio_dub_short) + " · "
                        + AudioTracks.shortLabel(t) : getString(R.string.audio_dub_short));
    }

    private void chooseAudio() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        AudioTracks.showMenu(this, info, resolver.lastTrack, track -> {
            resolver.audioTrackId = track.id;
            resolver.audioLanguage = AudioTracks.preferred(this);
            final long position = player.getCurrentPosition();
            final boolean playing = player.getPlayWhenReady();
            Bg.run(() -> resolver.resolve(info, targetHeight(), audioOnly, currentSubtitle),
                    new Bg.Callback<MediaSource>() {
                        @Override
                        public void onResult(final MediaSource source) {
                            updateAudioLabel();
                            player.setMediaSource(source, position);
                            player.prepare();
                            player.setPlayWhenReady(playing);
                        }

                        @Override
                        public void onError(final Throwable error) {
                            Toast.makeText(PlayerActivity.this, Images.errorMessage(error),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    // ---------- Legendas ----------

    private void chooseSubtitle() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        Subtitles.showMenu(this, info, currentSubtitle, chosen -> {
            currentSubtitle = chosen;
            final long position = player.getCurrentPosition();
            Bg.run(() -> resolver.resolve(info, targetHeight(), audioOnly, chosen),
                    new Bg.Callback<MediaSource>() {
                        @Override
                        public void onResult(final MediaSource source) {
                            Subtitles.select(player, chosen);
                            player.setMediaSource(source, position);
                            player.prepare();
                            player.play();
                        }

                        @Override
                        public void onError(final Throwable error) {
                            Toast.makeText(PlayerActivity.this, Images.errorMessage(error),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    // ---------- Favoritos ----------

    private void toggleFavorite() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        if (favorites.contains(info.getUrl())) {
            favorites.remove(info.getUrl());
        } else {
            favorites.add(new Favorites.Video(info.getServiceId(), info.getUrl(),
                    info.getName(), info.getUploaderName(), Images.best(info.getThumbnails())));
        }
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        final StreamInfo info = currentInfo;
        final boolean fav = info != null && favorites.contains(info.getUrl());
        favoriteButton.setText(fav ? R.string.favorited : R.string.favorite);
        favoriteButton.setIconResource(fav ? R.drawable.ic_favorite_filled
                : R.drawable.ic_favorite_outline);
    }

    // ---------- Inscrição ----------

    private void toggleSubscription() {
        final StreamInfo info = currentInfo;
        if (info == null || info.getUploaderUrl() == null) {
            return;
        }
        if (subscriptions.isSubscribed(info.getUploaderUrl())) {
            subscriptions.remove(info.getUploaderUrl());
        } else {
            subscriptions.add(new Subscriptions.Channel(info.getServiceId(),
                    info.getUploaderUrl(), info.getUploaderName(),
                    Images.best(info.getUploaderAvatars())));
            Toast.makeText(this, R.string.subscribed_toast, Toast.LENGTH_SHORT).show();
        }
        updateSubscribeButton();
    }

    private void updateSubscribeButton() {
        final StreamInfo info = currentInfo;
        final boolean has = info != null && info.getUploaderUrl() != null;
        subscribeButton.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            final boolean sub = subscriptions.isSubscribed(info.getUploaderUrl());
            subscribeButton.setText(sub ? R.string.unsubscribe : R.string.subscribe);
            // Branco quando ainda não inscrito, cinza quando já inscrito
            subscribeButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    this, sub ? R.color.chip : R.color.pill_light)));
            subscribeButton.setTextColor(ContextCompat.getColor(this,
                    sub ? R.color.text_primary : R.color.on_pill_light));
        }
    }

    // ---------- Download ----------

    private void download() {
        final StreamInfo info = currentInfo;
        if (info == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            return;
        }
        final List<Downloads.Option> options = Downloads.options(info);
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.no_download, Toast.LENGTH_LONG).show();
            return;
        }
        final String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = options.get(i).label;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download)
                .setItems(labels, (d, which) -> {
                    try {
                        Downloads.enqueue(this, info, options.get(which).stream);
                        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT)
                                .show();
                    } catch (final Exception e) {
                        Toast.makeText(this, Images.errorMessage(e), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            download();
        }
    }

    // ---------- Tela cheia ao girar ----------

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation(newConfig.orientation);
    }

    private void applyOrientation(final int orientation) {
        final boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        final WindowInsetsControllerCompat insets =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        final ViewGroup.LayoutParams lp = playerBox.getLayoutParams();
        if (landscape) {
            details.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            insets.hide(WindowInsetsCompat.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            details.setVisibility(View.VISIBLE);
            final int width = getResources().getDisplayMetrics().widthPixels;
            lp.height = width * 9 / 16;
            insets.show(WindowInsetsCompat.Type.systemBars());
        }
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).weight = landscape ? 1 : 0;
        }
        playerBox.setLayoutParams(lp);
    }

    private void saveProgress() {
        if (currentInfo != null && player.getPlaybackState() != Player.STATE_IDLE) {
            History.savePosition(this, currentInfo.getUrl(), player.getCurrentPosition());
        }
    }

    @Override
    protected void onPause() {
        saveProgress();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // O vídeo continua tocando em segundo plano pelo PlayerService
        player.removeListener(errorListener);
        playerView.setPlayer(null);
        super.onDestroy();
    }
}
