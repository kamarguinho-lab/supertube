package com.kamarguinho.supertube.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Roda tarefas de rede fora da thread principal e entrega o resultado na tela. */
public final class Bg {

    public interface Callback<T> {
        void onResult(T result);

        void onError(Throwable error);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {
    }

    public static <T> void run(final Callable<T> task, final Callback<T> callback) {
        EXECUTOR.execute(() -> {
            try {
                final T result = task.call();
                MAIN.post(() -> callback.onResult(result));
            } catch (final Throwable t) {
                MAIN.post(() -> callback.onError(t));
            }
        });
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }
}
