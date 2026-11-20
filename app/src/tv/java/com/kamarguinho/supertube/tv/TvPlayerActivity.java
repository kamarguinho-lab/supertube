package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
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
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.player.MediaResolver;
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
    private RecyclerView relatedList;
    private final TvAdapters.VideoAdapter related = new TvAdapters.VideoAdapter(false);

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
        final int t = ++token;
        hidePanel();
        related.set(new java.util.ArrayList<>());
        progress.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        titleView.setText("");
        subView.setText("");
        player.stop();
        Bg.run(() -> {
            final StreamInfo info = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url);
            return new Object[]{info, resolver.resolve(info, MAX_HEIGHT, false)};
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
                related.set(TvAdapters.streamsOnly(current.getRelatedItems()));
                relatedList.scrollToPosition(0);
                player.setMediaSource((MediaSource) r[1]);
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

        relatedList = findViewById(R.id.related);
        relatedList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        relatedList.setItemAnimator(null);
        relatedList.setAdapter(related);
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
                        || focus == actionSubscribe || focus == actionNext;
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

    @Override
    protected void onStop() {
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
