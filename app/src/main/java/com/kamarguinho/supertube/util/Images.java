package com.kamarguinho.supertube.util;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;

import java.util.List;

public final class Images {

    private Images() {
    }

    /** Escolhe a melhor imagem até ~720px de altura (para não gastar dados à toa). */
    @Nullable
    public static String best(@Nullable final List<Image> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Image chosen = null;
        for (final Image img : images) {
            final int h = img.getHeight();
            if (h == Image.HEIGHT_UNKNOWN) {
                if (chosen == null) {
                    chosen = img;
                }
                continue;
            }
            if (h <= 720 && (chosen == null || chosen.getHeight() == Image.HEIGHT_UNKNOWN
                    || h > chosen.getHeight())) {
                chosen = img;
            }
        }
        return (chosen != null ? chosen : images.get(images.size() - 1)).getUrl();
    }

    public static String duration(final long seconds) {
        if (seconds <= 0) {
            return "";
        }
        final long h = seconds / 3600;
        final long m = (seconds % 3600) / 60;
        final long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public static String errorMessage(final Throwable t) {
        final String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
    }
}
