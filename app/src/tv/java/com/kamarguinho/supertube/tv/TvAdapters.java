package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.data.Subscriptions;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adaptadores das listas da TV. */
final class TvAdapters {

    private TvAdapters() {
    }

    /** Cresce um pouco o item focado, como no YouTube da TV. */
    static void focusZoom(final View v, final float scale) {
        v.setOnFocusChangeListener((view, hasFocus) -> {
            final float s = hasFocus ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(150).start();
            view.setElevation(hasFocus ? 8f : 0f);
        });
    }

    static List<StreamInfoItem> streamsOnly(final List<InfoItem> items) {
        final List<StreamInfoItem> out = new ArrayList<>();
        for (final InfoItem i : items) {
            if (i instanceof StreamInfoItem) {
                out.add((StreamInfoItem) i);
            }
        }
        return out;
    }

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
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }

    // ---------------- Cards de vídeo ----------------

    static class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
        private final List<StreamInfoItem> items = new ArrayList<>();
        private final boolean grid;

        VideoAdapter(final boolean grid) {
            this.grid = grid;
        }

        void set(final List<StreamInfoItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        void add(final List<StreamInfoItem> list) {
            final int start = items.size();
            items.addAll(list);
            notifyItemRangeInserted(start, list.size());
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tv_card, parent, false);
            if (grid) {
                final ViewGroup.LayoutParams lp = v.getLayoutParams();
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                v.setLayoutParams(lp);
            }
            focusZoom(v, 1.08f);
            return new Holder(v, grid);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, final int position) {
            h.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final View thumbBox;
            final ImageView thumb;
            final TextView duration;
            final TextView title;
            final TextView subtitle;
            final boolean grid;

            Holder(final View v, final boolean grid) {
                super(v);
                this.grid = grid;
                thumbBox = v.findViewById(R.id.thumb_box);
                thumb = v.findViewById(R.id.thumb);
                duration = v.findViewById(R.id.duration);
                title = v.findViewById(R.id.title);
                subtitle = v.findViewById(R.id.subtitle);
            }

            void bind(final StreamInfoItem item) {
                final Context ctx = itemView.getContext();
                if (grid) {
                    // Mantém 16:9 na grade, onde a largura varia
                    thumbBox.post(() -> {
                        final ViewGroup.LayoutParams lp = thumbBox.getLayoutParams();
                        final int h = thumbBox.getWidth() * 9 / 16;
                        if (h > 0 && lp.height != h) {
                            lp.height = h;
                            thumbBox.setLayoutParams(lp);
                        }
                    });
                }
                title.setText(item.getName());

                final StringBuilder sub = new StringBuilder();
                if (item.getUploaderName() != null) {
                    sub.append(item.getUploaderName()).append('\n');
                }
                if (item.getViewCount() > 0) {
                    sub.append(formatCount(item.getViewCount())).append(" de visualizações");
                }
                if (item.getTextualUploadDate() != null) {
                    if (item.getViewCount() > 0) {
                        sub.append(" • ");
                    }
                    sub.append(item.getTextualUploadDate());
                }
                subtitle.setText(sub.toString().trim());

                final boolean live = item.getStreamType() == StreamType.LIVE_STREAM;
                final String d = live ? "AO VIVO" : Images.duration(item.getDuration());
                duration.setText(d);
                duration.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
                duration.setBackgroundResource(live ? R.drawable.live_bg : R.drawable.duration_bg);

                final int radius = (int) (10 * ctx.getResources().getDisplayMetrics().density);
                Glide.with(ctx).load(Images.best(item.getThumbnails()))
                        .apply(new RequestOptions().transform(new CenterCrop(),
                                new RoundedCorners(radius)))
                        .placeholder(R.drawable.tv_thumb_placeholder)
                        .into(thumb);

                itemView.setOnClickListener(v -> TvPlayerActivity.open(ctx, item.getUrl()));
                itemView.setOnLongClickListener(v -> {
                    if (item.getUploaderUrl() != null) {
                        TvChannelActivity.open(ctx, item.getServiceId(), item.getUploaderUrl());
                    }
                    return true;
                });
            }
        }
    }

    // ---------------- Fileiras da tela inicial ----------------

    static final class Row {
        final String title;
        final VideoAdapter adapter = new VideoAdapter(false);
        boolean loading = true;

        Row(final String title) {
            this.title = title;
        }
    }

    static class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.Holder> {
        private final List<Row> rows = new ArrayList<>();
        private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();

        void set(final List<Row> list) {
            rows.clear();
            rows.addAll(list);
            notifyDataSetChanged();
        }

        void loaded(final Row row) {
            row.loading = false;
            final int i = rows.indexOf(row);
            if (i >= 0) {
                if (row.adapter.getItemCount() == 0) {
                    rows.remove(i);
                    notifyItemRemoved(i);
                } else {
                    notifyItemChanged(i);
                }
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tv_row, parent, false);
            final Holder h = new Holder(v);
            h.list.setLayoutManager(new LinearLayoutManager(parent.getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            h.list.setRecycledViewPool(pool);
            h.list.setItemAnimator(null);
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, final int position) {
            final Row row = rows.get(position);
            h.title.setText(row.title);
            if (h.list.getAdapter() != row.adapter) {
                h.list.setAdapter(row.adapter);
            }
            h.progress.setVisibility(row.loading ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final RecyclerView list;
            final View progress;

            Holder(final View v) {
                super(v);
                title = v.findViewById(R.id.row_title);
                list = v.findViewById(R.id.row_list);
                progress = v.findViewById(R.id.row_progress);
            }
        }
    }

    // ---------------- Canais inscritos ----------------

    static class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
        private final List<Subscriptions.Channel> channels = new ArrayList<>();

        void set(final List<Subscriptions.Channel> list) {
            channels.clear();
            channels.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tv_channel_item, parent, false);
            focusZoom(v, 1.1f);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, final int position) {
            final Subscriptions.Channel c = channels.get(position);
            h.name.setText(c.name);
            Glide.with(h.itemView).load(c.avatar)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.avatar_placeholder).into(h.avatar);
            h.itemView.setOnClickListener(v ->
                    TvChannelActivity.open(v.getContext(), c.serviceId, c.url));
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;

            Holder(final View v) {
                super(v);
                avatar = v.findViewById(R.id.avatar);
                name = v.findViewById(R.id.name);
            }
        }
    }

    // ---------------- Sugestões de busca ----------------

    interface OnSuggestion {
        void onPick(String text);
    }

    static class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.Holder> {
        private final List<String> items = new ArrayList<>();
        private final OnSuggestion listener;

        SuggestionAdapter(final OnSuggestion listener) {
            this.listener = listener;
        }

        void set(final List<String> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tv_suggestion, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, final int position) {
            final String s = items.get(position);
            h.text.setText(s);
            h.itemView.setOnClickListener(v -> listener.onPick(s));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;

            Holder(final View v) {
                super(v);
                text = v.findViewById(R.id.text);
            }
        }
    }
}
