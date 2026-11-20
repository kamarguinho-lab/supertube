package com.kamarguinho.supertube.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Busca os vídeos mais novos de cada canal inscrito. */
public final class Feed {

    private static final int PER_CHANNEL = 15;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private Feed() {
    }

    /** Vídeos da aba "Vídeos" de um canal. */
    public static List<InfoItem> channelVideos(final ChannelInfo channel) throws Exception {
        final StreamingService service = NewPipe.getService(channel.getServiceId());
        ListLinkHandler videosTab = null;
        for (final ListLinkHandler tab : channel.getTabs()) {
            if (tab.getContentFilters().contains(ChannelTabs.VIDEOS)) {
                videosTab = tab;
                break;
            }
        }
        if (videosTab == null && !channel.getTabs().isEmpty()) {
            videosTab = channel.getTabs().get(0);
        }
        if (videosTab == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ChannelTabInfo.getInfo(service, videosTab).getRelatedItems());
    }

    public static List<StreamInfoItem> load(final List<Subscriptions.Channel> channels)
            throws InterruptedException {
        final List<Future<List<StreamInfoItem>>> futures = new ArrayList<>();
        for (final Subscriptions.Channel c : channels) {
            futures.add(POOL.submit(() -> {
                final List<StreamInfoItem> out = new ArrayList<>();
                final ChannelInfo info = ChannelInfo.getInfo(
                        NewPipe.getService(c.serviceId), c.url);
                for (final InfoItem item : channelVideos(info)) {
                    if (item instanceof StreamInfoItem) {
                        out.add((StreamInfoItem) item);
                        if (out.size() >= PER_CHANNEL) {
                            break;
                        }
                    }
                }
                return out;
            }));
        }

        final List<StreamInfoItem> all = new ArrayList<>();
        for (final Future<List<StreamInfoItem>> f : futures) {
            try {
                all.addAll(f.get());
            } catch (final InterruptedException e) {
                throw e;
            } catch (final Exception ignored) {
                // um canal com erro não derruba o feed inteiro
            }
        }

        // Mais novos primeiro
        Collections.sort(all, (a, b) -> {
            final long ta = a.getUploadDate() == null ? 0
                    : a.getUploadDate().getInstant().toEpochMilli();
            final long tb = b.getUploadDate() == null ? 0
                    : b.getUploadDate().getInstant().toEpochMilli();
            return Long.compare(tb, ta);
        });
        return all;
    }
}
