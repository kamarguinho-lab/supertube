package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Series;
import com.kamarguinho.supertube.player.PlayQueue;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/** Página de uma série na TV: episódios em ordem e "Assistir" do primeiro. */
public class TvSeriesActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";

    public static void open(final Context context, final String url, final String title) {
        context.startActivity(new Intent(context, TvSeriesActivity.class)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_channel);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        final String title = getIntent().getStringExtra(EXTRA_TITLE);

        final ImageView banner = findViewById(R.id.banner);
        final ImageView avatar = findViewById(R.id.avatar);
        final TextView name = findViewById(R.id.name);
        final TextView info = findViewById(R.id.info);
        final TextView message = findViewById(R.id.message);
        final View progress = findViewById(R.id.progress);
        final TextView play = findViewById(R.id.subscribe);
        avatar.setVisibility(View.GONE);
        name.setText(title);
        play.setText(R.string.series_play_all);
        TvAdapters.focusZoom(play, 1.06f);

        final RecyclerView grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        final TvAdapters.VideoAdapter adapter = new TvAdapters.VideoAdapter(true);
        grid.setAdapter(adapter);
        final TvAdapters.PagedList paged = new TvAdapters.PagedList(adapter, grid);
        final Series.EpisodesPager pager = new Series.EpisodesPager(url);

        // Ao escolher um episódio, os próximos tocam em sequência
        adapter.clickOverride = item -> {
            PlayQueue.set(title, adapter.items(), pager);
            TvPlayerActivity.open(this, item.getUrl());
        };
        play.setOnClickListener(v -> {
            if (adapter.getItemCount() > 0) {
                adapter.clickOverride.onClick(adapter.items().get(0));
            }
        });
        play.requestFocus();

        // Menu da playlist: Aleatório e Salvar nas minhas playlists
        final TextView shuffle = findViewById(R.id.tv_extra1);
        shuffle.setText(R.string.pl_shuffle);
        shuffle.setVisibility(View.VISIBLE);
        shuffle.setOnClickListener(v -> {
            final java.util.List<StreamInfoItem> eps =
                    new java.util.ArrayList<>(adapter.items());
            if (!eps.isEmpty()) {
                java.util.Collections.shuffle(eps);
                PlayQueue.set(title, eps, null);
                TvPlayerActivity.open(this, eps.get(0).getUrl());
            }
        });
        final TextView save = findViewById(R.id.tv_extra2);
        save.setText(R.string.pl_save_mine);
        save.setVisibility(View.VISIBLE);
        save.setOnClickListener(v -> {
            final androidx.appcompat.app.AlertDialog dialog =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.pl_save_mine)
                            .setMessage(R.string.pl_importing)
                            .setCancelable(false)
                            .show();
            final String plName = pager.info() != null ? pager.info().getName() : title;
            com.kamarguinho.supertube.util.Bg.run(() ->
                            com.kamarguinho.supertube.data.YtImport.importPlaylist(this, url,
                                    plName, text -> runOnUiThread(() -> dialog.setMessage(text))),
                    new com.kamarguinho.supertube.util.Bg.Callback<
                            com.kamarguinho.supertube.data.Playlists.Playlist>() {
                        @Override
                        public void onResult(
                                final com.kamarguinho.supertube.data.Playlists.Playlist p) {
                            dialog.dismiss();
                            android.widget.Toast.makeText(TvSeriesActivity.this,
                                    getString(R.string.pl_imported, p.name, p.videos.size()),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(final Throwable error) {
                            dialog.dismiss();
                            android.widget.Toast.makeText(TvSeriesActivity.this,
                                    Images.errorMessage(error),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
        });

        paged.start(pager, (episodes, error) -> {
            progress.setVisibility(View.GONE);
            final PlaylistInfo p = pager.info();
            if (p != null) {
                name.setText(p.getName());
                final StringBuilder sb = new StringBuilder();
                if (p.getUploaderName() != null) {
                    sb.append(p.getUploaderName());
                }
                if (p.getStreamCount() > 0) {
                    if (sb.length() > 0) {
                        sb.append(" • ");
                    }
                    sb.append(p.getStreamCount()).append(" vídeos");
                }
                info.setText(sb);
                Glide.with(this).load(Images.best(p.getThumbnails())).into(banner);
            }
            if (episodes.isEmpty()) {
                message.setText(error != null
                        ? getString(R.string.error_prefix, Images.errorMessage(error))
                        : getString(R.string.no_results));
                message.setVisibility(View.VISIBLE);
            }
        });
    }
}
