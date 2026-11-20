package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.PlaylistUi;
import com.kamarguinho.supertube.data.Playlists;
import com.kamarguinho.supertube.player.PlayQueue;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** Uma playlist criada pela pessoa: tocar tudo, tirar vídeos, renomear e apagar. */
public class PlaylistActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "id";

    private Playlists store;
    private String id;
    private final ItemAdapter adapter = new ItemAdapter();

    public static void open(final Context context, final String id) {
        context.startActivity(new Intent(context, PlaylistActivity.class)
                .putExtra(EXTRA_ID, id));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);
        store = new Playlists(this);
        id = getIntent().getStringExtra(EXTRA_ID);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        final RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        adapter.setOnStreamClick(item -> {
            final Playlists.Playlist p = store.get(id);
            if (p != null) {
                PlayQueue.set(p.name, p.asItems(), null);
            }
            PlayerActivity.open(this, item.getUrl());
        });
        adapter.setOnStreamLongClick(item -> new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.playlist_remove_video, item.getName()))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    store.remove(id, item.getUrl());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.play_all).setOnClickListener(v -> {
            final Playlists.Playlist p = store.get(id);
            if (p != null && !p.videos.isEmpty()) {
                final List<StreamInfoItem> items = p.asItems();
                PlayQueue.set(p.name, items, null);
                PlayerActivity.open(this, items.get(0).getUrl());
            }
        });

        findViewById(R.id.more).setOnClickListener(v -> {
            final PopupMenu menu = new PopupMenu(this, v);
            menu.getMenu().add(0, 1, 0, R.string.playlist_rename);
            menu.getMenu().add(0, 2, 1, R.string.playlist_delete);
            menu.setOnMenuItemClickListener(item -> {
                final Playlists.Playlist p = store.get(id);
                if (p == null) {
                    return true;
                }
                if (item.getItemId() == 1) {
                    PlaylistUi.askName(this, p.name, name -> {
                        store.rename(id, name);
                        refresh();
                    });
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setMessage(getString(R.string.playlist_delete_confirm, p.name))
                            .setPositiveButton(R.string.playlist_delete, (d, w) -> {
                                store.delete(id);
                                finish();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
                return true;
            });
            menu.show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final Playlists.Playlist p = store.get(id);
        if (p == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.title)).setText(p.name);
        ((TextView) findViewById(R.id.info)).setText(
                getString(R.string.playlist_videos, p.videos.size()));
        adapter.set(p.asItems());
        final TextView message = findViewById(R.id.message);
        message.setText(R.string.playlist_empty);
        message.setVisibility(p.videos.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
