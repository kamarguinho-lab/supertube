package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
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
        subscribeButton = findViewById(R.id.subscribe);
        subscribeButton.setEnabled(false);

        final RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        final ItemAdapter adapter = new ItemAdapter();
        list.setAdapter(adapter);

        subscribeButton.setOnClickListener(v -> toggleSubscription());

        Bg.run(() -> {
            final ChannelInfo c = ChannelInfo.getInfo(NewPipe.getService(serviceId), url);
            List<InfoItem> videos;
            try {
                videos = Feed.channelVideos(c);
            } catch (final Exception e) {
                videos = java.util.Collections.emptyList();
            }
            return new Object[]{c, videos};
        }, new Bg.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(final Object[] r) {
                progress.setVisibility(View.GONE);
                channel = (ChannelInfo) r[0];
                name.setText(channel.getName());
                info.setText(channel.getSubscriberCount() > 0
                        ? ItemAdapter.formatCount(channel.getSubscriberCount()) + " inscritos"
                        : "");
                Glide.with(ChannelActivity.this).load(Images.best(channel.getAvatars()))
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.color.thumb_placeholder).into(avatar);
                subscribeButton.setEnabled(true);
                updateButton();
                final List<InfoItem> videos = (List<InfoItem>) r[1];
                adapter.set(videos);
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
    }
}
