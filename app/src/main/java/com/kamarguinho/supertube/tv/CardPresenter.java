package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.kamarguinho.supertube.R;
import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

/** Card de vídeo grande, para navegar com o controle remoto. */
public class CardPresenter extends Presenter {

    private static final int CARD_WIDTH_DP = 320;
    private static final int CARD_HEIGHT_DP = 180;

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent) {
        final Context ctx = parent.getContext();
        final ImageCardView card = new ImageCardView(ctx);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        final float d = ctx.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions((int) (CARD_WIDTH_DP * d), (int) (CARD_HEIGHT_DP * d));
        card.setInfoAreaBackgroundColor(ContextCompat.getColor(ctx, R.color.surface));
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final Object item) {
        final ImageCardView card = (ImageCardView) viewHolder.view;
        final StreamInfoItem video = (StreamInfoItem) item;
        card.setTitleText(video.getName());

        final StringBuilder sub = new StringBuilder();
        if (video.getUploaderName() != null) {
            sub.append(video.getUploaderName());
        }
        final boolean live = video.getStreamType() == StreamType.LIVE_STREAM;
        final String duration = live ? "AO VIVO" : Images.duration(video.getDuration());
        if (!duration.isEmpty()) {
            if (sub.length() > 0) {
                sub.append(" • ");
            }
            sub.append(duration);
        }
        if (video.getTextualUploadDate() != null) {
            sub.append(" • ").append(video.getTextualUploadDate());
        }
        card.setContentText(sub);

        Glide.with(card.getContext())
                .load(Images.best(video.getThumbnails()))
                .placeholder(R.color.thumb_placeholder)
                .centerCrop()
                .into(card.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(final ViewHolder viewHolder) {
        final ImageCardView card = (ImageCardView) viewHolder.view;
        Glide.with(card.getContext()).clear(card.getMainImageView());
        card.setMainImage(null);
    }
}
