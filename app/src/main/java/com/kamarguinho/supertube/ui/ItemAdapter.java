package com.kamarguinho.supertube.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.List;

/** Lista de vídeos e canais (resultados de busca, feed, vídeos relacionados). */
public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_STREAM = 0;
    private static final int TYPE_CHANNEL = 1;

    private final List<InfoItem> items = new ArrayList<>();

    public void set(final List<? extends InfoItem> newItems) {
        items.clear();
        addFiltered(newItems);
        notifyDataSetChanged();
    }

    public void add(final List<? extends InfoItem> more) {
        final int start = items.size();
        addFiltered(more);
        notifyItemRangeInserted(start, items.size() - start);
    }

    private void addFiltered(final List<? extends InfoItem> list) {
        for (final InfoItem i : list) {
            if (i instanceof StreamInfoItem || i instanceof ChannelInfoItem) {
                items.add(i);
            }
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(final int position) {
        return items.get(position) instanceof ChannelInfoItem ? TYPE_CHANNEL : TYPE_STREAM;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_CHANNEL) {
            return new ChannelHolder(inflater.inflate(R.layout.item_channel, parent, false));
        }
        return new StreamHolder(inflater.inflate(R.layout.item_stream, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                 final int position) {
        final InfoItem item = items.get(position);
        if (holder instanceof StreamHolder) {
            ((StreamHolder) holder).bind((StreamInfoItem) item);
        } else {
            ((ChannelHolder) holder).bind((ChannelInfoItem) item);
        }
    }

    static class StreamHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView duration;
        final TextView title;
        final TextView subtitle;

        StreamHolder(final View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            duration = v.findViewById(R.id.duration);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
        }

        void bind(final StreamInfoItem item) {
            final Context ctx = itemView.getContext();
            title.setText(item.getName());

            final StringBuilder sub = new StringBuilder();
            if (item.getUploaderName() != null) {
                sub.append(item.getUploaderName());
            }
            if (item.getViewCount() > 0) {
                sub.append(" • ").append(formatCount(item.getViewCount())).append(" views");
            }
            if (item.getTextualUploadDate() != null) {
                sub.append(" • ").append(item.getTextualUploadDate());
            }
            subtitle.setText(sub);

            final boolean live = item.getStreamType() == StreamType.LIVE_STREAM
                    || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
            final String d = live ? "AO VIVO" : Images.duration(item.getDuration());
            duration.setText(d);
            duration.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);

            Glide.with(ctx).load(Images.best(item.getThumbnails()))
                    .placeholder(R.color.thumb_placeholder).into(thumb);

            itemView.setOnClickListener(v -> PlayerActivity.open(ctx, item.getUrl()));
            itemView.setOnLongClickListener(v -> {
                if (item.getUploaderUrl() != null) {
                    ChannelActivity.open(ctx, item.getServiceId(), item.getUploaderUrl());
                } else {
                    Toast.makeText(ctx, R.string.no_channel, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    static class ChannelHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name;
        final TextView info;

        ChannelHolder(final View v) {
            super(v);
            avatar = v.findViewById(R.id.avatar);
            name = v.findViewById(R.id.name);
            info = v.findViewById(R.id.info);
        }

        void bind(final ChannelInfoItem item) {
            final Context ctx = itemView.getContext();
            name.setText(item.getName());
            info.setText(item.getSubscriberCount() > 0
                    ? formatCount(item.getSubscriberCount()) + " inscritos" : "Canal");
            Glide.with(ctx).load(Images.best(item.getThumbnails()))
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.color.thumb_placeholder).into(avatar);
            itemView.setOnClickListener(v ->
                    ChannelActivity.open(ctx, item.getServiceId(), item.getUrl()));
        }
    }

    static String formatCount(final long n) {
        if (n >= 1_000_000_000L) {
            return String.format("%.1f bi", n / 1e9);
        } else if (n >= 1_000_000L) {
            return String.format("%.1f mi", n / 1e6);
        } else if (n >= 1_000L) {
            return String.format("%.1f mil", n / 1e3);
        }
        return String.valueOf(n);
    }
}
