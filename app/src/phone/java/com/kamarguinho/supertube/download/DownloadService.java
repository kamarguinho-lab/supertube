package com.kamarguinho.supertube.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.kamarguinho.supertube.DownloaderImpl;
import com.kamarguinho.supertube.R;

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Baixa em segundo plano, com aviso na barra de notificações:
 * MP3 (áudio convertido no próprio celular) ou vídeo 720p/1080p (vídeo + som juntados em MP4).
 * Os arquivos vão para Downloads/SuperTube.
 */
public class DownloadService extends Service {

    public static final int MP3 = 0;
    public static final int HD_720 = 720;
    public static final int HD_1080 = 1080;
    public static final int SD = 360;

    private static final String CHANNEL = "downloads";
    private static final int FOREGROUND_ID = 7001;
    private static final long CHUNK = 8L * 1024 * 1024;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(7100);

    private static final class Job {
        final int id = NEXT_ID.incrementAndGet();
        final StreamInfo info;
        final int kind;

        Job(final StreamInfo info, final int kind) {
            this.info = info;
            this.kind = kind;
        }
    }

    private static final java.util.Map<Integer, Job> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Coloca o download na fila (um por vez). */
    public static void start(final Context context, final StreamInfo info, final int kind) {
        final Job job = new Job(info, kind);
        PENDING.put(job.id, job);
        final Intent i = new Intent(context, DownloadService.class).putExtra("job", job.id);
        ContextCompat.startForegroundService(context, i);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger running = new AtomicInteger();
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            final NotificationChannel ch = new NotificationChannel(CHANNEL,
                    getString(R.string.download), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        final Notification n = progress(getString(R.string.download), null, -1);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, n);
        }
        final Job job = intent == null ? null : PENDING.remove(intent.getIntExtra("job", -1));
        if (job == null) {
            if (running.get() == 0) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        running.incrementAndGet();
        worker.execute(() -> {
            try {
                run(job);
            } finally {
                if (running.decrementAndGet() == 0) {
                    main.post(() -> {
                        if (running.get() == 0) {
                            stopForeground(STOP_FOREGROUND_DETACH);
                            stopSelf();
                        }
                    });
                }
            }
        });
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---------- Trabalho ----------

    private void run(final Job job) {
        final String title = job.info.getName();
        final File dir = new File(getCacheDir(), "dl" + job.id);
        dir.mkdirs();
        try {
            final File result;
            final String ext;
            final String mime;
            if (job.kind == MP3) {
                AudioStream audio = DownloadOptions.bestAudio(job.info, true);
                if (audio == null) {
                    audio = DownloadOptions.bestAudio(job.info, false);
                }
                if (audio == null) {
                    throw new IOException(getString(R.string.no_download));
                }
                final File src = new File(dir, "audio");
                fetch(job, audio, src, 0, 70);
                result = new File(dir, "out.mp3");
                toMp3(job, src, result);
                ext = "mp3";
                mime = "audio/mpeg";
            } else if (job.kind == SD) {
                final VideoStream v = DownloadOptions.muxed(job.info);
                if (v == null) {
                    throw new IOException(getString(R.string.no_download));
                }
                result = new File(dir, "out.mp4");
                fetch(job, v, result, 0, 100);
                ext = "mp4";
                mime = "video/mp4";
            } else {
                final VideoStream v = DownloadOptions.videoOnly(job.info, job.kind);
                final AudioStream a = DownloadOptions.bestAudio(job.info, true);
                if (v == null || a == null) {
                    throw new IOException(getString(R.string.no_download));
                }
                final File vf = new File(dir, "video");
                final File af = new File(dir, "audio");
                fetch(job, v, vf, 0, 88);
                fetch(job, a, af, 88, 98);
                notify(job, getString(R.string.download_joining), 99);
                result = new File(dir, "out.mp4");
                mux(vf, af, result);
                vf.delete();
                af.delete();
                ext = "mp4";
                mime = "video/mp4";
            }
            final String suffix = job.kind == MP3 ? "" : " (" + job.kind + "p)";
            final Uri uri = save(result, safeName(title) + suffix + "." + ext, mime);
            done(job, title, uri, mime);
        } catch (final Throwable e) {
            failed(job, title, e);
        } finally {
            deleteAll(dir);
        }
    }

    /** Baixa em pedaços (o YouTube corta a velocidade quando se pede o arquivo inteiro). */
    private void fetch(final Job job, final Stream stream, final File to, final int fromPct,
                       final int toPct) throws IOException {
        final String url = stream.getContent();
        final String ua = YoutubeParsingHelper.isVisionOsStreamingUrl(url)
                ? YoutubeParsingHelper.getVisionOsUserAgent(null)
                : DownloaderImpl.USER_AGENT;
        final OkHttpClient client = DownloaderImpl.getInstance().getClient();
        long total = -1;
        if (stream.getItagItem() != null && stream.getItagItem().getContentLength() > 0) {
            total = stream.getItagItem().getContentLength();
        }
        long done = 0;
        int lastPct = -1;
        final byte[] buf = new byte[64 * 1024];
        try (OutputStream out = new FileOutputStream(to)) {
            while (total < 0 || done < total) {
                final long end = total > 0 ? Math.min(total, done + CHUNK) - 1 : done + CHUNK - 1;
                final Request req = new Request.Builder().url(url)
                        .header("User-Agent", ua)
                        .header("Range", "bytes=" + done + "-" + end)
                        .build();
                long got = 0;
                try (Response r = client.newCall(req).execute()) {
                    if (r.code() == 416 && total < 0) {
                        break;
                    }
                    if (!r.isSuccessful() || r.body() == null) {
                        throw new IOException("HTTP " + r.code());
                    }
                    if (total < 0) {
                        final String cr = r.header("Content-Range");
                        if (cr != null && cr.contains("/") && !cr.endsWith("*")) {
                            total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                        }
                    }
                    final boolean whole = r.code() == 200;
                    try (InputStream in = r.body().byteStream()) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            got += n;
                            done += n;
                            if (total > 0) {
                                final int pct = fromPct
                                        + (int) ((toPct - fromPct) * done / Math.max(1, total));
                                if (pct != lastPct) {
                                    lastPct = pct;
                                    notify(job, job.info.getName(), pct);
                                }
                            }
                        }
                    }
                    if (whole) {
                        break; // servidor mandou tudo de uma vez
                    }
                }
                if (got == 0) {
                    break;
                }
                if (total < 0 && got < CHUNK) {
                    break;
                }
            }
        }
        if (done == 0) {
            throw new IOException("0 bytes");
        }
    }

    /** Decodifica o áudio (AAC/Opus) e grava em MP3 192 kbps. */
    private void toMp3(final Job job, final File src, final File dst) throws IOException {
        final MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(src.getAbsolutePath());
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            final MediaFormat f = ex.getTrackFormat(i);
            final String m = f.getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith("audio/")) {
                track = i;
                fmt = f;
                break;
            }
        }
        if (track < 0) {
            ex.release();
            throw new IOException("audio");
        }
        ex.selectTrack(track);
        final long duration = fmt.containsKey(MediaFormat.KEY_DURATION)
                ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
        final MediaCodec dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        dec.configure(fmt, null, null, 0);
        dec.start();

        Mp3Encoder enc = null;
        int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        boolean isFloat = false;
        int outCh = Math.min(2, channels);
        int sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        short[] pcm = new short[0];
        final String artist = job.info.getUploaderName();
        final MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        int lastPct = -1;
        try (OutputStream out = new FileOutputStream(dst)) {
            while (!outputDone) {
                if (!inputDone) {
                    final int inIdx = dec.dequeueInputBuffer(10_000);
                    if (inIdx >= 0) {
                        final ByteBuffer ib = dec.getInputBuffer(inIdx);
                        final int size = ex.readSampleData(ib, 0);
                        if (size < 0) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            dec.queueInputBuffer(inIdx, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                final int outIdx = dec.dequeueOutputBuffer(bi, 10_000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    final MediaFormat of = dec.getOutputFormat();
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    outCh = Math.min(2, channels);
                    isFloat = Build.VERSION.SDK_INT >= 24
                            && of.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            && of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            == AudioFormat.ENCODING_PCM_FLOAT;
                } else if (outIdx >= 0) {
                    if (bi.size > 0) {
                        if (enc == null) {
                            enc = new Mp3Encoder(sampleRate, outCh, 192, job.info.getName(),
                                    artist);
                        }
                        final ByteBuffer ob = dec.getOutputBuffer(outIdx);
                        ob.position(bi.offset);
                        ob.limit(bi.offset + bi.size);
                        ob.order(ByteOrder.nativeOrder());
                        final int samples = bi.size / (isFloat ? 4 : 2);
                        final int frames = samples / channels;
                        if (pcm.length < frames * outCh) {
                            pcm = new short[frames * outCh];
                        }
                        for (int f = 0; f < frames; f++) {
                            for (int c = 0; c < outCh; c++) {
                                final int idx = f * channels + c;
                                if (isFloat) {
                                    final float v = ob.getFloat(bi.offset + idx * 4);
                                    pcm[f * outCh + c] = (short) Math.max(-32768,
                                            Math.min(32767, Math.round(v * 32767f)));
                                } else {
                                    pcm[f * outCh + c] = ob.getShort(bi.offset + idx * 2);
                                }
                            }
                        }
                        final int n = enc.encode(pcm, frames);
                        if (n > 0) {
                            out.write(enc.out(), 0, n);
                        }
                        if (duration > 0) {
                            final int pct = 70 + (int) (30 * Math.min(1.0,
                                    bi.presentationTimeUs / (double) duration));
                            if (pct != lastPct) {
                                lastPct = pct;
                                notify(job, getString(R.string.download_converting), pct);
                            }
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false);
                    if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
            if (enc == null) {
                throw new IOException("audio");
            }
            final int n = enc.flush();
            if (n > 0) {
                out.write(enc.out(), 0, n);
            }
        } finally {
            if (enc != null) {
                enc.close();
            }
            try {
                dec.stop();
            } catch (final Exception ignored) {
                // já parado
            }
            dec.release();
            ex.release();
        }
    }

    /** Junta o vídeo (sem som) com o áudio num único MP4, sem recodificar. */
    private static void mux(final File video, final File audio, final File dst)
            throws IOException {
        final MediaExtractor ve = new MediaExtractor();
        final MediaExtractor ae = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            ve.setDataSource(video.getAbsolutePath());
            ae.setDataSource(audio.getAbsolutePath());
            final int vt = firstTrack(ve, "video/");
            final int at = firstTrack(ae, "audio/");
            if (vt < 0 || at < 0) {
                throw new IOException("mux");
            }
            ve.selectTrack(vt);
            ae.selectTrack(at);
            muxer = new MediaMuxer(dst.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            final MediaFormat vf = ve.getTrackFormat(vt);
            final MediaFormat af = ae.getTrackFormat(at);
            final int outV = muxer.addTrack(vf);
            final int outA = muxer.addTrack(af);
            muxer.start();
            final ByteBuffer buf = ByteBuffer.allocateDirect(Math.max(4 * 1024 * 1024,
                    Math.max(maxInput(vf), maxInput(af))));
            final MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            copy(ve, muxer, outV, buf, bi);
            copy(ae, muxer, outA, buf, bi);
            muxer.stop();
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (final Exception ignored) {
                    // nada
                }
            }
            ve.release();
            ae.release();
        }
    }

    private static int maxInput(final MediaFormat f) {
        return f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                ? f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 0;
    }

    private static int firstTrack(final MediaExtractor ex, final String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            final String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static void copy(final MediaExtractor ex, final MediaMuxer muxer, final int track,
                             final ByteBuffer buf, final MediaCodec.BufferInfo bi) {
        while (true) {
            buf.clear();
            final int size = ex.readSampleData(buf, 0);
            if (size < 0) {
                break;
            }
            bi.offset = 0;
            bi.size = size;
            bi.presentationTimeUs = ex.getSampleTime();
            bi.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            muxer.writeSampleData(track, buf, bi);
            ex.advance();
        }
    }

    /** Copia para Downloads/SuperTube e devolve o endereço para abrir o arquivo. */
    private Uri save(final File src, final String name, final String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            final ContentResolver cr = getContentResolver();
            final ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/SuperTube");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            final Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new IOException("MediaStore");
            }
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("MediaStore");
                }
                copyStream(in, out);
            } catch (final IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            return uri;
        }
        final File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "SuperTube");
        dir.mkdirs();
        File dst = new File(dir, name);
        int i = 1;
        while (dst.exists()) {
            final int dot = name.lastIndexOf('.');
            dst = new File(dir, name.substring(0, dot) + " (" + i++ + ")" + name.substring(dot));
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            copyStream(in, out);
        }
        MediaScannerConnection.scanFile(this, new String[]{dst.getAbsolutePath()},
                new String[]{mime}, null);
        return FileProvider.getUriForFile(this, getPackageName() + ".files", dst);
    }

    private static void copyStream(final InputStream in, final OutputStream out)
            throws IOException {
        final byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    // ---------- Notificações ----------

    private Notification progress(final String title, @Nullable final String text,
                                  final int pct) {
        final NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, Math.max(0, pct), pct < 0);
        if (text != null) {
            b.setContentText(text);
        }
        return b.build();
    }

    private void notify(final Job job, final String text, final int pct) {
        final String kind = job.kind == MP3 ? "MP3" : job.kind + "p";
        nm.notify(FOREGROUND_ID, progress(kind + " · " + pct + "%", text, pct));
    }

    private void done(final Job job, final String title, final Uri uri, final String mime) {
        final Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pi = PendingIntent.getActivity(this, job.id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(job.id, new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(getString(R.string.download_done))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        main.post(() -> Toast.makeText(this, getString(R.string.download_done) + ": " + title,
                Toast.LENGTH_LONG).show());
    }

    private void failed(final Job job, final String title, final Throwable e) {
        final String msg = getString(R.string.download_failed) + " "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        nm.notify(job.id, new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setAutoCancel(true)
                .build());
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private static void deleteAll(final File f) {
        final File[] kids = f.listFiles();
        if (kids != null) {
            for (final File k : kids) {
                deleteAll(k);
            }
        }
        f.delete();
    }

    static String safeName(final String name) {
        String s = name == null ? "video"
                : name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_").trim();
        if (s.isEmpty()) {
            s = "video";
        }
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
