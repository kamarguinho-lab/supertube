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
                        overlay.setVisibility(visibility));
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
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            playNext();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
