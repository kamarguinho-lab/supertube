package com.kamarguinho.supertube.player;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamSegment;

import java.util.Collections;
import java.util.List;

/**
 * Capítulos do vídeo (ex.: cada música de uma compilação/álbum completo),
 * para pular de uma música para a outra dentro do mesmo vídeo.
 */
public final class Chapters {

    private Chapters() {
    }

    /** Capítulos do vídeo (vazio se tiver menos de 2). */
    public static List<StreamSegment> of(@Nullable final StreamInfo info) {
        if (info == null || info.getStreamSegments() == null
                || info.getStreamSegments().size() < 2) {
            return Collections.emptyList();
        }
        return info.getStreamSegments();
    }

    /** Índice do capítulo que está tocando em {@code positionMs}. */
    public static int indexAt(final List<StreamSegment> segs, final long positionMs) {
        int idx = 0;
        for (int i = 0; i < segs.size(); i++) {
            if (segs.get(i).getStartTimeSeconds() * 1000L <= positionMs + 500) {
                idx = i;
            }
        }
        return idx;
    }

    /** Início do próximo capítulo em ms, ou -1 se já está no último. */
    public static long nextStart(@Nullable final StreamInfo info, final long positionMs) {
        final List<StreamSegment> segs = of(info);
        if (segs.isEmpty()) {
            return -1;
        }
        final int i = indexAt(segs, positionMs);
        return i + 1 < segs.size() ? segs.get(i + 1).getStartTimeSeconds() * 1000L : -1;
    }

    /**
     * Para o "anterior": início do capítulo atual (se já passou 3 s dele) ou do anterior.
     * -1 se está no começo do primeiro capítulo.
     */
    public static long previousStart(@Nullable final StreamInfo info, final long positionMs) {
        final List<StreamSegment> segs = of(info);
        if (segs.isEmpty()) {
            return -1;
        }
        final int i = indexAt(segs, positionMs);
        final long start = segs.get(i).getStartTimeSeconds() * 1000L;
        if (positionMs - start > 3000) {
            return start;
        }
        return i > 0 ? segs.get(i - 1).getStartTimeSeconds() * 1000L : -1;
    }

    public static String time(final int seconds) {
        final int h = seconds / 3600;
        final int m = (seconds % 3600) / 60;
        final int s = seconds % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(java.util.Locale.ROOT, "%d:%02d", m, s);
    }
}
