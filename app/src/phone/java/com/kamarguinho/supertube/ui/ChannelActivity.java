package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.ArrayList;
import java.util.List;

/** Página do canal com as abas do YouTube: Vídeos, Shorts, Ao vivo e Playlists. */
public class ChannelActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SERVICE = "service";

    private Subscriptions subscriptions;
    private ChannelInfo channel;
    private MaterialButton subscribeButton;
    private final ItemAdapter adapter = new ItemAdapter();
    private Pagers.TabPager pager;
    private boolean loading = false;
    private int token = 0;
    private TextView message;
    private ProgressBar progress;
    private TextView sectionTitle;
    private MaterialButton importAll;

    public static void open(final Context context, final int serviceId, final String url) {
        context.startActivity(new Intent(context, ChannelActivity.class)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SERVICE, serviceId));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        subscriptions = new Subscriptions(this);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        final int serviceId = getIntent().getIntExtra(EXTRA_SERVICE, 0);

        final ImageView avatar = findViewById(R.id.avatar);
        final TextView name = findViewById(R.id.name);
        final TextView info = findViewById(R.id.info);
        final ImageView banner = findViewById(R.id.banner);
        final TextView topTitle = findViewById(R.id.top_title);
        progress = findViewById(R.id.progress);
        message = findViewById(R.id.message);
        sectionTitle = findViewById(R.id.section_title);
        sectionTitle.setVisibility(View.GONE);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        subscribeButton = findViewById(R.id.subscribe);
        subscribeButton.setEnabled(false);
        subscribeButton.setOnClickListener(v -> toggleSubscription());

        importAll = findViewById(R.id.extra1);
        importAll.setText(R.string.pl_import_all);
        importAll.setIconResource(R.drawable.ic_playlist_add);
        importAll.setOnClickListener(v -> importAllPlaylists());

        final RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // Rolagem sem fim dentro da aba aberta
        final NestedScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> {
                    final View child = v.getChildAt(0);
                    if (child != null && y + v.getHeight() >= child.getHeight() - 1500) {
                        more();
                    }
                });

        Bg.run(() -> ChannelInfo.getInfo(NewPipe.getService(serviceId), url),
                new Bg.Callback<ChannelInfo>() {
                    @Override
                    public void onResult(final ChannelInfo c) {
                        channel = c;
                        name.setText(c.getName());
                        topTitle.setText(c.getName());
                        info.setText(c.getSubscriberCount() > 0
                                ? getString(R.string.subs_count,
                                ItemAdapter.formatCount(c.getSubscriberCount())) : "");
                        Glide.with(ChannelActivity.this).load(Images.best(c.getAvatars()))
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.color.thumb_placeholder).into(avatar);
                        final String bannerUrl = Images.best(c.getBanners());
                        if (bannerUrl != null) {
                            banner.setVisibility(View.VISIBLE);
                            Glide.with(ChannelActivity.this).load(bannerUrl).into(banner);
                        }
                        subscribeButton.setEnabled(true);
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
        final ChipGroup group = findViewById(R.id.tabs);
        final List<ListLinkHandler> tabs = new ArrayList<>();
        for (final ListLinkHandler t : c.getTabs()) {
            final String label = Pagers.tabName(t);
            if (label == null) {
                continue;
            }
            tabs.add(t);
            final Chip chip = (Chip) LayoutInflater.from(this)
                    .inflate(R.layout.chip_category, group, false);
            chip.setId(View.generateViewId());
            chip.setText(label);
            chip.setTag(t);
            group.addView(chip);
        }
        if (tabs.isEmpty()) {
            progress.setVisibility(View.GONE);
            message.setText(R.string.no_results);
            message.setVisibility(View.VISIBLE);
            return;
        }
        findViewById(R.id.tabs_scroll).setVisibility(View.VISIBLE);
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (!ids.isEmpty()) {
                final Chip chip = g.findViewById(ids.get(0));
                if (chip != null) {
                    openTab((ListLinkHandler) chip.getTag());
                }
            }
        });
        ((Chip) group.getChildAt(0)).setChecked(true);
    }

    private void openTab(final ListLinkHandler tab) {
        final int t = ++token;
        importAll.setVisibility(Pagers.isPlaylistsTab(tab) ? View.VISIBLE : View.GONE);
        adapter.clear();
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
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
                adapter.set(items);
                if (adapter.getItemCount() == 0) {
                    message.setText(Pagers.isPlaylistsTab(tab)
                            ? R.string.pl_no_playlists : R.string.no_results);
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
                    adapter.add(items);
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

    /** Importa todas as playlists do canal para as playlists do SuperTube. */
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
                        Toast.makeText(ChannelActivity.this,
                                n > 0 ? getString(R.string.pl_imported_many, n)
                                        : getString(R.string.pl_no_playlists),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        dialog.dismiss();
                        Toast.makeText(ChannelActivity.this, Images.errorMessage(error),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void toggleSubscription() {
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
        final boolean subscribed = channel != null
                && subscriptions.isSubscribed(channel.getUrl());
        subscribeButton.setText(subscribed ? R.string.unsubscribe : R.string.subscribe);
        subscribeButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this, subscribed ? R.color.chip : R.color.pill_light)));
        subscribeButton.setTextColor(ContextCompat.getColor(this,
                subscribed ? R.color.text_primary : R.color.on_pill_light));
    }
}
