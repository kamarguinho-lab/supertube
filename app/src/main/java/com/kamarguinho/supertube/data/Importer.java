package com.kamarguinho.supertube.data;

import android.content.Context;
import android.net.Uri;

import com.kamarguinho.supertube.DownloaderImpl;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Importa inscrições e vídeos favoritos do Google Takeout ou da conta Google. */
public final class Importer {

    public static final class Result {
        public int channelsFound;
        public int channelsAdded;
        public int videosFound;
        public int videosAdded;
    }

    /** Título temporário de vídeos importados só com o ID (é trocado depois). */
    public static final String PENDING_TITLE = "Carregando título…";

    private static final Pattern CHANNEL_ID = Pattern.compile("UC[\\w-]{22}");
    private static final Pattern VIDEO_ID = Pattern.compile("[\\w-]{11}");
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private Importer() {
    }

    // ---------------- Google Takeout ----------------

    /**
     * Lê um .zip inteiro do Takeout ou um .csv solto (subscriptions.csv, playlists...).
     * Reconhece o conteúdo pelos IDs, então funciona em qualquer idioma do Takeout.
     */
    public static Result importTakeout(final Context context, final Uri uri) throws IOException {
        final Map<String, Subscriptions.Channel> channels = new LinkedHashMap<>();
        final Map<String, Favorites.Video> videos = new LinkedHashMap<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) {
                throw new IOException("Não foi possível abrir o arquivo");
            }
            final BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(4);
            final byte[] magic = new byte[2];
            final int n = in.read(magic);
            in.reset();

