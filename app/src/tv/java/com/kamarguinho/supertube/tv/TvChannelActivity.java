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
import com.bumptech.glide.request.RequestOptions;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Pagers;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Bg;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;

/** Página do canal na TV: cabeçalho, botão de inscrição e grade de vídeos. */
public class TvChannelActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_SERVICE = "service";

    private ChannelInfo channel;
    private Subscriptions subscriptions;
    private TextView subscribe;

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
        final TextView message = findViewById(R.id.message);
        final View progress = findViewById(R.id.progress);
        subscribe = findViewById(R.id.subscribe);
        subscribe.setOnClickListener(v -> toggle());
        TvAdapters.focusZoom(subscribe, 1.06f);

        final RecyclerView grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        final TvAdapters.VideoAdapter adapter = new TvAdapters.VideoAdapter(true);
        grid.setAdapter(adapter);
        // Vai carregando mais vídeos do canal enquanto desce
        final TvAdapters.PagedList paged = new TvAdapters.PagedList(adapter, grid);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        final int serviceId = getIntent().getIntExtra(EXTRA_SERVICE, 0);
        subscribe.requestFocus();

        Bg.run(() -> ChannelInfo.getInfo(NewPipe.getService(serviceId), url),
                new Bg.Callback<ChannelInfo>() {
            @Override
            public void onResult(final ChannelInfo c) {
                channel = c;
                name.setText(channel.getName());
                info.setText(channel.getSubscriberCount() > 0
                        ? getString(R.string.subs_count,
                        TvAdapters.formatCount(channel.getSubscriberCount())) : "");
                Glide.with(TvChannelActivity.this).load(Images.best(channel.getAvatars()))
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.avatar_placeholder).into(avatar);
                Glide.with(TvChannelActivity.this).load(Images.best(channel.getBanners()))
                        .into(banner);
                updateButton();
                paged.start(new Pagers.ChannelPager(c), (videos, error) -> {
                    progress.setVisibility(View.GONE);
                    if (videos.isEmpty()) {
                        message.setText(error != null
                                ? getString(R.string.error_prefix, Images.errorMessage(error))
                                : getString(R.string.no_results));
                        message.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(final Throwable error) {
                progress.setVisibility(View.GONE);
                message.setText(getString(R.string.error_prefix, Images.errorMessage(error)));
                message.setVisibility(View.VISIBLE);
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
