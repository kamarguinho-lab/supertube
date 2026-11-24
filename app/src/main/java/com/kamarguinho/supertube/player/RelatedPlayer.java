package com.kamarguinho.supertube.player;

import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.Player;

/**
 * Player que habilita o botão "próximo" dos controles e o liga ao próximo vídeo
 * relacionado (como no YouTube), mesmo tocando um vídeo só.
 */
public class RelatedPlayer extends ForwardingPlayer {

    private final Runnable onNext;
    private final Runnable onPrevious;
    private boolean hasNext = false;
    /** Se verdadeiro, o "anterior" sempre chama {@code onPrevious} (ele decide o que fazer). */
    private boolean delegatePrevious = false;

    public void setDelegatePrevious(final boolean value) {
        delegatePrevious = value;
    }

    public RelatedPlayer(final Player player, final Runnable onNext) {
        this(player, onNext, null);
    }

    /** @param onPrevious chamado no "anterior" (null = só volta ao início do vídeo) */
    public RelatedPlayer(final Player player, final Runnable onNext, final Runnable onPrevious) {
        super(player);
        this.onNext = onNext;
        this.onPrevious = onPrevious;
    }

    @Override
    public void seekToPrevious() {
        // Como no YouTube: depois de 3 s volta ao início; antes disso, vídeo anterior
        if (onPrevious != null && (delegatePrevious || getCurrentPosition() <= 3000)) {
            onPrevious.run();
        } else {
            seekTo(0);
        }
    }

    @Override
    public void seekToPreviousMediaItem() {
        seekToPrevious();
    }

    public void setHasNext(final boolean value) {
        hasNext = value;
    }

    @Override
    public boolean isCommandAvailable(final int command) {
        if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
            return hasNext;
        }
        return super.isCommandAvailable(command);
    }

    @Override
    public Commands getAvailableCommands() {
        return super.getAvailableCommands().buildUpon()
                .addIf(COMMAND_SEEK_TO_NEXT, hasNext)
                .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNext)
                .build();
    }

    @Override
    public boolean hasNextMediaItem() {
        return hasNext || super.hasNextMediaItem();
    }

    @Override
    public void seekToNext() {
        if (hasNext) {
            onNext.run();
        } else {
            super.seekToNext();
        }
    }

    @Override
    public void seekToNextMediaItem() {
        seekToNext();
    }
}
