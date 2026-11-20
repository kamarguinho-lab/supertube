package com.kamarguinho.supertube.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.kamarguinho.supertube.DownloaderImpl;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Transforma um {@link StreamInfo} da NewPipeExtractor em algo que o ExoPlayer toca.
 * Versão simplificada do PlaybackResolver do NewPipe (GPLv3).
 */
public final class MediaResolver {

    private final DataSource.Factory generic;
    private final DataSource.Factory ytHls;
    private final DataSource.Factory ytDash;
    private final DataSource.Factory ytProgressive;

    /** Resolução do vídeo escolhido na última vez (0 = só áudio / desconhecida). */
    public volatile int lastHeight = 0;

    /** Faixa de áudio escolhida para o vídeo atual (null = automática). */
    public volatile String audioTrackId = null;
    /** Idioma de áudio preferido ("original", "pt"...). */
    public volatile String audioLanguage = AudioTracks.PREF_ORIGINAL;
    /** Faixa de áudio usada na última vez. */
    public volatile AudioTracks.Track lastTrack = null;

    public MediaResolver(final Context context) {
        generic = new DefaultDataSource.Factory(context,
                new DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT));
        ytHls = new YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(false).setRnParameterEnabled(false);
        ytDash = new YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(true).setRnParameterEnabled(true);
        ytProgressive = new YoutubeHttpDataSource.Factory()
                .setRangeParameterEnabled(false).setRnParameterEnabled(true);
    }

    /**
     * @param audioOnly se verdadeiro, carrega só o áudio (economiza dados em segundo plano)
     */
    public MediaSource resolve(final StreamInfo info, final int maxHeight,
                               final boolean audioOnly) throws Exception {
        return resolve(info, maxHeight, audioOnly, null);
    }

    /** Igual ao de cima, juntando uma legenda (ou nenhuma, se {@code subtitle} for null). */
    public MediaSource resolve(final StreamInfo info, final int maxHeight,
                               final boolean audioOnly,
                               @Nullable final Subtitles.Option subtitle) throws Exception {
        final MediaSource media = resolveMedia(info, maxHeight, audioOnly);
        if (subtitle == null) {
            return media;
        }
        final MediaItem.SubtitleConfiguration config =
                new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType)
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build();
        final MediaSource text = new SingleSampleMediaSource.Factory(generic)
                .createMediaSource(config, C.TIME_UNSET);
        return new MergingMediaSource(media, text);
    }

    private MediaSource resolveMedia(final StreamInfo info, final int maxHeight,
                                     final boolean audioOnly) throws Exception {
        final boolean youtube = info.getServiceId() == ServiceList.YouTube.getServiceId();
        final StreamType type = info.getStreamType();

        // Transmissões ao vivo
        if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) {
            if (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
                return new HlsMediaSource.Factory(youtube ? ytHls : generic)
                        .createMediaSource(MediaItem.fromUri(info.getHlsUrl()));
            }
            if (info.getDashMpdUrl() != null && !info.getDashMpdUrl().isEmpty()) {
                return new DashMediaSource.Factory(generic)
                        .createMediaSource(MediaItem.fromUri(info.getDashMpdUrl()));
            }
            throw new Exception("Transmissão ao vivo sem link compatível");
        }

        lastHeight = 0;
        final AudioStream audio = pickAudioFor(info.getAudioStreams());
        lastTrack = audio != null ? AudioTracks.of(audio) : null;

        if (audioOnly && audio != null) {
            return source(audio, info, youtube);
        }

        // Melhor qualidade: vídeo sem som + áudio separados, tocados juntos
        final VideoStream videoOnly = pickVideo(info.getVideoOnlyStreams(), maxHeight);
        if (videoOnly != null && audio != null) {
            lastHeight = videoOnly.getHeight();
            return new MergingMediaSource(true,
                    source(videoOnly, info, youtube), source(audio, info, youtube));
        }

        // Vídeo com som no mesmo arquivo (geralmente 360p)
        final VideoStream muxed = pickVideo(info.getVideoStreams(), maxHeight);
        if (muxed != null) {
            lastHeight = muxed.getHeight();
            return source(muxed, info, youtube);
        }
        if (audio != null) {
            return source(audio, info, youtube);
        }
        if (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
            return new HlsMediaSource.Factory(youtube ? ytHls : generic)
                    .createMediaSource(MediaItem.fromUri(info.getHlsUrl()));
        }
        throw new Exception("Nenhum formato de vídeo compatível foi encontrado");
    }

    private MediaSource source(final Stream stream, final StreamInfo info,
                               final boolean youtube) throws Exception {
        final DeliveryMethod method = stream.getDeliveryMethod();
        final String content = stream.getContent();
        final MediaItem item = new MediaItem.Builder().setUri(Uri.parse(content)).build();

        if (method == DeliveryMethod.HLS) {
            return new HlsMediaSource.Factory(youtube ? ytHls : generic).createMediaSource(item);
        }

        if (youtube) {
            final boolean separate = stream instanceof AudioStream
                    || (stream instanceof VideoStream && ((VideoStream) stream).isVideoOnly());
            if (method == DeliveryMethod.PROGRESSIVE_HTTP && separate
                    && stream.getItagItem() != null) {
                try {
                    final String manifest = YoutubeProgressiveDashManifestCreator
                            .fromProgressiveStreamingUrl(content, stream.getItagItem(),
                                    info.getDuration());
                    return dash(manifest, item);
                } catch (final Exception e) {
                    return new ProgressiveMediaSource.Factory(ytProgressive)
                            .createMediaSource(item);
                }
            }
            if (method == DeliveryMethod.DASH && stream.getItagItem() != null) {
                final String manifest = YoutubeOtfDashManifestCreator
                        .fromOtfStreamingUrl(content, stream.getItagItem(), info.getDuration());
                return dash(manifest, item);
            }
            return new ProgressiveMediaSource.Factory(ytProgressive).createMediaSource(item);
        }

        if (method == DeliveryMethod.DASH && stream.isUrl()) {
            return new DashMediaSource.Factory(generic).createMediaSource(item);
        }
        return new ProgressiveMediaSource.Factory(generic).createMediaSource(item);
    }

    private DashMediaSource dash(final String manifestXml, final MediaItem item)
            throws Exception {
        final DashManifest manifest = new DashManifestParser().parse(Uri.parse(""),
                new ByteArrayInputStream(manifestXml.getBytes(StandardCharsets.UTF_8)));
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(ytDash), ytDash)
                .createMediaSource(manifest, item);
    }

    /** Maior resolução até {@code maxHeight}, preferindo MP4 (mais compatível). */
    @Nullable
    public static VideoStream pickVideo(@Nullable final List<VideoStream> list,
                                        final int maxHeight) {
        if (list == null) {
            return null;
        }
        VideoStream best = null;
        for (final VideoStream v : list) {
            if (unsupported(v) || !Quality.canPlay(v)) {
                continue;
            }
            final int h = v.getHeight();
            if (h > maxHeight) {
                continue;
            }
            if (best == null || h > best.getHeight()
                    || (h == best.getHeight() && v.getFormat() == MediaFormat.MPEG_4
                    && best.getFormat() != MediaFormat.MPEG_4)) {
                best = v;
            }
        }
        if (best == null && !list.isEmpty()) {
            // Nada abaixo do limite: pega o menor disponível
            for (final VideoStream v : list) {
                if (!unsupported(v)
                        && (best == null || v.getHeight() < best.getHeight())) {
                    best = v;
                }
            }
        }
        return best;
    }

    /** Escolhe a faixa: a pedida para este vídeo, ou o idioma preferido, ou a original. */
    @Nullable
    private AudioStream pickAudioFor(@Nullable final List<AudioStream> list) {
        if (list == null) {
            return null;
        }
        final String id = audioTrackId;
        if (id != null) {
            final List<AudioStream> same = new java.util.ArrayList<>();
            for (final AudioStream a : list) {
                if (id.equals(a.getAudioTrackId())) {
                    same.add(a);
                }
            }
            if (!same.isEmpty()) {
                return pickAudio(same);
            }
        }
        final String lang = audioLanguage;
        if (lang != null && !AudioTracks.PREF_ORIGINAL.equals(lang)) {
            final List<AudioStream> inLang = new java.util.ArrayList<>();
            for (final AudioStream a : list) {
                if (lang.equals(AudioTracks.language(a))) {
                    inLang.add(a);
                }
            }
            if (!inLang.isEmpty()) {
                return pickAudio(inLang);
            }
        }
        return pickAudio(list);
    }

    /** Áudio original (não dublado) com maior qualidade, preferindo M4A. */
    @Nullable
    public static AudioStream pickAudio(@Nullable final List<AudioStream> list) {
        if (list == null) {
            return null;
        }
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final AudioStream a : list) {
            if (unsupported(a)) {
                continue;
            }
            int score = a.getAverageBitrate();
            if (a.getFormat() == MediaFormat.M4A) {
                score += 20;
            }
            final AudioTrackType trackType = a.getAudioTrackType();
            if (trackType == null || trackType == AudioTrackType.ORIGINAL) {
                score += 10_000;
            }
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    private static boolean unsupported(final Stream s) {
        return s.getDeliveryMethod() == DeliveryMethod.TORRENT
                || s.getDeliveryMethod() == DeliveryMethod.SS;
    }
}
