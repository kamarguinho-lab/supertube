package com.kamarguinho.supertube.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.kamarguinho.supertube.BuildConfig;
import com.kamarguinho.supertube.DownloaderImpl;

import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Conta SuperTube (Firebase Authentication pela API REST, sem bibliotecas do Google).
 * No celular: e-mail e senha. Na TV: entra com um código de 6 números aprovado no celular.
 */
public final class Account {

    private static final String PREFS = "account";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String AUTH = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String TOKEN = "https://securetoken.googleapis.com/v1/token?key=";

    private static String idToken;
    private static long idTokenExpires;

    private Account() {
    }

    /** O app foi configurado com um projeto Firebase? */
    public static boolean configured() {
        return !BuildConfig.FIREBASE_API_KEY.isEmpty() && !BuildConfig.FIREBASE_DB_URL.isEmpty();
    }

    static String dbUrl() {
        final String u = BuildConfig.FIREBASE_DB_URL;
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static SharedPreferences prefs(final Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean loggedIn(final Context c) {
        return prefs(c).getString("refresh", null) != null;
    }

    @Nullable
    public static String email(final Context c) {
        return prefs(c).getString("email", null);
    }

    @Nullable
    static String uid(final Context c) {
        return prefs(c).getString("uid", null);
    }

    // ================= E-mail e senha =================

    public static void signIn(final Context c, final String email, final String password)
            throws Exception {
        final JSONObject r = post(AUTH + "signInWithPassword?key=" + BuildConfig.FIREBASE_API_KEY,
                new JSONObject().put("email", email.trim()).put("password", password)
                        .put("returnSecureToken", true));
        saveSession(c, r.getString("refreshToken"), r.getString("localId"),
                r.optString("email", email), r.getString("idToken"), r.optLong("expiresIn", 3600));
    }

    public static void signUp(final Context c, final String email, final String password)
            throws Exception {
        final JSONObject r = post(AUTH + "signUp?key=" + BuildConfig.FIREBASE_API_KEY,
                new JSONObject().put("email", email.trim()).put("password", password)
                        .put("returnSecureToken", true));
        saveSession(c, r.getString("refreshToken"), r.getString("localId"),
                r.optString("email", email), r.getString("idToken"), r.optLong("expiresIn", 3600));
    }

    public static void resetPassword(final String email) throws Exception {
        post(AUTH + "sendOobCode?key=" + BuildConfig.FIREBASE_API_KEY,
                new JSONObject().put("requestType", "PASSWORD_RESET").put("email", email.trim()));
    }

    public static synchronized void signOut(final Context c) {
        prefs(c).edit().clear().apply();
        idToken = null;
        idTokenExpires = 0;
    }

    private static synchronized void saveSession(final Context c, final String refresh,
                                                 final String uid, final String email,
                                                 final String token, final long expiresIn) {
        prefs(c).edit().putString("refresh", refresh).putString("uid", uid)
                .putString("email", email).apply();
        idToken = token;
        idTokenExpires = System.currentTimeMillis() + (expiresIn - 120) * 1000;
    }

    /** Token válido para acessar o banco (renova sozinho a cada hora). */
    static synchronized String idToken(final Context c) throws Exception {
        if (idToken != null && System.currentTimeMillis() < idTokenExpires) {
            return idToken;
        }
        final String refresh = prefs(c).getString("refresh", null);
        if (refresh == null) {
            throw new IOException(com.kamarguinho.supertube.util.Lang.t("Não conectado"));
        }
        final JSONObject r = refresh(refresh);
        prefs(c).edit().putString("refresh", r.getString("refresh_token"))
                .putString("uid", r.getString("user_id")).apply();
        idToken = r.getString("id_token");
        idTokenExpires = System.currentTimeMillis() + (r.optLong("expires_in", 3600) - 120) * 1000;
        return idToken;
    }

    private static JSONObject refresh(final String refreshToken) throws Exception {
        final Request request = new Request.Builder()
                .url(TOKEN + BuildConfig.FIREBASE_API_KEY)
                .post(new FormBody.Builder().add("grant_type", "refresh_token")
                        .add("refresh_token", refreshToken).build())
                .build();
        return execute(request);
    }

    // ================= TV: entrar com código =================

    /** Sessão anônima só da TV, usada enquanto espera a aprovação do celular. */
    private static String[] anonymous(final Context c) throws Exception {
        final SharedPreferences p = prefs(c);
        String refresh = p.getString("anon_refresh", null);
        if (refresh == null) {
            final JSONObject r = post(AUTH + "signUp?key=" + BuildConfig.FIREBASE_API_KEY,
                    new JSONObject().put("returnSecureToken", true));
            refresh = r.getString("refreshToken");
            p.edit().putString("anon_refresh", refresh).putString("anon_uid",
                    r.getString("localId")).apply();
            return new String[]{r.getString("idToken"), r.getString("localId")};
        }
        final JSONObject r = refresh(refresh);
        p.edit().putString("anon_refresh", r.getString("refresh_token")).apply();
        return new String[]{r.getString("id_token"), r.getString("user_id")};
    }

    /** TV: cria um código de 6 números e espera o celular aprovar. */
    public static String startPairing(final Context c) throws Exception {
        final String[] anon = anonymous(c);
        final SecureRandom random = new SecureRandom();
        for (int tries = 0; tries < 5; tries++) {
            final String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
            final Request request = new Request.Builder()
                    .url(dbUrl() + "/pairings/" + code + ".json?auth=" + anon[0])
                    .put(RequestBody.create(new JSONObject().put("tv", anon[1])
                            .put("created", System.currentTimeMillis()).toString(), JSON))
                    .build();
            try (Response response = DownloaderImpl.getInstance().getClient()
                    .newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return code;
                }
            }
        }
        throw new IOException(com.kamarguinho.supertube.util.Lang.t("Não foi possível criar o código. Tente de novo."));
    }

    /** TV: o celular já aprovou? Se sim, a TV fica conectada na mesma conta. */
    public static boolean checkPairing(final Context c, final String code) throws Exception {
        final String[] anon = anonymous(c);
        final String url = dbUrl() + "/pairings/" + code + ".json?auth=" + anon[0];
        final JSONObject data;
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(new Request.Builder().url(url).build()).execute()) {
            final String body = response.body() != null ? response.body().string() : "null";
            if (!response.isSuccessful() || "null".equals(body.trim())) {
                return false;
            }
            data = new JSONObject(body);
        }
        final String refresh = data.optString("refresh", "");
        if (refresh.isEmpty()) {
            return false;
        }
        final JSONObject r = refresh(refresh);
        saveSession(c, r.getString("refresh_token"), r.getString("user_id"),
                data.optString("email", ""), r.getString("id_token"),
                r.optLong("expires_in", 3600));
        cancelPairing(c, code);
        return true;
    }

