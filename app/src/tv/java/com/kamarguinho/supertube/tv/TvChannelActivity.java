package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Pagers;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.data.YtImport;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/** Página do canal na TV: abas Vídeos, Ao vivo e Playlists, como no YouTube. */
public class TvChannelActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SERVICE = "service";

    private ChannelInfo channel;
    private Subscriptions subscriptions;
    private TextView subscribe;
    private RecyclerView grid;
    private TextView message;
    private View progress;
    private TextView importAll;
    private final List<TextView> tabPills = new ArrayList<>();

    private Pagers.TabPager pager;
    private boolean playlistsTab = false;
    private boolean loading = false;
    private int token = 0;
    private TvAdapters.VideoAdapter videos;
    private TvAdapters.SeriesAdapter lists;

    public static void open(final Context context, final int serviceId, final String url) {
        context.startActivity(new Intent(context, TvChannelActivity.class)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_SERVICE, serviceId));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_channel);
        subscriptions = new Subscriptions(this);

        final ImageView banner = findViewById(R.id.banner);
        final ImageView avatar = findViewById(R.id.avatar);
        final TextView name = findViewById(R.id.name);
        final TextView info = findViewById(R.id.info);
        message = findViewById(R.id.message);
        progress = findViewById(R.id.progress);
        subscribe = findViewById(R.id.subscribe);
        subscribe.setOnClickListener(v -> toggle());
        TvAdapters.focusZoom(subscribe, 1.06f);

        importAll = findViewById(R.id.tv_extra1);
        importAll.setText(R.string.pl_import_all);
        importAll.setOnClickListener(v -> importAllPlaylists());

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        TvAdapters.endless(grid, this::more);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        final int serviceId = getIntent().getIntExtra(EXTRA_SERVICE, 0);
        subscribe.requestFocus();

        Bg.run(() -> ChannelInfo.getInfo(NewPipe.getService(serviceId), url),
                new Bg.Callback<ChannelInfo>() {
                    @Override
                    public void onResult(final ChannelInfo c) {
                        channel = c;
                        name.setText(c.getName());
                        info.setText(c.getSubscriberCount() > 0
                                ? getString(R.string.subs_count,
                                TvAdapters.formatCount(c.getSubscriberCount())) : "");
                        Glide.with(TvChannelActivity.this).load(Images.best(c.getAvatars()))
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.drawable.avatar_placeholder).into(avatar);
                        Glide.with(TvChannelActivity.this).load(Images.best(c.getBanners()))
                                .into(banner);
                        updateButton();
                        buildTabs(c);
                    }

                    @Override
                    public void onError(final Throwable error) {
                        progress.setVisibility(View.GONE);
                        message.setText(getString(R.string.error_prefix,
                                Images.errorMessage(error)));
                        message.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void buildTabs(final ChannelInfo c) {
        final LinearLayout box = findViewById(R.id.tv_tabs);
        int position = 0;
        ListLinkHandler first = null;
        for (final ListLinkHandler t : c.getTabs()) {
            final String label = Pagers.tabName(t);
            // Shorts são vídeos de celular: não aparecem na TV
            if (label == null || t.getContentFilters().contains(ChannelTabs.SHORTS)) {
                continue;
            }
            final TextView pill = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.tv_pill, box, false);
            pill.setText(label);
            pill.setOnClickListener(v -> {
                for (final TextView p : tabPills) {
                    p.setSelected(p == pill);
                }
                openTab(t);
            });
            box.addView(pill, position++);
            tabPills.add(pill);
            if (first == null) {
                first = t;
            }
        }
        if (first == null) {
            progress.setVisibility(View.GONE);
            message.setText(R.string.no_results);
            message.setVisibility(View.VISIBLE);
            return;
        }
        tabPills.get(0).setSelected(true);
        openTab(first);
    }

    private void openTab(final ListLinkHandler tab) {
        final int t = ++token;
        playlistsTab = Pagers.isPlaylistsTab(tab);
        importAll.setVisibility(playlistsTab ? View.VISIBLE : View.GONE);
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        if (playlistsTab) {
            lists = new TvAdapters.SeriesAdapter();
            grid.setAdapter(lists);
        } else {
            videos = new TvAdapters.VideoAdapter(true);
            grid.setAdapter(videos);
        }
        pager = new Pagers.TabPager(channel.getServiceId(), tab);
        loading = true;
        final Pagers.TabPager p = pager;
        Bg.run(p::next, new Bg.Callback<List<InfoItem>>() {
            @Override
            public void onResult(final List<InfoItem> items) {
                if (t != token) {
                    return;
                }
                loading = false;
                progress.setVisibility(View.GONE);
                final int count = add(items);
                if (count == 0) {
                    message.setText(playlistsTab ? R.string.pl_no_playlists
                            : R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (t != token) {
                    return;
                }
                loading = false;
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Põe os itens na grade certa. Retorna quantos entraram. */
    private int add(final List<InfoItem> items) {
        if (playlistsTab) {
            final List<PlaylistInfoItem> out = new ArrayList<>();
            for (final InfoItem i : items) {
                if (i instanceof PlaylistInfoItem) {
                    out.add((PlaylistInfoItem) i);
                }
            }
            lists.add(out);
            return out.size();
        }
        final List<StreamInfoItem> out = TvAdapters.streamsOnly(items);
        videos.add(out);
        return out.size();
    }

    private void more() {
        final Pagers.TabPager p = pager;
        if (p == null || loading || !p.hasMore()) {
            return;
        }
        loading = true;
        final int t = token;
        Bg.run(p::next, new Bg.Callback<List<InfoItem>>() {
            @Override
            public void onResult(final List<InfoItem> items) {
                if (t == token) {
                    loading = false;
                    add(items);
                }
            }

            @Override
            public void onError(final Throwable error) {
                if (t == token) {
                    loading = false;
                }
            }
        });
    }

    private void importAllPlaylists() {
        if (channel == null) {
            return;
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pl_import_all)
                .setMessage(R.string.pl_importing)
                .setCancelable(false)
                .show();
        final ChannelInfo c = channel;
        Bg.run(() -> YtImport.importChannelPlaylists(this, c, text ->
                        runOnUiThread(() -> dialog.setMessage(text))),
                new Bg.Callback<Integer>() {
                    @Override
                    public void onResult(final Integer n) {
                        dialog.dismiss();
                        Toast.makeText(TvChannelActivity.this,
                                n > 0 ? getString(R.string.pl_imported_many, n)
                                        : getString(R.string.pl_no_playlists),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        dialog.dismiss();
                        Toast.makeText(TvChannelActivity.this, Images.errorMessage(error),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void toggle() {
        if (channel == null) {
            return;
        }
        if (subscriptions.isSubscribed(channel.getUrl())) {
            subscriptions.remove(channel.getUrl());
        } else {
            subscriptions.add(new Subscriptions.Channel(channel.getServiceId(), channel.getUrl(),
                    channel.getName(), Images.best(channel.getAvatars())));
        }
        updateButton();
    }

    private void updateButton() {
        final boolean sub = channel != null && subscriptions.isSubscribed(channel.getUrl());
        subscribe.setText(sub ? R.string.unsubscribe : R.string.subscribe);
    }
}
