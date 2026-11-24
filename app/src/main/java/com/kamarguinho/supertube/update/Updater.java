package com.kamarguinho.supertube.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.BuildConfig;
import com.kamarguinho.supertube.DownloaderImpl;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Atualização pelo próprio app: consulta a última versão publicada na página
 * Releases do GitHub, baixa o APK e abre o instalador do Android.
 */
public final class Updater {

    public static final class Release {
        public final int buildNumber;
        public final String apkUrl;
        public final String notes;

        Release(final int buildNumber, final String apkUrl, final String notes) {
            this.buildNumber = buildNumber;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    private static final String PREFS = "updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final long AUTO_CHECK_INTERVAL = 12L * 60 * 60 * 1000;

    private Updater() {
    }

    /** Nome do APK desta versão do app na página Releases. */
    private static String apkName() {
        return "tv".equals(BuildConfig.FLAVOR) ? "SuperTube-TV.apk" : "SuperTube.apk";
    }

    public static String currentVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /** Busca a última versão. Retorna null se já estiver atualizado. */
    @Nullable
    public static Release check() throws Exception {
        final Request request = new Request.Builder()
                .url("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO
                        + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            if (response.code() == 404) {
                throw new IOException(com.kamarguinho.supertube.util.Lang.t("Página de versões não encontrada. "
                        + "O repositório do GitHub precisa ser público."));
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("GitHub respondeu HTTP " + response.code());
            }
            final JSONObject json = new JSONObject(response.body().string());
            final String tag = json.optString("tag_name", "");
            final String digits = tag.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                throw new IOException("Versão desconhecida: " + tag);
            }
            final int build = Integer.parseInt(digits);
            if (build <= BuildConfig.VERSION_CODE) {
                return null;
            }
            // Prefere o arquivo com a versão no nome (SuperTube-1.0.N.apk / SuperTube-TV-1.0.N.apk)
            final JSONArray assets = json.optJSONArray("assets");
            final boolean tv = "tv".equals(BuildConfig.FLAVOR);
            String fallback = null;
            for (int i = 0; assets != null && i < assets.length(); i++) {
                final JSONObject a = assets.getJSONObject(i);
                final String name = a.optString("name");
                final boolean isTv = name.startsWith("SuperTube-TV");
                if (!name.endsWith(".apk") || !name.startsWith("SuperTube") || isTv != tv) {
                    continue;
                }
                if (name.matches(".*-\\d+\\.\\d+\\.\\d+\\.apk")) {
                    return new Release(build, a.getString("browser_download_url"),
                            json.optString("body", ""));
                }
                fallback = a.getString("browser_download_url");
            }
            if (fallback != null) {
                return new Release(build, fallback, json.optString("body", ""));
            }
            throw new IOException("A versão nova não tem o arquivo " + apkName());
        }
    }

    public interface Progress {
        void onProgress(int percent);
    }

    /** Baixa o APK para a pasta temporária do app. */
    public static File download(final Context context, final Release release,
                                final Progress progress) throws Exception {
        final File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta de atualização");
        }
        final File out = new File(dir, apkName());
        final Request request = new Request.Builder().url(release.apkUrl).build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Download falhou: HTTP " + response.code());
            }
            final long total = body.contentLength();
            try (InputStream in = body.byteStream(); OutputStream os = new FileOutputStream(out)) {
                final byte[] buf = new byte[64 * 1024];
                long done = 0;
                int last = -1;
                int r;
                while ((r = in.read(buf)) != -1) {
                    os.write(buf, 0, r);
                    done += r;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        if (pct != last) {
                            last = pct;
                            progress.onProgress(pct);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Abre o instalador do Android com o APK baixado. */
    public static void install(final Activity activity, final File apk) {
        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        final Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".files", apk);
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    // ================= Interface (funciona no celular e na TV) =================

    /**
     * Verifica e oferece a atualização.
     *
     * @param silent se verdadeiro (verificação automática), não mostra nada quando já está
     *               atualizado ou quando dá erro, e só verifica de 12 em 12 horas
     */
    public static void checkAndPrompt(final Activity activity, final boolean silent) {
        final SharedPreferences prefs = activity.getSharedPreferences(PREFS,
                Context.MODE_PRIVATE);
        if (silent) {
            final long last = prefs.getLong(KEY_LAST_CHECK, 0);
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL) {
                return;
            }
        } else {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }
        Bg.run(Updater::check, new Bg.Callback<Release>() {
            @Override
            public void onResult(final Release release) {
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                if (activity.isFinishing()) {
                    return;
                }
                if (release == null) {
                    if (!silent) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.update_title)
                                .setMessage(activity.getString(R.string.update_none,
                                        currentVersion()))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                    return;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.update_available_title)
                        .setMessage(activity.getString(R.string.update_available,
                                currentVersion(), "1.0." + release.buildNumber))
                        .setPositiveButton(R.string.update_now,
                                (d, w) -> downloadAndInstall(activity, release))
                        .setNegativeButton(R.string.update_later, null)
                        .show();
            }

            @Override
            public void onError(final Throwable error) {
                if (!silent && !activity.isFinishing()) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.update_title)
                            .setMessage(activity.getString(R.string.update_error,
                                    Images.errorMessage(error)))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        });
    }

    private static void downloadAndInstall(final Activity activity, final Release release) {
        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_downloading)
                .setMessage("0%")
                .setCancelable(false)
                .show();
        Bg.run(() -> download(activity, release, pct ->
                activity.runOnUiThread(() -> dialog.setMessage(pct + "%"))),
                new Bg.Callback<File>() {
                    @Override
                    public void onResult(final File apk) {
                        dialog.dismiss();
                        install(activity, apk);
                    }

                    @Override
                    public void onError(final Throwable error) {
                        dialog.dismiss();
                        Toast.makeText(activity, activity.getString(R.string.update_error,
                                Images.errorMessage(error)), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
