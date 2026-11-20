package com.kamarguinho.supertube.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.kamarguinho.supertube.App;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.ui.PlayerActivity;

/**
 * Serviço que mantém o vídeo/áudio tocando com a tela desligada ou com o app em segundo plano,
 * com controles na notificação e na tela de bloqueio.
 */
public class PlayerService extends Service {

    private static final int NOTIFICATION_ID = 1001;

    private static ExoPlayer player;

    /** O que está tocando agora (mostrado na notificação). */
    public static String nowTitle = "";
    public static String nowUploader = "";
    public static String nowThumbnail = null;
    public static String nowUrl = null;

    private MediaSessionCompat session;
    private MediaSessionConnector connector;
    private PlayerNotificationManager notificationManager;
    private boolean foreground = false;

    /** Um único player para o app inteiro: a tela e o serviço usam o mesmo. */
    public static synchronized ExoPlayer getPlayer(final Context context) {
        if (player == null) {
            player = new ExoPlayer.Builder(context.getApplicationContext())
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(), true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .build();
        }
        return player;
    }

    public static void start(final Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, PlayerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final ExoPlayer p = getPlayer(this);

        session = new MediaSessionCompat(this, "SuperTube");
        session.setActive(true);
        connector = new MediaSessionConnector(session);
        connector.setPlayer(p);

        notificationManager = new PlayerNotificationManager.Builder(this, NOTIFICATION_ID,
                App.CHANNEL_PLAYER)
                .setMediaDescriptionAdapter(new DescriptionAdapter())
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationPosted(final int notificationId,
                                                     @NonNull final Notification notification,
                                                     final boolean ongoing) {
                        // Fica em primeiro plano mesmo pausado, para o Android não
                        // fechar o player. Para encerrar, use o botão "parar".
                        goForeground(notification);
                    }

                    @Override
                    public void onNotificationCancelled(final int notificationId,
                                                        final boolean dismissedByUser) {
                        stopSelf();
                    }
                })
                .build();
        notificationManager.setSmallIcon(R.drawable.ic_notification);
        notificationManager.setUseNextAction(false);
        notificationManager.setUsePreviousAction(false);
        notificationManager.setUseFastForwardAction(true);
        notificationManager.setUseRewindAction(true);
        notificationManager.setUseStopAction(true);
        notificationManager.setMediaSessionToken(session.getSessionToken());
        notificationManager.setPlayer(p);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        // Android exige uma notificação em até 5 segundos depois de iniciar o serviço
        if (!foreground) {
            goForeground(new NotificationCompat.Builder(this, App.CHANNEL_PLAYER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(nowTitle)
                    .setContentText(nowUploader)
                    .setSilent(true)
                    .build());
        }
        return START_NOT_STICKY;
    }

    private void goForeground(final Notification notification) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        foreground = true;
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        // Se fechar o app pela lista de recentes com o vídeo pausado, encerra tudo
        if (player != null && !player.getPlayWhenReady()) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        notificationManager.setPlayer(null);
        connector.setPlayer(null);
        session.setActive(false);
        session.release();
        if (player != null) {
            player.stop();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    private class DescriptionAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {
        private Bitmap bitmap;
        private String bitmapUrl;

        @NonNull
        @Override
        public CharSequence getCurrentContentTitle(@NonNull final Player p) {
            return nowTitle;
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(@NonNull final Player p) {
            final Intent intent = new Intent(PlayerService.this, PlayerActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return PendingIntent.getActivity(PlayerService.this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        @Nullable
        @Override
        public CharSequence getCurrentContentText(@NonNull final Player p) {
            return nowUploader;
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(@NonNull final Player p,
                                          @NonNull final PlayerNotificationManager.BitmapCallback
                                                  callback) {
            final String url = nowThumbnail;
            if (url == null) {
                return null;
            }
            if (url.equals(bitmapUrl) && bitmap != null) {
                return bitmap;
            }
            Glide.with(PlayerService.this).asBitmap().load(url).into(
                    new CustomTarget<Bitmap>(512, 288) {
                        @Override
                        public void onResourceReady(@NonNull final Bitmap resource,
                                                    @Nullable final Transition<? super Bitmap> t) {
                            bitmap = resource;
                            bitmapUrl = url;
                            callback.onBitmap(resource);
                        }

                        @Override
                        public void onLoadCleared(@Nullable final Drawable placeholder) {
                        }
                    });
            return null;
        }
    }
}
