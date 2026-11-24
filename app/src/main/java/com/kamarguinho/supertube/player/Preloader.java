package com.kamarguinho.supertube.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.kamarguinho.supertube.util.Bg;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Quando a música/vídeo chega em 30%, já carrega o próximo (da playlist ou o primeiro
 * relacionado). Assim, ao apertar "próximo", ele começa na hora.
 */
public final class Preloader {

    /** Em que ponto do vídeo atual o próximo começa a ser carregado. */
    private static final double AT = 0.30;
    /** Os links do YouTube vencem em ~6 h; depois de 3 h carrega de novo. */
    private static final long MAX_AGE_MS = 3L * 60 * 60 * 1000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String preparedFor;
    private static String nextUrl;
    private static StreamInfo nextInfo;
    private static long nextAt;

    private Preloader() {
    }

    /** Fica de olho no player (a cada 2 s) enquanto ele existir. */
    public static void watch(final ExoPlayer player, final Supplier<StreamInfo> current,
                             final BooleanSupplier alive) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive.getAsBoolean()) {
                    return;
                }
                try {
                    check(current.get(), player.getCurrentPosition(), player.getDuration());
                } catch (final IllegalStateException released) {
                    return; // player liberado
                }
                MAIN.postDelayed(this, 2000);
            }
        }, 2000);
    }

    /** Pode ser chamado a qualquer momento com a posição atual. */
    public static void check(final StreamInfo info, final long position, final long duration) {
        if (info == null || duration == C.TIME_UNSET || duration <= 0
                || position < duration * AT) {
            return;
        }
        final String url = info.getUrl();
        synchronized (Preloader.class) {
            if (url.equals(preparedFor)) {
                return;
            }
            preparedFor = url;
        }
        Bg.executor().execute(() -> {
            try {
                final String next = nextUrlFor(info);
                if (next == null) {
                    return;
                }
                final StreamInfo ni = StreamInfo.getInfo(NewPipe.getServiceByUrl(next), next);
                synchronized (Preloader.class) {
                    if (url.equals(preparedFor)) {
                        nextUrl = next;
                        nextInfo = ni;
                        nextAt = SystemClock.elapsedRealtime();
                    }
                }
            } catch (final Throwable e) {
                synchronized (Preloader.class) {
                    if (url.equals(preparedFor)) {
                        preparedFor = null; // tenta de novo depois
                    }
                }
            }
        });
    }

    /** O próximo, do mesmo jeito que o botão "próximo" escolhe. */
    private static String nextUrlFor(final StreamInfo info) throws Exception {
        if (PlayQueue.contains(info.getUrl())) {
            final String n = PlayQueue.nextAfter(info.getUrl());
            if (n != null) {
                return n;
            }
        }
        for (final InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                return item.getUrl();
            }
        }
        return null;
    }

    /** Dados do vídeo: já prontos se foram carregados antes; senão busca agora. */
    public static StreamInfo get(final String url) throws Exception {
        synchronized (Preloader.class) {
            if (url.equals(nextUrl) && nextInfo != null
                    && SystemClock.elapsedRealtime() - nextAt < MAX_AGE_MS) {
                final StreamInfo ready = nextInfo;
                nextInfo = null;
                nextUrl = null;
                return ready;
            }
        }
        return StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url);
    }
}
