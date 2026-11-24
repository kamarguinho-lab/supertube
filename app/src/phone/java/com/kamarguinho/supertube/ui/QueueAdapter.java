package com.kamarguinho.supertube.ui;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.kamarguinho.supertube.R;

import java.util.ArrayList;
import java.util.List;

/** Lista compacta: músicas da playlist ou capítulos do vídeo, com a atual destacada. */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.Holder> {

    public static final class Row {
        final String title;
        final String subtitle;
        final String thumb;

        public Row(final String title, final String subtitle, final String thumb) {
            this.title = title;
            this.subtitle = subtitle;
            this.thumb = thumb;
        }
    }

    public interface OnPick {
        void onPick(int position);
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnPick listener;
    private int current = -1;

    public QueueAdapter(final OnPick listener) {
        this.listener = listener;
    }

    public void set(final List<Row> list, final int currentIndex) {
        rows.clear();
        rows.addAll(list);
        current = currentIndex;
        notifyDataSetChanged();
    }

    public void setCurrent(final int index) {
        if (index == current) {
            return;
        }
        final int old = current;
        current = index;
        if (old >= 0 && old < rows.size()) {
            notifyItemChanged(old);
        }
        if (index >= 0 && index < rows.size()) {
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder h, final int position) {
        final Row r = rows.get(position);
        final boolean now = position == current;
        h.index.setText(now ? "▶" : String.valueOf(position + 1));
        h.index.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                now ? R.color.brand : R.color.text_secondary));
        h.title.setText(r.title);
        h.title.setTypeface(null, now ? Typeface.BOLD : Typeface.NORMAL);
        h.subtitle.setText(r.subtitle == null ? "" : r.subtitle);
        h.itemView.setBackgroundColor(now ? 0x22FFFFFF : 0x00000000);
        if (r.thumb != null) {
            h.thumbBox.setVisibility(View.VISIBLE);
            Glide.with(h.itemView).load(r.thumb).placeholder(R.color.thumb_placeholder)
                    .into(h.thumb);
        } else {
            h.thumbBox.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onPick(h.getBindingAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView index;
        final View thumbBox;
        final ImageView thumb;
        final TextView title;
        final TextView subtitle;

        Holder(final View v) {
            super(v);
            index = v.findViewById(R.id.index);
            thumbBox = v.findViewById(R.id.thumb_box);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
        }
    }
}
