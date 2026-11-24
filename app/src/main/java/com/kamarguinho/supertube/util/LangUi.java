package com.kamarguinho.supertube.util;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;

/** Escolha do idioma do app: Automático, Português ou English. */
public final class LangUi {

    private LangUi() {
    }

    public static String currentLabel(final Activity a) {
        final String c = Lang.chosen();
        if (Lang.PT.equals(c)) {
            return "Português";
        } else if (Lang.EN.equals(c)) {
            return "English";
        }
        return a.getString(R.string.language_system);
    }

    public static void show(final Activity a) {
        final String[] codes = {Lang.SYSTEM, Lang.PT, Lang.EN};
        final String[] labels = {a.getString(R.string.language_system), "Português", "English"};
        int checked = 0;
        final String c = Lang.chosen();
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(c)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.language)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    // O app se recria no idioma escolhido
                    Lang.choose(codes[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
