package com.kamarguinho.supertube.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Em alta": junta as listas de tendências do YouTube (música, jogos, podcasts e
 * filmes/séries), intercaladas, como a página Explorar do YouTube.
 */
public final class Trending {

    /** Texto especial usado nos menus para pedir os vídeos em alta. */
    public static final String QUERY = "__em_alta__";

    private static final String[] KIOSKS = {
            "trending_music",
            "trending_gaming",
            "trending_podcasts_episodes",
            "trending_movies_and_shows",
    };

    private Trending() {
    }

    public static List<StreamInfoItem> load() throws Exception {
        final StreamingService yt = ServiceList.YouTube;
        final List<List<StreamInfoItem>> lists = new ArrayList<>();
        for (final String id : KIOSKS) {
            try {
                final KioskExtractor<?> extractor =
                        yt.getKioskList().getExtractorById(id, null);
                extractor.fetchPage();
                lists.add(new ArrayList<>(KioskInfo.getInfo(extractor).getRelatedItems()));
            } catch (final Exception ignored) {
                // uma lista com problema não impede as outras
            }
        }

        // Intercala: 1º de música, 1º de jogos, 1º de podcasts, 1º de filmes, 2º de música...
        final Map<String, StreamInfoItem> out = new LinkedHashMap<>();
        boolean added = true;
        for (int i = 0; added; i++) {
            added = false;
            for (final List<StreamInfoItem> list : lists) {
                if (i < list.size()) {
                    added = true;
                    final StreamInfoItem item = list.get(i);
                    if (!Movies.isShort(item)) {
                        out.put(item.getUrl(), item);
                    }
                }
            }
        }

        if (out.isEmpty()) {
            // Se o YouTube mudar as listas de tendências, cai para uma busca
            for (final InfoItem i : SearchInfo.getInfo(yt,
                    yt.getSearchQHFactory().fromQuery("em alta hoje")).getRelatedItems()) {
                if (i instanceof StreamInfoItem && !Movies.isShort((StreamInfoItem) i)) {
                    out.put(i.getUrl(), (StreamInfoItem) i);
                }
            }
        }
        return new ArrayList<>(out.values());
    }
}
