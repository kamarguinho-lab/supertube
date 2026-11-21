package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.kamarguinho.supertube.player.PlayQueue;
import com.kamarguinho.supertube.player.PlayerService;
import com.kamarguinho.supertube.util.Bg;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Próximo / anterior de qualquer lugar: botões da tela, notificação, tela de bloqueio,
 * fone Bluetooth e carro. Funciona mesmo com a tela do vídeo fechada.
 */
public final class PlaybackNav {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Deque<String> BACK = new ArrayDeque<>();
    private static WeakReference<PlayerActivity> active = new WeakReference<>(null);

    private PlaybackNav() {
    }

    static void attach(final PlayerActivity activity) {
        active = new WeakReference<>(activity);
    }

    static void detach(final PlayerActivity activity) {
        if (active.get() == activity) {
            active = new WeakReference<>(null);
        }
    }

    /** Guarda o vídeo que estava tocando, para o "anterior". */
    static synchronized void pushBack(final String url) {
        if (url == null || url.equals(BACK.peekFirst())) {
            return;
        }
        BACK.addFirst(url);
        while (BACK.size() > 50) {
            BACK.removeLast();
        }
    }

    private static synchronized String popBack() {
        return BACK.pollFirst();
    }

    /** Toca este vídeo: na tela, se ela estiver aberta; senão, em segundo plano. */
    static void play(final Context context, final String url, final boolean remember) {
        final PlayerActivity a = active.get();
        if (a != null && !a.isFinishing() && !a.isDestroyed()) {
            a.loadFromNav(url, remember);
        } else {
            PlayerActivity.playInBackground(context.getApplicationContext(), url, remember);
        }
    }

    public static void next(final Context context) {
        MAIN.post(() -> {
            final StreamInfo info = PlayerActivity.current();
            if (info == null) {
                return;
            }
            if (PlayQueue.contains(info.getUrl())) {
                Bg.run(() -> PlayQueue.nextAfter(info.getUrl()), new Bg.Callback<String>() {
                    @Override
                    public void onResult(final String next) {
                        if (next != null) {
                            play(context, next, true);
                        } else {
                            playRelated(context, info);
                        }
                    }

                    @Override
                    public void onError(final Throwable error) {
                        playRelated(context, info);
                    }
                });
                return;
            }
            playRelated(context, info);
        });
    }

    private static void playRelated(final Context context, final StreamInfo info) {
        for (final InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                play(context, item.getUrl(), true);
                return;
            }
        }
    }

    public static void previous(final Context context) {
        MAIN.post(() -> {
            final String url = popBack();
            if (url != null) {
                play(context, url, false);
            } else {
                PlayerService.getPlayer(context).seekTo(0);
            }
        });
    }
}
