package com.kamarguinho.supertube.data;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.kamarguinho.supertube.DownloaderImpl;

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

/** Downloads usando o gerenciador de downloads do próprio Android. */
public final class Downloads {

    public static final class Option {
        public final String label;
        public final Stream stream;

        Option(final String label, final Stream stream) {
            this.label = label;
            this.stream = stream;
        }
    }

    private Downloads() {
    }

    /** Só arquivos diretos (vídeo com som e áudio), que dá para baixar de uma vez. */
    public static List<Option> options(final StreamInfo info) {
        final List<Option> out = new ArrayList<>();
        for (final VideoStream v : info.getVideoStreams()) {
            if (direct(v)) {
                out.add(new Option("Vídeo " + v.getResolution() + " ("
                        + v.getFormat().getSuffix().toUpperCase() + ")", v));
            }
        }
        for (final AudioStream a : info.getAudioStreams()) {
            if (direct(a)) {
                final int kbps = a.getAverageBitrate();
                String label = "Áudio " + (kbps > 0 ? kbps + " kbps " : "")
                        + "(" + a.getFormat().getSuffix().toUpperCase() + ")";
                if (a.getAudioTrackName() != null) {
                    label += " – " + a.getAudioTrackName();
                }
                out.add(new Option(label, a));
            }
        }
        return out;
    }

    private static boolean direct(final Stream s) {
        return s.isUrl() && s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && s.getFormat() != null;
    }

    public static long enqueue(final Context context, final StreamInfo info, final Stream stream) {
        final String url = stream.getContent();
        final String userAgent = YoutubeParsingHelper.isVisionOsStreamingUrl(url)
                ? YoutubeParsingHelper.getVisionOsUserAgent(null)
                : DownloaderImpl.USER_AGENT;
        final String fileName = safeName(info.getName()) + "." + stream.getFormat().getSuffix();

        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("User-Agent", userAgent)
                .setTitle(info.getName())
                .setDescription("SuperTube")
                .setMimeType(stream.getFormat().getMimeType())
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        "SuperTube/" + fileName);
        final DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        return dm.enqueue(request);
    }

    private static String safeName(final String name) {
        String s = name == null ? "video" : name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (s.isEmpty()) {
            s = "video";
        }
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
