package com.kamarguinho.supertube.player;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Faixas de áudio do vídeo: a original e as dublagens que o YouTube gerou
 * (inclusive a dublagem automática por IA).
 */
public final class AudioTracks {

    /** Uma faixa de áudio do menu. */
    public static final class Track {
        /** Identificador da faixa no YouTube (null quando o vídeo só tem uma). */
        @Nullable
        public final String id;
        public final String language;
        public final String label;
        public final boolean original;

        Track(@Nullable final String id, final String language, final String label,
              final boolean original) {
            this.id = id;
            this.language = language;
            this.label = label;
            this.original = original;
        }
    }

    /** Preferência salva: "original" ou um idioma ("pt", "en"...). */
    public static final String PREF_ORIGINAL = "original";

    private static final String PREFS = "audio";
    private static final String KEY_LANG = "language";
    

    private AudioTracks() {
    }

    public static String preferred(final Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANG, PREF_ORIGINAL);
    }

    public static void setPreferred(final Context c, final String lang) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANG, lang).apply();
    }

    /** Faixas diferentes do vídeo (sem repetir a mesma em vários formatos). */
    public static List<Track> tracks(final StreamInfo info) {
        final Map<String, Track> map = new LinkedHashMap<>();
        for (final AudioStream a : info.getAudioStreams()) {
            final String key = a.getAudioTrackId() != null ? a.getAudioTrackId() : "default";
            if (map.containsKey(key)) {
                continue;
            }
            map.put(key, new Track(a.getAudioTrackId(), language(a), label(a), isOriginal(a)));
        }
        final List<Track> out = new ArrayList<>(map.values());
        // Original primeiro, depois as dublagens
        out.sort((x, y) -> Boolean.compare(y.original, x.original));
        return out;
    }

    /** A faixa que um AudioStream representa. */
    public static Track of(final AudioStream a) {
        return new Track(a.getAudioTrackId(), language(a), label(a), isOriginal(a));
    }

    static boolean isOriginal(final AudioStream a) {
        return a.getAudioTrackType() == null || a.getAudioTrackType() == AudioTrackType.ORIGINAL;
    }

    static String language(final AudioStream a) {
        final Locale l = a.getAudioLocale();
        return l == null ? "und" : l.getLanguage().toLowerCase(Locale.ROOT);
    }

    private static String label(final AudioStream a) {
        final Locale l = a.getAudioLocale();
        String name = l != null ? l.getDisplayLanguage(com.kamarguinho.supertube.util.Lang.locale()) : null;
        if (name == null || name.isEmpty()) {
            name = a.getAudioTrackName() != null ? a.getAudioTrackName() : com.kamarguinho.supertube.util.Lang.t("Áudio");
        } else {
            name = name.substring(0, 1).toUpperCase(com.kamarguinho.supertube.util.Lang.locale()) + name.substring(1);
        }
        final AudioTrackType t = a.getAudioTrackType();
        if (t == null || t == AudioTrackType.ORIGINAL) {
            return name + " (" + com.kamarguinho.supertube.util.Lang.t("original") + ")";
        } else if (t == AudioTrackType.DUBBED) {
            return name + " (" + com.kamarguinho.supertube.util.Lang.t("dublado") + ")";
        } else if (t == AudioTrackType.DESCRIPTIVE) {
            return name + " (" + com.kamarguinho.supertube.util.Lang.t("descrição de áudio") + ")";
        }
        return name;
    }

    /** Nome curto para o botão ("Português", "Original"...). */
    public static String shortLabel(@Nullable final Track t) {
        if (t == null || t.original) {
            return com.kamarguinho.supertube.util.Lang.t("Original");
        }
        final int p = t.label.indexOf(" (");
        return p > 0 ? t.label.substring(0, p) : t.label;
    }

    public interface OnChoice {
        void onChoice(Track track);
    }

    /** Menu "Áudio / Dublagem". */
    public static void showMenu(final Activity activity, final StreamInfo info,
                                @Nullable final Track current, final OnChoice listener) {
        final List<Track> tracks = tracks(info);
        if (tracks.size() <= 1) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.audio_dub)
                    .setMessage(R.string.audio_dub_none)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final String[] labels = new String[tracks.size()];
        int checked = 0;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i] = tracks.get(i).label;
            if (current != null && current.id != null && current.id.equals(tracks.get(i).id)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.audio_dub)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    final Track chosen = tracks.get(which);
                    // Lembra a escolha para os próximos vídeos, como no YouTube
                    setPreferred(activity, chosen.original ? PREF_ORIGINAL : chosen.language);
                    listener.onChoice(chosen);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
