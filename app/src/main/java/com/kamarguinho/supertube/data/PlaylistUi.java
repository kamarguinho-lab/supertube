package com.kamarguinho.supertube.data;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;

import java.util.List;

/** Janelas de playlist (funcionam com toque e com controle remoto). */
public final class PlaylistUi {

    public interface Done {
        void onDone();
    }

    private PlaylistUi() {
    }

    /** "Salvar em": marca/desmarca o vídeo em cada playlist, ou cria uma nova. */
    public static void saveTo(final Activity activity, final Favorites.Video video,
                              final Done done) {
        final Playlists store = new Playlists(activity);
        final List<Playlists.Playlist> lists = store.all();
        final String[] names = new String[lists.size() + 1];
        final boolean[] checked = new boolean[lists.size() + 1];
        names[0] = activity.getString(R.string.playlist_new);
        for (int i = 0; i < lists.size(); i++) {
            names[i + 1] = lists.get(i).name + "  (" + lists.get(i).videos.size() + ")";
            checked[i + 1] = lists.get(i).contains(video.url);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.playlist_save_to)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        dialog.dismiss();
                        askName(activity, null, name -> {
                            final Playlists.Playlist p = store.create(name);
                            store.toggle(p.id, video);
                            Toast.makeText(activity, activity.getString(R.string.playlist_saved,
                                    name), Toast.LENGTH_SHORT).show();
                            if (done != null) {
                                done.onDone();
                            }
                        });
                        return;
                    }
                    final Playlists.Playlist p = lists.get(which - 1);
                    final boolean inside = store.toggle(p.id, video);
                    Toast.makeText(activity, activity.getString(inside
                            ? R.string.playlist_saved : R.string.playlist_removed, p.name),
                            Toast.LENGTH_SHORT).show();
                    if (done != null) {
                        done.onDone();
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public interface OnName {
        void onName(String name);
    }

    /** Pede o nome de uma playlist (nova ou renomear). */
    public static void askName(final Activity activity, final String current,
                               final OnName onName) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.playlist_name_hint);
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }
        final FrameLayout box = new FrameLayout(activity);
        final int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(current == null ? R.string.playlist_new : R.string.playlist_rename)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        onName.onName(name);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }
}
