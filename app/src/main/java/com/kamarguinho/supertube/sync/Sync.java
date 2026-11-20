package com.kamarguinho.supertube.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.kamarguinho.supertube.DownloaderImpl;
import com.kamarguinho.supertube.data.History;
import com.kamarguinho.supertube.util.Bg;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sincroniza playlists, favoritos, inscrições e histórico entre os aparelhos da mesma conta.
 * Cada lista guarda quando mudou; na sincronização, vale a versão mais nova.
 */
public final class Sync {

    public static final String PLAYLISTS = "playlists";
    public static final String FAVORITES = "favorites";
    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String HISTORY = "history";

    /** Coleção → {arquivo de preferências, chave}. */
    private static final String[][] COLLECTIONS = {
            {PLAYLISTS, "playlists", "lists"},
            {FAVORITES, "favorites", "videos"},
            {SUBSCRIPTIONS, "subscriptions", "channels"},
            {HISTORY, "history", "entries"},
    };

    private static final String PREFS = "sync";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> PENDING = new HashMap<>();

    /** Avisado quando dados chegam de outro aparelho. */
    public interface Listener {
        void onSynced();
    }

    private static Listener listener;
    private static boolean applying = false;

    private Sync() {
    }

    public static void setListener(final Listener l) {
        listener = l;
    }

    private static SharedPreferences prefs(final Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static long lastSync(final Context c) {
        return prefs(c).getLong("last", 0);
    }

    /** Chamado sempre que uma lista muda neste aparelho: envia em alguns segundos. */
    public static void changed(final Context context, final String collection) {
        if (applying) {
            return;
        }
        final Context c = context.getApplicationContext();
        prefs(c).edit().putLong(collection + "_updated", System.currentTimeMillis()).apply();
        if (!Account.configured() || !Account.loggedIn(c)) {
            return;
        }
        MAIN.post(() -> {
            final Runnable old = PENDING.remove(collection);
            if (old != null) {
                MAIN.removeCallbacks(old);
            }
            final Runnable push = () -> {
                PENDING.remove(collection);
                Bg.run(() -> {
                    push(c, collection);
                    return true;
                }, new Bg.Callback<Boolean>() {
                    @Override
                    public void onResult(final Boolean ok) {
                    }

                    @Override
                    public void onError(final Throwable error) {
                    }
                });
            };
            PENDING.put(collection, push);
            MAIN.postDelayed(push, 3000);
        });
    }

    /** Sincroniza tudo agora (baixa o que for mais novo na nuvem, envia o que for mais novo aqui). */
    public static void syncAll(final Context context, final Bg.Callback<Integer> callback) {
        final Context c = context.getApplicationContext();
        if (!Account.configured() || !Account.loggedIn(c)) {
            if (callback != null) {
                callback.onResult(0);
            }
            return;
        }
        Bg.run(() -> {
            int received = 0;
            for (final String[] col : COLLECTIONS) {
                received += syncOne(c, col) ? 1 : 0;
            }
            prefs(c).edit().putLong("last", System.currentTimeMillis()).apply();
            return received;
        }, new Bg.Callback<Integer>() {
            @Override
            public void onResult(final Integer received) {
                if (received > 0 && listener != null) {
                    listener.onSynced();
                }
                if (callback != null) {
                    callback.onResult(received);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    /** Retorna true se recebeu dados novos da nuvem. */
    private static boolean syncOne(final Context c, final String[] col) throws Exception {
        final String url = url(c, col[0]);
        final JSONObject remote;
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(new Request.Builder().url(url).build()).execute()) {
            final String body = response.body() != null ? response.body().string() : "null";
            if (!response.isSuccessful()) {
                throw new IOException("Sincronização falhou: HTTP " + response.code());
            }
            remote = "null".equals(body.trim()) ? null : new JSONObject(body);
        }
        final long localUpdated = prefs(c).getLong(col[0] + "_updated", 0);
        final long remoteUpdated = remote == null ? 0 : remote.optLong("updatedAt", 0);

        if (remote != null && remoteUpdated > localUpdated) {
            apply(c, col, remote.optString("data", "[]"), remoteUpdated);
            return true;
        }
        if (localUpdated > remoteUpdated || remote == null) {
            push(c, col[0]);
        }
        return false;
    }

    private static void apply(final Context c, final String[] col, final String data,
                              final long updatedAt) {
        applying = true;
        try {
            c.getSharedPreferences(col[1], Context.MODE_PRIVATE).edit()
                    .putString(col[2], data).commit();
            prefs(c).edit().putLong(col[0] + "_updated", updatedAt).apply();
            if (HISTORY.equals(col[0])) {
                History.invalidate();
            }
        } finally {
            applying = false;
        }
    }

    private static void push(final Context c, final String collection) throws Exception {
        String[] col = null;
        for (final String[] x : COLLECTIONS) {
            if (x[0].equals(collection)) {
                col = x;
            }
        }
        if (col == null) {
            return;
        }
        long updated = prefs(c).getLong(collection + "_updated", 0);
        if (updated == 0) {
            updated = System.currentTimeMillis();
            prefs(c).edit().putLong(collection + "_updated", updated).apply();
        }
        final String data = c.getSharedPreferences(col[1], Context.MODE_PRIVATE)
                .getString(col[2], "[]");
        final Request request = new Request.Builder().url(url(c, collection))
                .put(RequestBody.create(new JSONObject().put("updatedAt", updated)
                        .put("data", data).toString(), JSON))
                .build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Envio falhou: HTTP " + response.code());
            }
        }
    }

    private static String url(final Context c, final String collection) throws Exception {
        final String token = Account.idToken(c);
        return Account.dbUrl() + "/users/" + Account.uid(c) + "/" + collection
                + ".json?auth=" + token;
    }
}
