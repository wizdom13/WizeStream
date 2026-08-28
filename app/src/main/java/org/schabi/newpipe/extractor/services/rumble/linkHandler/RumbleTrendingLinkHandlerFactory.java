package org.schabi.newpipe.extractor.services.rumble.linkHandler;

import org.schabi.newpipe.extractor.search.filter.FilterItem;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.extractors.RumbleTrendingShortsExtractor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class RumbleTrendingLinkHandlerFactory extends ListLinkHandlerFactory {

    public static final String LIVE = "Live";
    public static final String EDITOR_PICKS = "Editor Picks";
    public static final String LATEST = "Latest";
    public static final String TODAYS_BATTLE_LEADERBOARD_TOP_50 =
            "Today's Battle Leaderboard Top 50";
    public static final String TRENDING_TODAY = "Trending Today";

    public static final String DEFAULT_TRENDING = TODAYS_BATTLE_LEADERBOARD_TOP_50;

    private final List<String> trendingIdList = new LinkedList<>();
    private final Map<String, String> trendingId2UrlMap = new HashMap<>();

    // use LinkedHashMap to preserver insert order
    private final Map<String, String> trendingUrl2IdMap = new LinkedHashMap<>();

    private RumbleTrendingLinkHandlerFactory() {
        trendingUrl2IdMap.put("https://rumble.com/editor-picks", EDITOR_PICKS);
        trendingUrl2IdMap.put("https://rumble.com/browse/live", LIVE);
        trendingUrl2IdMap
                .put("https://rumble.com/battle-leaderboard", TODAYS_BATTLE_LEADERBOARD_TOP_50);
        trendingUrl2IdMap.put("https://rumble.com/videos?sort=views&date=today", TRENDING_TODAY);
        trendingUrl2IdMap.put("https://rumble.com/videos?date=this-week", LATEST);
        trendingUrl2IdMap.put("not.relevant.for.rumble.trending.shorts.extractor",
                RumbleTrendingShortsExtractor.KIOSK_SHORTS);

        for (final Map.Entry<String, String> entry : trendingUrl2IdMap.entrySet()) {
            trendingId2UrlMap.put(entry.getValue(), entry.getKey());
            trendingIdList.add(entry.getValue());
        }
    }

    public static RumbleTrendingLinkHandlerFactory instance =
            new RumbleTrendingLinkHandlerFactory();

    public static RumbleTrendingLinkHandlerFactory getInstance() {
        return instance;
    }

    public List<String> getTrendingKioskIdsList() {
        return trendingIdList;
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilter)
            throws ParsingException, UnsupportedOperationException {
        if ("".equals(id)) {
            return trendingId2UrlMap.get(DEFAULT_TRENDING);
        }
        return trendingId2UrlMap.get(id);
    }

    @Override
    public String getId(final String url) {
        return trendingUrl2IdMap.get(url);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return trendingUrl2IdMap.containsKey(url);
    }
}
