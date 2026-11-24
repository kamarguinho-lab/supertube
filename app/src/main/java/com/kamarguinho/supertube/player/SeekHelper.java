package com.kamarguinho.supertube.player;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;

/**
 * Avançar e voltar como no YouTube: cada toque duplo (celular) ou seta (TV) pula 10
 * segundos, e toques seguidos somam ("20 segundos", "30 segundos"...).
 */
public final class SeekHelper {

    private static final long GROUP_WINDOW_MS = 900;

    private final Player player;
    private final TextView left;
    private final TextView right;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTime = 0;
    private int lastDir = 0;
    private long totalMs = 0;

    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            fadeOut(left);
            fadeOut(right);
            totalMs = 0;
            lastDir = 0;
        }
    };

    public SeekHelper(final Player player, final TextView left, final TextView right) {
        this.player = player;
        this.left = left;
        this.right = right;
        left.setVisibility(View.GONE);
        right.setVisibility(View.GONE);
    }

    /** @param direction -1 volta, +1 avança */
    public void seek(final int direction, final long stepMs) {
        final long now = System.currentTimeMillis();
        if (direction != lastDir || now - lastTime > GROUP_WINDOW_MS) {
            totalMs = 0;
        }
        lastDir = direction;
        lastTime = now;
        totalMs += stepMs;

        long target = player.getCurrentPosition() + direction * stepMs;
        final long duration = player.getDuration();
        if (duration != C.TIME_UNSET && target > duration) {
            target = Math.max(0, duration - 500);
        }
        player.seekTo(Math.max(0, target));

        final long secs = totalMs / 1000;
        final TextView show = direction < 0 ? left : right;
        final TextView other = direction < 0 ? right : left;
        other.setVisibility(View.GONE);
        final String label = show.getContext().getString(
                com.kamarguinho.supertube.R.string.seek_seconds, (int) secs);
        show.setText(direction < 0 ? "◀◀  " + label : label + "  ▶▶");
        show.animate().cancel();
        show.setAlpha(1f);
        show.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, 700);
    }

    private static void fadeOut(final View v) {
        if (v.getVisibility() == View.VISIBLE) {
            v.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> v.setVisibility(View.GONE)).start();
        }
    }
}
