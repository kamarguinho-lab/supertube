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
    private static final int TYPE_PLAYLIST = 2;

    private final List<InfoItem> items = new ArrayList<>();

    /** Clique personalizado em vídeos (ex.: episódios de uma série). */
    public interface OnStreamClick {
        void onClick(StreamInfoItem item);
    }

    OnStreamClick streamClick;

    public void setOnStreamClick(final OnStreamClick listener) {
        streamClick = listener;
    }

    /** Vídeos da lista, na ordem. */
    public List<StreamInfoItem> streams() {
        final List<StreamInfoItem> out = new ArrayList<>();
        for (final InfoItem i : items) {
            if (i instanceof StreamInfoItem) {
                out.add((StreamInfoItem) i);
            }
        }
        return out;
    }

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
            if (i instanceof StreamInfoItem || i instanceof ChannelInfoItem
                    || i instanceof org.schabi.newpipe.extractor.playlist.PlaylistInfoItem) {
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
        final InfoItem i = items.get(position);
        if (i instanceof ChannelInfoItem) {
            return TYPE_CHANNEL;
        }
        return i instanceof org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
                ? TYPE_PLAYLIST : TYPE_STREAM;
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
        if (viewType == TYPE_PLAYLIST) {
            return new PlaylistHolder(inflater.inflate(R.layout.item_stream, parent, false));
        }
        return new StreamHolder(inflater.inflate(R.layout.item_stream, parent, false), this);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                 final int position) {
        final InfoItem item = items.get(position);
        if (holder instanceof StreamHolder) {
            ((StreamHolder) holder).bind((StreamInfoItem) item);
        } else if (holder instanceof PlaylistHolder) {
            ((PlaylistHolder) holder).bind(
                    (org.schabi.newpipe.extractor.playlist.PlaylistInfoItem) item);
        } else {
            ((ChannelHolder) holder).bind((ChannelInfoItem) item);
        }
    }

    static class StreamHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final ImageView avatar;
        final TextView duration;
        final android.widget.ProgressBar watched;
        final TextView title;
        final TextView subtitle;

        final ItemAdapter owner;

        StreamHolder(final View v, final ItemAdapter owner) {
            super(v);
            this.owner = owner;
            thumb = v.findViewById(R.id.thumb);
            avatar = v.findViewById(R.id.avatar);
            watched = v.findViewById(R.id.watch_progress);
            duration = v.findViewById(R.id.duration);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
        }

        void bind(final StreamInfoItem item) {
            final Context ctx = itemView.getContext();
            title.setText(item.getName());

            // Miniatura 16:9 ocupando a largura toda, como no YouTube
            final int width = itemView.getResources().getDisplayMetrics().widthPixels;
            final ViewGroup.LayoutParams lp = thumb.getLayoutParams();
            if (lp.height != width * 9 / 16) {
                lp.height = width * 9 / 16;
                thumb.setLayoutParams(lp);
            }

            final StringBuilder sub = new StringBuilder();
            if (item.getUploaderName() != null) {
                sub.append(item.getUploaderName());
            }
            if (item.getViewCount() > 0) {
                if (sub.length() > 0) {
                    sub.append(" • ");
                }
                sub.append(formatCount(item.getViewCount())).append(" de visualizações");
            }
            if (item.getTextualUploadDate() != null) {
                if (sub.length() > 0) {
                    sub.append(" • ");
                }
                sub.append(item.getTextualUploadDate());
            }
            subtitle.setText(sub);

            final String avatarUrl = Images.best(item.getUploaderAvatars());
            Glide.with(ctx).load(avatarUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.avatar_placeholder).into(avatar);
            avatar.setOnClickListener(v -> {
                if (item.getUploaderUrl() != null) {
                    ChannelActivity.open(ctx, item.getServiceId(), item.getUploaderUrl());
                }
            });

            final boolean live = item.getStreamType() == StreamType.LIVE_STREAM
                    || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
            final String d = live ? "AO VIVO" : Images.duration(item.getDuration());
            duration.setText(d);
            duration.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);

            Glide.with(ctx).load(Images.best(item.getThumbnails()))
                    .placeholder(R.color.thumb_placeholder).into(thumb);

            final float p = com.kamarguinho.supertube.data.History.progress(ctx, item.getUrl());
            watched.setVisibility(p > 0.01f ? View.VISIBLE : View.GONE);
            watched.setProgress((int) (p * 1000));

            itemView.setOnClickListener(v -> {
                if (owner.streamClick != null) {
                    owner.streamClick.onClick(item);
                } else {
                    PlayerActivity.open(ctx, item.getUrl());
                }
            });
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

    /** Série (playlist): miniatura grande com o número de episódios. */
    static class PlaylistHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final ImageView avatar;
        final TextView badge;
        final TextView title;
        final TextView subtitle;

        PlaylistHolder(final View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            avatar = v.findViewById(R.id.avatar);
            badge = v.findViewById(R.id.duration);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            v.findViewById(R.id.watch_progress).setVisibility(View.GONE);
            avatar.setVisibility(View.GONE);
        }

        void bind(final org.schabi.newpipe.extractor.playlist.PlaylistInfoItem item) {
            final Context ctx = itemView.getContext();
            final int width = itemView.getResources().getDisplayMetrics().widthPixels;
            final ViewGroup.LayoutParams lp = thumb.getLayoutParams();
            if (lp.height != width * 9 / 16) {
                lp.height = width * 9 / 16;
                thumb.setLayoutParams(lp);
            }
            title.setText(item.getName());
            subtitle.setText(item.getUploaderName() != null ? item.getUploaderName() : "");
            final long n = item.getStreamCount();
            badge.setText(n > 0 ? "▶ " + n + " episódios" : "Série");
            badge.setVisibility(View.VISIBLE);
            Glide.with(ctx).load(Images.best(item.getThumbnails()))
                    .placeholder(R.color.thumb_placeholder).into(thumb);
            itemView.setOnClickListener(v ->
                    SeriesActivity.open(ctx, item.getUrl(), item.getName()));
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
                    ? formatCount(item.getSubscriberCount()) + " de inscritos" : "Canal");
            Glide.with(ctx).load(Images.best(item.getThumbnails()))
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.color.thumb_placeholder).into(avatar);
            itemView.setOnClickListener(v ->
                    ChannelActivity.open(ctx, item.getServiceId(), item.getUrl()));
        }
    }

    /** Números no estilo "1,2 mi", "350 mil". */
    static String formatCount(final long n) {
        if (n >= 1_000_000_000L) {
            return compact(n / 1e9) + " bi";
        } else if (n >= 1_000_000L) {
            return compact(n / 1e6) + " mi";
        } else if (n >= 1_000L) {
            return compact(n / 1e3) + " mil";
        }
        return String.valueOf(n);
    }

    private static String compact(final double v) {
        if (v >= 100 || Math.abs(v - Math.round(v)) < 0.05) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }
}
