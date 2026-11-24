package com.kamarguinho.supertube.download;

/** Codificador MP3 (biblioteca LAME, compilada junto com o app). */
final class Mp3Encoder implements AutoCloseable {

    static {
        System.loadLibrary("supertube_mp3");
    }

    private long handle;
    private final int channels;
    private byte[] out = new byte[16 * 1024];

    Mp3Encoder(final int sampleRate, final int channels, final int kbps,
               final String title, final String artist) {
        this.channels = channels;
        handle = nativeInit(sampleRate, channels, kbps, title, artist);
        if (handle == 0) {
            throw new IllegalStateException("MP3 encoder");
        }
    }

    /** {@code pcm} intercalado, {@code frames} amostras por canal. Devolve o tamanho em out(). */
    int encode(final short[] pcm, final int frames) {
        final int need = (int) (1.25 * frames) + 7200;
        if (out.length < need) {
            out = new byte[need];
        }
        return nativeEncode(handle, pcm, frames, channels, out);
    }

    int flush() {
        if (out.length < 7200) {
            out = new byte[7200];
        }
        return nativeFlush(handle, out);
    }

    byte[] out() {
        return out;
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
        }
    }

    private static native long nativeInit(int sampleRate, int channels, int kbps,
                                          String title, String artist);

    private static native int nativeEncode(long handle, short[] pcm, int frames, int channels,
                                           byte[] out);

    private static native int nativeFlush(long handle, byte[] out);

    private static native void nativeClose(long handle);
}
