package com.kamarguinho.supertube.download;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

/** Escolhe os arquivos do YouTube para cada opção de download (MP3, 720p, 1080p). */
public final class DownloadOptions {

    private DownloadOptions() {
    }

    static boolean direct(final Stream s) {
        return s != null && s.isUrl() && s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && s.getFormat() != null;
    }

    /** Melhor áudio original (não dublado), preferindo M4A (AAC). */
    public static AudioStream bestAudio(final StreamInfo info, final boolean m4aOnly) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final AudioStream a : info.getAudioStreams()) {
            if (!direct(a)) {
                continue;
            }
            final boolean m4a = a.getFormat() == MediaFormat.M4A;
            if (m4aOnly && !m4a) {
                continue;
            }
            final AudioTrackType type = a.getAudioTrackType();
            final boolean original = type == null || type == AudioTrackType.ORIGINAL;
            final int score = (original ? 1_000_000 : 0) + (m4a ? 100_000 : 0)
                    + Math.max(0, a.getAverageBitrate());
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** Vídeo sem som em MP4/H.264 exatamente na altura pedida (720 ou 1080). */
    public static VideoStream videoOnly(final StreamInfo info, final int height) {
        VideoStream best = null;
        for (final VideoStream v : info.getVideoOnlyStreams()) {
            if (!direct(v) || v.getFormat() != MediaFormat.MPEG_4 || v.getHeight() != height) {
                continue;
            }
            final String codec = v.getCodec();
            if (codec != null && !codec.startsWith("avc1")) {
                continue;
            }
            // Prefere 30 fps quando existir 60 (arquivo menor, toca em qualquer aparelho)
            if (best == null || (best.getFps() > 30 && v.getFps() <= 30)) {
                best = v;
            }
        }
        return best;
    }

    /** Vídeo já com som (normalmente 360p), usado quando não há 720p/1080p. */
    public static VideoStream muxed(final StreamInfo info) {
        VideoStream best = null;
        for (final VideoStream v : info.getVideoStreams()) {
            if (direct(v) && v.getFormat() == MediaFormat.MPEG_4
                    && (best == null || v.getHeight() > best.getHeight())) {
                best = v;
            }
        }
        return best;
    }
}
