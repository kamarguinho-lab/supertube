package com.kamarguinho.supertube.data;

import android.content.Context;

import com.kamarguinho.supertube.util.Images;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Importa playlists do YouTube para as playlists do SuperTube. */
public final class YtImport {

    public interface Progress {
        void onProgress(String text);
    }

    private static final int MAX_VIDEOS = 1000;
    private static final int MAX_PLAYLISTS = 60;
    private static final Pattern LIST_ID = Pattern.compile("[?&]list=([\\w-]+)");

    private YtImport() {
    }

    /** É um link de playlist do YouTube? Retorna o link padrão ou null. */
    public static String playlistUrl(final String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = LIST_ID.matcher(text);
        if (!m.find()) {
            return null;
        }
        final String id = m.group(1);
        // Listas automáticas ("Mix") não podem ser abertas como playlist
        if (id.startsWith("RD")) {
            return null;
        }
        return "https://www.youtube.com/playlist?list=" + id;
    }

    /** Todos os vídeos de uma playlist do YouTube (até 1000). */
    public static List<StreamInfoItem> allVideos(final String url, final Progress progress)
            throws Exception {
        final StreamingService service = NewPipe.getServiceByUrl(url);
        final PlaylistInfo info = PlaylistInfo.getInfo(service, url);
        final List<StreamInfoItem> out = new ArrayList<>(info.getRelatedItems());
        Page page = info.getNextPage();
        while (Page.isValid(page) && out.size() < MAX_VIDEOS) {
            if (progress != null) {
                progress.onProgress(out.size() + " vídeos…");
            }
            final ListExtractor.InfoItemsPage<StreamInfoItem> more =
                    PlaylistInfo.getMoreItems(service, url, page);
            out.addAll(more.getItems());
            page = more.getNextPage();
        }
        return out;
    }

    static List<Favorites.Video> toVideos(final List<StreamInfoItem> items) {
        final List<Favorites.Video> out = new ArrayList<>();
        for (final StreamInfoItem i : items) {
            out.add(new Favorites.Video(i.getServiceId(), i.getUrl(), i.getName(),
                    i.getUploaderName(), Images.best(i.getThumbnails())));
        }
        return out;
    }

    /** Importa uma playlist; usa o nome dela no YouTube (ou {@code name}, se dado). */
    public static Playlists.Playlist importPlaylist(final Context c, final String url,
                                                   final String name, final Progress progress)
            throws Exception {
        String title = name;
        if (title == null || title.trim().isEmpty()) {
            title = PlaylistInfo.getInfo(NewPipe.getServiceByUrl(url), url).getName();
        }
        final List<StreamInfoItem> items = allVideos(url, progress);
        return new Playlists(c).importVideos(title, toVideos(items));
    }

    /** Importa todas as playlists de um canal. Retorna quantas foram importadas. */
    public static int importChannelPlaylists(final Context c, final ChannelInfo channel,
                                             final Progress progress) throws Exception {
        final StreamingService service = NewPipe.getService(channel.getServiceId());
        ListLinkHandler tab = null;
        for (final ListLinkHandler t : channel.getTabs()) {
            if (t.getContentFilters().contains(ChannelTabs.PLAYLISTS)) {
                tab = t;
            }
        }
        if (tab == null) {
            return 0;
        }
        final List<PlaylistInfoItem> lists = new ArrayList<>();
        ChannelTabInfo info = ChannelTabInfo.getInfo(service, tab);
        collect(info.getRelatedItems(), lists);
        Page page = info.getNextPage();
        while (Page.isValid(page) && lists.size() < MAX_PLAYLISTS) {
            final ListExtractor.InfoItemsPage<InfoItem> more =
                    ChannelTabInfo.getMoreItems(service, tab, page);
            collect(more.getItems(), lists);
            page = more.getNextPage();
        }
        int done = 0;
        for (final PlaylistInfoItem p : lists) {
            if (done >= MAX_PLAYLISTS) {
                break;
            }
            if (progress != null) {
                progress.onProgress((done + 1) + "/" + lists.size() + ": " + p.getName());
            }
            try {
                importPlaylist(c, p.getUrl(), p.getName(), null);
                done++;
            } catch (final Exception ignored) {
                // uma playlist com erro não impede as outras
            }
        }
        return done;
    }

    private static void collect(final List<? extends InfoItem> items,
                                final List<PlaylistInfoItem> out) {
        for (final InfoItem i : items) {
            if (i instanceof PlaylistInfoItem) {
                out.add((PlaylistInfoItem) i);
            }
        }
    }
}
