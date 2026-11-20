package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Series;
import com.kamarguinho.supertube.player.PlayQueue;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** Página de uma série: episódios em ordem e "Assistir do início". */
public class SeriesActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";

    private Series.EpisodesPager pager;
    private boolean loading = false;
    private final ItemAdapter adapter = new ItemAdapter();

    public static void open(final Context context, final String url, final String title) {
        context.startActivity(new Intent(context, SeriesActivity.class)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        final String title = getIntent().getStringExtra(EXTRA_TITLE);

        final ImageView banner = findViewById(R.id.banner);
        final TextView name = findViewById(R.id.name);
        final TextView info = findViewById(R.id.info);
        final TextView topTitle = findViewById(R.id.top_title);
        final TextView message = findViewById(R.id.message);
        final View progress = findViewById(R.id.progress);
        final MaterialButton play = findViewById(R.id.subscribe);
        findViewById(R.id.avatar).setVisibility(View.GONE);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        name.setText(title);
        topTitle.setText(title);
        play.setText(R.string.series_play);
        play.setEnabled(false);

        final RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        pager = new Series.EpisodesPager(url);
        // Ao escolher um episódio, os próximos tocam em sequência
        adapter.setOnStreamClick(item -> {
            PlayQueue.set(title, adapter.streams(), pager);
            PlayerActivity.open(this, item.getUrl());
        });
        play.setOnClickListener(v -> {
            final List<StreamInfoItem> eps = adapter.streams();
            if (!eps.isEmpty()) {
                PlayQueue.set(title, eps, pager);
                PlayerActivity.open(this, eps.get(0).getUrl());
            }
        });

        final NestedScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> {
                    final View child = v.getChildAt(0);
                    if (child != null && y + v.getHeight() >= child.getHeight() - 1500) {
                        more();
                    }
                });

        loading = true;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> episodes) {
                loading = false;
                progress.setVisibility(View.GONE);
                adapter.set(episodes);
                play.setEnabled(!episodes.isEmpty());
                final PlaylistInfo p = pager.info();
                if (p != null) {
                    name.setText(p.getName());
                    topTitle.setText(p.getName());
                    final StringBuilder sb = new StringBuilder();
                    if (p.getUploaderName() != null) {
                        sb.append(p.getUploaderName());
                    }
                    if (p.getStreamCount() > 0) {
                        if (sb.length() > 0) {
                            sb.append(" • ");
                        }
                        sb.append(p.getStreamCount()).append(" episódios");
                    }
                    info.setText(sb);
                    final String b = Images.best(p.getThumbnails());
                    if (b != null) {
                        banner.setVisibility(View.VISIBLE);
                        Glide.with(SeriesActivity.this).load(b).into(banner);
                    }
                }
                if (episodes.isEmpty()) {
                    message.setText(R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                loading = false;
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void more() {
        if (loading || !pager.hasMore()) {
            return;
        }
        loading = true;
        Bg.run(pager::next, new Bg.Callback<List<StreamInfoItem>>() {
            @Override
            public void onResult(final List<StreamInfoItem> episodes) {
                loading = false;
                adapter.add(episodes);
            }

            @Override
            public void onError(final Throwable error) {
                loading = false;
            }
        });
    }
}
