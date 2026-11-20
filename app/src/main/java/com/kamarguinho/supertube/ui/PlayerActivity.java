package com.kamarguinho.supertube.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
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
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.player.MediaResolver;
import com.kamarguinho.supertube.player.PlayerService;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final int MAX_HEIGHT = 1080;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    /** Último vídeo carregado (para voltar à tela sem recarregar). */
    private static StreamInfo currentInfo;
    private static boolean audioOnly = false;

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private View details;
    private ProgressBar progress;
    private TextView title;
    private TextView uploader;
    private ImageView uploaderAvatar;
    private MaterialButton subscribeButton;
    private MaterialButton audioButton;
    private final ItemAdapter related = new ItemAdapter();
    private Subscriptions subscriptions;
    private MediaResolver resolver;
    private int loadToken = 0;

    private final Player.Listener errorListener = new Player.Listener() {
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
        resolver = new MediaResolver(this);

        playerView = findViewById(R.id.player_view);
        details = findViewById(R.id.details);
        progress = findViewById(R.id.progress);
        title = findViewById(R.id.title);
        uploader = findViewById(R.id.uploader);
        uploaderAvatar = findViewById(R.id.uploader_avatar);
        subscribeButton = findViewById(R.id.subscribe);
        audioButton = findViewById(R.id.audio_only);

        final RecyclerView list = findViewById(R.id.related);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(related);

        player = PlayerService.getPlayer(this);
        player.addListener(errorListener);
        playerView.setPlayer(player);
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
        final int token = ++loadToken;
        progress.setVisibility(View.VISIBLE);
        title.setText("");
        uploader.setText("");
        related.clear();
        player.stop();

        Bg.run(() -> {
            final StreamingService service = NewPipe.getServiceByUrl(url);
            final StreamInfo info = StreamInfo.getInfo(service, url);
            final MediaSource source = resolver.resolve(info, MAX_HEIGHT, audioOnly);
            return new Object[]{info, source};
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

                player.setMediaSource((MediaSource) r[1]);
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

    private void showInfo(final StreamInfo info) {
        progress.setVisibility(View.GONE);
        title.setText(info.getName());
        uploader.setText(info.getUploaderName());
        Glide.with(this).load(Images.best(info.getUploaderAvatars()))
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.color.thumb_placeholder).into(uploaderAvatar);
        related.set(info.getRelatedItems());
        updateSubscribeButton();
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
        Bg.run(() -> resolver.resolve(info, MAX_HEIGHT, audioOnly),
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
            subscribeButton.setText(subscriptions.isSubscribed(info.getUploaderUrl())
                    ? R.string.unsubscribe : R.string.subscribe);
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
        final ViewGroup.LayoutParams lp = playerView.getLayoutParams();
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
        playerView.setLayoutParams(lp);
    }

    @Override
    protected void onDestroy() {
        // O vídeo continua tocando em segundo plano pelo PlayerService
        player.removeListener(errorListener);
        playerView.setPlayer(null);
        super.onDestroy();
    }
}