    /** TV: apaga o código (ao conectar ou ao desistir). */
    public static void cancelPairing(final Context c, final String code) {
        try {
            final String[] anon = anonymous(c);
            DownloaderImpl.getInstance().getClient().newCall(new Request.Builder()
                    .url(dbUrl() + "/pairings/" + code + ".json?auth=" + anon[0])
                    .delete().build()).execute().close();
        } catch (final Exception ignored) {
            // o código expira sozinho na prática
        }
    }

    /** Celular: aprova a TV que mostra este código. */
    public static void approveTv(final Context c, final String code) throws Exception {
        final String token = idToken(c);
        final String refresh = prefs(c).getString("refresh", null);
        final Request request = new Request.Builder()
                .url(dbUrl() + "/pairings/" + code.trim() + ".json?auth=" + token)
                .patch(RequestBody.create(new JSONObject().put("refresh", refresh)
                        .put("email", email(c)).toString(), JSON))
                .build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(com.kamarguinho.supertube.util.Lang.t(
                        "Código não encontrado. Confira o número que aparece na TV."));
            }
        }
    }

    // ================= HTTP =================

    private static JSONObject post(final String url, final JSONObject body) throws Exception {
        return execute(new Request.Builder().url(url)
                .post(RequestBody.create(body.toString(), JSON)).build());
    }

    private static JSONObject execute(final Request request) throws Exception {
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            final String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                String code = "HTTP " + response.code();
                try {
                    code = new JSONObject(body).getJSONObject("error").getString("message");
                } catch (final Exception ignored) {
                    // mantém o código HTTP
                }
                throw new IOException(friendly(code));
            }
            return new JSONObject(body);
        }
    }

    /** Erros do Firebase em português. */
    private static String friendly(final String code) {
        if (code.startsWith("EMAIL_EXISTS")) {
            return com.kamarguinho.supertube.util.Lang.t("Este e-mail já tem conta. Use \"Entrar\".");
        } else if (code.startsWith("EMAIL_NOT_FOUND") || code.startsWith("INVALID_LOGIN")
                || code.startsWith("INVALID_PASSWORD")) {
            return com.kamarguinho.supertube.util.Lang.t("E-mail ou senha incorretos.");
        } else if (code.startsWith("WEAK_PASSWORD")) {
            return com.kamarguinho.supertube.util.Lang.t("A senha precisa ter pelo menos 6 caracteres.");
        } else if (code.startsWith("INVALID_EMAIL")) {
            return com.kamarguinho.supertube.util.Lang.t("E-mail inválido.");
        } else if (code.startsWith("TOO_MANY_ATTEMPTS")) {
            return com.kamarguinho.supertube.util.Lang.t("Muitas tentativas. Espere um pouco e tente de novo.");
        } else if (code.startsWith("OPERATION_NOT_ALLOWED") || code.startsWith("ADMIN_ONLY")) {
            return com.kamarguinho.supertube.util.Lang.t("Login não ativado no Firebase (ative E-mail/senha e Anônimo).");
        }
        return code;
    }
}
