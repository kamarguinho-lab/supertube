package com.kamarguinho.supertube.player;

import android.app.Activity;
import android.content.Context;
import android.media.MediaCodecList;
import android.net.ConnectivityManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Qualidade do vídeo no estilo do YouTube:
 * Automática (recomendada), Imagem superior, Economia de dados e Avançado (resolução exata).
 */
public final class Quality {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_HIGH = "high";
    public static final String MODE_SAVER = "saver";

    private static final String PREFS = "quality";
    private static final String KEY_MODE = "mode";
    private static final Map<String, Boolean> CODEC_CACHE = new HashMap<>();

    private Quality() {
    }

    public static String mode(final Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_AUTO);
    }

    public static void setMode(final Context c, final String mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, mode).apply();
    }

    /** Resolução máxima para um modo. */
    public static int maxHeight(final Context c, final String mode, final boolean tv) {
        switch (mode) {
            case MODE_HIGH:
                return tv ? 2160 : 1440;
            case MODE_SAVER:
                return 360;
            default:
                // Automática: na TV e no Wi-Fi, Full HD; nos dados móveis, 480p
                if (tv || !isMetered(c)) {
                    return 1080;
                }
                return 480;
        }
    }

    private static boolean isMetered(final Context c) {
        final ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        return cm != null && cm.isActiveNetworkMetered();
    }

    /** Resoluções disponíveis neste vídeo que este aparelho consegue tocar. */
    public static List<Integer> heights(final StreamInfo info) {
        final TreeMap<Integer, Boolean> set = new TreeMap<>((a, b) -> b - a);
        final List<VideoStream> all = new ArrayList<>(info.getVideoOnlyStreams());
        all.addAll(info.getVideoStreams());
        for (final VideoStream v : all) {
            if (v.getHeight() > 0 && canPlay(v)) {
                set.put(v.getHeight(), true);
            }
        }
        return new ArrayList<>(set.keySet());
    }

    /** "1080p HD", "2160p 4K", "480p"... */
    public static String label(final int height) {
        if (height >= 2160) {
            return height + "p 4K";
        }
        if (height >= 1440) {
            return height + "p HD";
        }
        if (height >= 720) {
            return height + "p HD";
        }
        return height + "p";
    }

    /** O aparelho tem decodificador para este formato e tamanho? */
    public static boolean canPlay(final VideoStream v) {
        final String codec = v.getCodec() == null ? "" : v.getCodec().toLowerCase();
        final String mime;
        if (codec.startsWith("av01")) {
            mime = "video/av01";
        } else if (codec.startsWith("vp9") || codec.startsWith("vp09")) {
            mime = "video/x-vnd.on2.vp9";
        } else if (codec.startsWith("avc")) {
            mime = "video/avc";
        } else {
            return true;
        }
        final int w = v.getWidth() > 0 ? v.getWidth() : v.getHeight() * 16 / 9;
        final int h = v.getHeight();
        final String key = mime + w + "x" + h;
        synchronized (CODEC_CACHE) {
            final Boolean cached = CODEC_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            boolean ok;
            try {
                final android.media.MediaFormat format =
                        android.media.MediaFormat.createVideoFormat(mime, Math.max(w, 1),
                                Math.max(h, 1));
                ok = new MediaCodecList(MediaCodecList.REGULAR_CODECS)
                        .findDecoderForFormat(format) != null;
            } catch (final Exception e) {
                ok = !"video/av01".equals(mime);
            }
            CODEC_CACHE.put(key, ok);
            return ok;
        }
    }

    public interface OnChoice {
        /**
         * @param maxHeight resolução a usar
         * @param exact     true quando escolhida em "Avançado" (vale só para este vídeo)
         */
        void onChoice(int maxHeight, boolean exact);
    }

    /** Menu igual ao do YouTube. */
    public static void showMenu(final Activity activity, final StreamInfo info,
                                final boolean tv, final int playingHeight,
                                final boolean exactActive, final OnChoice listener) {
        final String mode = mode(activity);
        final String playing = playingHeight > 0 ? " (" + playingHeight + "p)" : "";
        final String[] labels = {
                activity.getString(R.string.quality_auto)
                        + (MODE_AUTO.equals(mode) && !exactActive ? playing : "")
                        + "\n" + activity.getString(R.string.quality_auto_desc),
                activity.getString(R.string.quality_high)
                        + "\n" + activity.getString(R.string.quality_high_desc),
                activity.getString(R.string.quality_saver)
                        + "\n" + activity.getString(R.string.quality_saver_desc),
                activity.getString(R.string.quality_advanced)
                        + (exactActive ? playing : "")
                        + "\n" + activity.getString(R.string.quality_advanced_desc),
        };
        int checked = exactActive ? 3 : MODE_HIGH.equals(mode) ? 1
                : MODE_SAVER.equals(mode) ? 2 : 0;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.quality_title) + playing)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 3) {
                        showAdvanced(activity, info, playingHeight, listener);
                        return;
                    }
                    final String newMode = which == 1 ? MODE_HIGH
                            : which == 2 ? MODE_SAVER : MODE_AUTO;
                    setMode(activity, newMode);
                    listener.onChoice(maxHeight(activity, newMode, tv), false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showAdvanced(final Activity activity, final StreamInfo info,
                                     final int playingHeight, final OnChoice listener) {
        final List<Integer> heights = heights(info);
        if (heights.isEmpty()) {
            return;
        }
        final String[] labels = new String[heights.size()];
        int checked = -1;
        for (int i = 0; i < heights.size(); i++) {
            labels[i] = label(heights.get(i));
            if (heights.get(i) == playingHeight) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.quality_advanced)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    listener.onChoice(heights.get(which), true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
