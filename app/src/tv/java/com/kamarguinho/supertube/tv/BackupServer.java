package com.kamarguinho.supertube.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.kamarguinho.supertube.data.Backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

/**
 * Pequeno site dentro da TV, só na rede de casa (Wi-Fi): o celular abre o endereço no
 * navegador para baixar o backup da TV ou enviar um backup para ela.
 * Nada passa pela internet.
 */
final class BackupServer {

    interface Listener {
        void onRestored(int count);

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private Thread thread;

    BackupServer(final Context context, final Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Liga o servidor e devolve o endereço (ex.: http://192.168.1.20:8080). */
    String start() throws IOException {
        IOException last = null;
        for (int port = 8080; port < 8090; port++) {
            try {
                server = new ServerSocket(port);
                break;
            } catch (final IOException e) {
                last = e;
            }
        }
        if (server == null) {
            throw last != null ? last : new IOException("porta");
        }
        thread = new Thread(this::loop, "backup-server");
        thread.start();
        final String ip = localIp();
        if (ip == null) {
            throw new IOException("Wi-Fi");
        }
        return "http://" + ip + ":" + server.getLocalPort();
    }

    void stop() {
        try {
            if (server != null) {
                server.close();
            }
        } catch (final IOException ignored) {
            // já fechado
        }
    }

    private void loop() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(20_000);
                handle(s);
            } catch (final IOException ignored) {
                // conexão encerrada
            }
        }
    }

    private void handle(final Socket s) throws IOException {
        final InputStream in = s.getInputStream();
        final String head = readHead(in);
        if (head == null) {
            return;
        }
        final String first = head.split("\r\n", 2)[0];
        final String[] parts = first.split(" ");
        final String method = parts.length > 0 ? parts[0] : "";
        final String path = parts.length > 1 ? parts[1] : "/";
        final OutputStream out = s.getOutputStream();
        try {
            if ("GET".equals(method) && path.startsWith("/" + Backup.fileName())) {
                final byte[] body = Backup.toJson(context).getBytes(StandardCharsets.UTF_8);
                send(out, "200 OK", "application/json", body,
                        "Content-Disposition: attachment; filename=\"" + Backup.fileName()
                                + "\"\r\n");
            } else if ("POST".equals(method) && path.startsWith("/restore")) {
                final int len = contentLength(head);
                if (len <= 0 || len > 30 * 1024 * 1024) {
                    throw new IOException("tamanho");
                }
                final byte[] body = readN(in, len);
                final String json = extractJson(new String(body, StandardCharsets.UTF_8));
                final int count = Backup.restore(context, json);
                send(out, "200 OK", "text/html; charset=utf-8",
                        page(texts()[6]).getBytes(StandardCharsets.UTF_8), "");
                main.post(() -> listener.onRestored(count));
            } else {
                send(out, "200 OK", "text/html; charset=utf-8",
                        page(null).getBytes(StandardCharsets.UTF_8), "");
            }
        } catch (final Exception e) {
            final String msg = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage();
            send(out, "200 OK", "text/html; charset=utf-8",
                    page("⚠ " + msg).getBytes(StandardCharsets.UTF_8), "");
            main.post(() -> listener.onError(msg));
        }
    }

    /** O arquivo vem dentro do formulário: pega só o JSON. */
    private static String extractJson(final String body) {
        final int a = body.indexOf('{');
        final int b = body.lastIndexOf('}');
        if (a < 0 || b <= a) {
            throw new IllegalArgumentException("arquivo vazio");
        }
        return body.substring(a, b + 1);
    }

    private static String[] texts() {
        final boolean en = com.kamarguinho.supertube.util.Lang.english();
        return en ? new String[]{"SuperTube TV backup", "1. Save the TV backup",
                "Download backup", "2. Send a backup to the TV",
                "Choose the file SuperTube-backup.json and tap Send.", "Send to TV",
                "Done! The backup is on the TV."}
                : new String[]{"Backup do SuperTube TV", "1. Guardar o backup da TV",
                "Baixar backup", "2. Enviar um backup para a TV",
                "Escolha o arquivo SuperTube-backup.json e toque em Enviar.", "Enviar para a TV",
                "Pronto! O backup chegou na TV."};
    }

    private static String page(final String notice) {
        final String[] t = texts();
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + t[0] + "</title><style>body{font-family:sans-serif;background:#0f0f0f;"
                + "color:#fff;padding:20px;max-width:520px;margin:auto}"
                + "a,button{display:block;background:#f1f1f1;color:#0f0f0f;border:0;"
                + "border-radius:24px;padding:14px;text-align:center;font-size:17px;"
                + "text-decoration:none;margin:12px 0;width:100%}"
                + "input{margin:12px 0;color:#fff}.n{background:#272727;padding:14px;"
                + "border-radius:12px}</style></head><body><h2>" + t[0] + "</h2>"
                + (notice != null ? "<p class='n'>" + escape(notice) + "</p>" : "")
                + "<h3>" + t[1] + "</h3><a href='/" + Backup.fileName() + "'>" + t[2] + "</a>"
                + "<h3>" + t[3] + "</h3><p>" + t[4] + "</p>"
                + "<form method='post' action='/restore' enctype='multipart/form-data'>"
                + "<input type='file' name='f' accept='.json,application/json' required>"
                + "<button type='submit'>" + t[5] + "</button></form></body></html>";
    }

    private static String escape(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void send(final OutputStream out, final String status, final String type,
                             final byte[] body, final String extra) throws IOException {
        final String h = "HTTP/1.1 " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\n" + extra
                + "Cache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String readHead(final InputStream in) throws IOException {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        int c;
        while ((c = in.read()) >= 0) {
            b.write(c);
            if (b.size() > 64 * 1024) {
                return null;
            }
            state = (c == '\r' && (state == 0 || state == 2)) ? state + 1
                    : (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            if (state == 4) {
                return b.toString("UTF-8");
            }
        }
        return null;
    }

    private static int contentLength(final String head) {
        for (final String line : head.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.substring(15).trim());
                } catch (final NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static byte[] readN(final InputStream in, final int n) throws IOException {
        final byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            final int r = in.read(buf, off, n - off);
            if (r < 0) {
                break;
            }
            off += r;
        }
        return buf;
    }

    private static String localIp() {
        try {
            for (final NetworkInterface ni : Collections.list(
                    NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (final InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (final Exception ignored) {
            // sem rede
        }
        return null;
    }
}
