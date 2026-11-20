package com.kamarguinho.supertube;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * Faz as requisições HTTP para a NewPipeExtractor.
 * Baseado no DownloaderImpl do NewPipe (GPLv3).
 */
public final class DownloaderImpl extends Downloader {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private static DownloaderImpl instance;
    private final OkHttpClient client;

    private DownloaderImpl() {
        client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static void init() {
        instance = new DownloaderImpl();
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }

    @Override
    public Response execute(@NonNull final Request request)
            throws IOException, ReCaptchaException {
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody body = null;
        if (dataToSend != null) {
            body = RequestBody.create(dataToSend);
        }

        final okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);

        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            builder.removeHeader(entry.getKey());
            for (final String value : entry.getValue()) {
                builder.addHeader(entry.getKey(), value);
            }
        }

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", url);
            }
            String responseBody = null;
            try (ResponseBody rb = response.body()) {
                if (rb != null) {
                    responseBody = rb.string();
                }
            }
            return new Response(response.code(), response.message(),
                    response.headers().toMultimap(), responseBody,
                    response.request().url().toString());
        }
    }
}