            if (n == 2 && magic[0] == 'P' && magic[1] == 'K') {
                try (ZipInputStream zip = new ZipInputStream(in)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        final String name = entry.getName().toLowerCase();
                        if (!entry.isDirectory() && name.endsWith(".csv")) {
                            parseCsv(readAll(zip), channels, videos);
                        }
                    }
                }
            } else {
                parseCsv(readAll(in), channels, videos);
            }
        }
        return save(context, new ArrayList<>(channels.values()),
                new ArrayList<>(videos.values()));
    }

    private static String readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[16 * 1024];
        int r;
        long total = 0;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > MAX_FILE_BYTES) {
                break;
            }
            out.write(buf, 0, r);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    static void parseCsv(final String text, final Map<String, Subscriptions.Channel> channels,
                         final Map<String, Favorites.Video> videos) {
        for (final String line : text.split("\\r?\\n")) {
            final List<String> cols = splitCsvLine(line);
            if (cols.isEmpty()) {
                continue;
            }
            final String first = cols.get(0).trim();
            if (CHANNEL_ID.matcher(first).matches()) {
                // subscriptions.csv: ID do canal, URL do canal, Nome do canal
                String name = cols.size() > 2 ? cols.get(2).trim() : "";
                if (name.isEmpty()) {
                    name = first;
                }
                channels.put(first, new Subscriptions.Channel(0,
                        "https://www.youtube.com/channel/" + first, name, null));
            } else if (VIDEO_ID.matcher(first).matches()) {
                // playlists (inclusive "vídeos curtidos"): ID do vídeo, data
                videos.put(first, new Favorites.Video(0, Favorites.youtubeUrl(first),
                        PENDING_TITLE, null, Favorites.youtubeThumb(first)));
            }
        }
    }

    private static List<String> splitCsvLine(final String line) {
        final List<String> out = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        if (out.size() == 1 && out.get(0).trim().isEmpty()) {
            out.clear();
        }
        return out;
    }

    // ---------------- Conta Google (YouTube Data API) ----------------

    private static final String API = "https://www.googleapis.com/youtube/v3/";
    private static final int MAX_ITEMS = 2000;

    /** Busca as inscrições e os vídeos curtidos da conta que fez login. */
    public static Result importFromAccount(final Context context, final String accessToken)
            throws Exception {
        final List<Subscriptions.Channel> channels = new ArrayList<>();
        String pageToken = null;
        do {
            final HttpUrl.Builder url = HttpUrl.get(API + "subscriptions").newBuilder()
                    .addQueryParameter("part", "snippet")
                    .addQueryParameter("mine", "true")
                    .addQueryParameter("maxResults", "50");
            if (pageToken != null) {
                url.addQueryParameter("pageToken", pageToken);
            }
            final JSONObject json = get(url.build(), accessToken);
            final JSONArray items = json.optJSONArray("items");
            for (int i = 0; items != null && i < items.length(); i++) {
                final JSONObject snippet = items.getJSONObject(i).getJSONObject("snippet");
                final String id = snippet.getJSONObject("resourceId").optString("channelId");
                if (id.isEmpty()) {
                    continue;
                }
                channels.add(new Subscriptions.Channel(0,
                        "https://www.youtube.com/channel/" + id,
                        snippet.optString("title", id), thumb(snippet)));
            }
            pageToken = json.optString("nextPageToken", null);
        } while (pageToken != null && !pageToken.isEmpty() && channels.size() < MAX_ITEMS);

        final List<Favorites.Video> videos = new ArrayList<>();
        pageToken = null;
        do {
            final HttpUrl.Builder url = HttpUrl.get(API + "videos").newBuilder()
                    .addQueryParameter("part", "snippet")
                    .addQueryParameter("myRating", "like")
                    .addQueryParameter("maxResults", "50");
            if (pageToken != null) {
                url.addQueryParameter("pageToken", pageToken);
            }
            final JSONObject json = get(url.build(), accessToken);
            final JSONArray items = json.optJSONArray("items");
            for (int i = 0; items != null && i < items.length(); i++) {
                final JSONObject item = items.getJSONObject(i);
                final String id = item.optString("id");
                final JSONObject snippet = item.getJSONObject("snippet");
                final String t = thumb(snippet);
                videos.add(new Favorites.Video(0, Favorites.youtubeUrl(id),
                        snippet.optString("title", id), snippet.optString("channelTitle", null),
                        t != null ? t : Favorites.youtubeThumb(id)));
            }
            pageToken = json.optString("nextPageToken", null);
        } while (pageToken != null && !pageToken.isEmpty() && videos.size() < MAX_ITEMS);

        return save(context, channels, videos);
    }

    private static String thumb(final JSONObject snippet) {
        final JSONObject t = snippet.optJSONObject("thumbnails");
        if (t == null) {
            return null;
        }
        for (final String size : new String[]{"medium", "high", "default"}) {
            final JSONObject o = t.optJSONObject(size);
            if (o != null && o.has("url")) {
                return o.optString("url");
            }
        }
        return null;
    }

    private static JSONObject get(final HttpUrl url, final String token) throws Exception {
        final Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + token).build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            final String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                String message = "HTTP " + response.code();
                try {
                    message = new JSONObject(body).getJSONObject("error").getString("message");
                } catch (final Exception ignored) {
                    // mantém o código HTTP
                }
                throw new IOException(message);
            }
            return new JSONObject(body);
        }
    }

    // ---------------- Comum ----------------

    private static Result save(final Context context, final List<Subscriptions.Channel> channels,
                               final List<Favorites.Video> videos) {
        final Result r = new Result();
        r.channelsFound = channels.size();
        r.videosFound = videos.size();
        r.channelsAdded = new Subscriptions(context).addAll(channels);
        r.videosAdded = new Favorites(context).addAll(videos);
        return r;
    }

    /**
     * Preenche o título e o canal dos vídeos importados só com o ID,
     * usando o oEmbed público do YouTube (rápido e leve).
     */
    public static int fillMissingTitles(final Context context, final int limit) {
        final Favorites favorites = new Favorites(context);
        int done = 0;
        for (final Favorites.Video v : favorites.all()) {
            if (done >= limit) {
                break;
            }
            if (!PENDING_TITLE.equals(v.title)) {
                continue;
            }
            try {
                final HttpUrl url = HttpUrl.get("https://www.youtube.com/oembed").newBuilder()
                        .addQueryParameter("url", v.url)
                        .addQueryParameter("format", "json").build();
                try (Response response = DownloaderImpl.getInstance().getClient()
                        .newCall(new Request.Builder().url(url).build()).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        final JSONObject o = new JSONObject(response.body().string());
                        favorites.update(new Favorites.Video(v.serviceId, v.url,
                                o.optString("title", v.url),
                                o.optString("author_name", null), v.thumbnail));
                    } else if (response.code() == 401 || response.code() == 404
                            || response.code() == 403) {
                        // vídeo privado ou apagado
                        favorites.update(new Favorites.Video(v.serviceId, v.url,
                                "Vídeo indisponível", null, v.thumbnail));
                    }
                }
                done++;
            } catch (final Exception ignored) {
                // tenta de novo numa próxima vez
            }
        }
        return done;
    }
}
