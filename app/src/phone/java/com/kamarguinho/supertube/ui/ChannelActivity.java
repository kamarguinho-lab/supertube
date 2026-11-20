package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Feed;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;

import java.util.List;

public class ChannelActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SERVICE = "service";

    private Subscriptions subscriptions;
    private ChannelInfo channel;
    private MaterialButton subscribeButton;

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
        final ProgressBar progress = findViewById(R.id.progress);
        final TextView message = findViewById(R.id.message);
        final ImageView banner = findViewById(R.id.banner);
        final TextView topTitle = findViewById(R.id.top_title);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        subscribeButton = findViewById(R.id.subscribe);
        subscribeButton.setEnabled(false);

        final RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        final ItemAdapter adapter = new ItemAdapter();
        list.setAdapter(adapter);

        subscribeButton.setOnClickListener(v -> toggleSubscription());

        // Rolagem sem fim: perto do fim da página, carrega mais vídeos do canal
        final androidx.core.widget.NestedScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> {
                    final View child = v.getChildAt(0);
                    if (child != null && y + v.getHeight() >= child.getHeight() - 1500) {
                        loadMoreVideos(adapter);
                    }
                });

        Bg.run(() -> {
            final ChannelInfo c = ChannelInfo.getInfo(NewPipe.getService(serviceId), url);
            final com.kamarguinho.supertube.data.Pagers.ChannelPager p =
                    new com.kamarguinho.supertube.data.Pagers.ChannelPager(c);
            List<? extends InfoItem> videos;
            try {
                videos = p.next();
            } catch (final Exception e) {
                videos = java.util.Collections.emptyList();
            }
            return new Object[]{c, videos, p};
        }, new Bg.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(final Object[] r) {
                progress.setVisibility(View.GONE);
                channel = (ChannelInfo) r[0];
                name.setText(channel.getName());
                topTitle.setText(channel.getName());
                info.setText(channel.getSubscriberCount() > 0
                        ? getString(R.string.subs_count,
                        ItemAdapter.formatCount(channel.getSubscriberCount()))
                        : "");
                final String bannerUrl = Images.best(channel.getBanners());
                if (bannerUrl != null) {
                    banner.setVisibility(View.VISIBLE);
                    Glide.with(ChannelActivity.this).load(bannerUrl).into(banner);
                }
                Glide.with(ChannelActivity.this).load(Images.best(channel.getAvatars()))
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.color.thumb_placeholder).into(avatar);
                subscribeButton.setEnabled(true);
                updateButton();
                final List<InfoItem> videos = (List<InfoItem>) r[1];
                adapter.set(videos);
                pager = (com.kamarguinho.supertube.data.Pagers.Pager) r[2];
                if (videos.isEmpty()) {
                    message.setText(R.string.no_results);
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(final Throwable error) {
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private com.kamarguinho.supertube.data.Pagers.Pager pager;
    private boolean loadingMore = false;

    private void loadMoreVideos(final ItemAdapter adapter) {
        final com.kamarguinho.supertube.data.Pagers.Pager p = pager;
        if (p == null || loadingMore || !p.hasMore()) {
            return;
        }
        loadingMore = true;
        Bg.run(p::next, new Bg.Callback<List<org.schabi.newpipe.extractor.stream.StreamInfoItem>>() {
            @Override
            public void onResult(
                    final List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items) {
                loadingMore = false;
                adapter.add(items);
            }

            @Override
            public void onError(final Throwable error) {
                loadingMore = false;
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
