package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class InvidiousExtractors {
    private InvidiousExtractors() { }

    private static Page continuation(final InvidiousApi api, final String path,
                                     final JsonObject response) {
        final String token = response.getString("continuation", "");
        return token.isEmpty() ? null : new Page(api.url(path)
                + (path.contains("?") ? "&" : "?") + "continuation=" + InvidiousApi.encode(token));
    }

    private static String pageUrl(final Page page) throws ExtractionException {
        if (!Page.isValid(page) || page.getUrl() == null) {
            throw new ExtractionException("Invalid Invidious page");
        }
        return page.getUrl();
    }

    public static final class Search extends SearchExtractor {
        private final InvidiousApi api = new InvidiousApi();
        private String path;
        private JsonArray first;

        public Search(final StreamingService service, final SearchQueryHandler handler) {
            super(service, handler);
        }

        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            path = "search?q=" + InvidiousApi.encode(getSearchString());
            for (final FilterItem item : getLinkHandler().getContentFilters()) {
                if (item instanceof YoutubeFilters.MusicYoutubeContentFilterItem) {
                    throw new ExtractionException("YouTube Music search is unavailable through "
                            + "Invidious");
                }
                final String name = item.getName();
                if (Arrays.asList("videos", "channels", "playlists").contains(name)) {
                    path += "&type=" + name.substring(0, name.length() - 1);
                }
            }
            final List<String> features = new ArrayList<>();
            final List<FilterItem> sorts = getLinkHandler().getSortFilter();
            if (sorts != null) {
                for (final FilterItem item : sorts) {
                    final String name = item.getName();
                    if (name.startsWith("sort_")) {
                        path += "&sort=" + ("sort_view".equals(name) ? "views"
                                : name.substring("sort_".length()));
                    } else if (name.startsWith("past_")) {
                        path += "&date=" + name.substring("past_".length());
                    } else if ("short_video".equals(name) || "long_video".equals(name)) {
                        path += "&duration=" + name.substring(0, name.indexOf('_'));
                    } else if (!"all".equals(name)) {
                        final String feature = "Ccommons".equals(name) ? "creative_commons"
                                : "360°".equals(name) ? "360" : name
                                        .toLowerCase(java.util.Locale.ROOT);
                        features.add(feature);
                    }
                }
            }
            if (!features.isEmpty()) {
                path += "&features=" + InvidiousApi.encode(String.join(",", features));
            }
            path += "&region=" + InvidiousApi.encode(getExtractorContentCountry().getCountryCode());
            first = api.array(api.url(path + "&page=1"));
        }

        private InfoItemsPage<InfoItem> result(final JsonArray items, final int number) {
            return new InfoItemsPage<>(InvidiousItems.mixed(items, getServiceId(), api),
                    items.isEmpty() ? null : new Page(api.url(path + "&page=" + (number + 1)),
                            Integer.toString(number + 1)));
        }
        @Override protected InfoItemsPage<InfoItem> getInitialPageInternal() { return result(
                first, 1); }
        @Override protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
                throws IOException, ExtractionException {
            // Continuations may be restored into a new extractor before fetchPage.
            final String url = pageUrl(page);
            final int separator = url.lastIndexOf("&page=");
            if (separator < 0) {
                throw new ExtractionException("Invalid Invidious search page");
            }
            path = url.substring((api.base + "/api/v1/").length(), separator);
            return result(api.array(url), Integer.parseInt(url.substring(separator + 6)));
        }
    }

    public static final class Suggestions extends SuggestionExtractor {
        private final InvidiousApi api = new InvidiousApi();
        public Suggestions(final StreamingService service) {
            super(service);
        }
        @Override
        public List<String> suggestionList(final String query)
                throws IOException, ExtractionException {
            final JsonArray suggestions;
            try {
                suggestions = api.object(api.url("search/suggestions?q="
                        + InvidiousApi.encode(query))).getArray("suggestions");
            } catch (final InvidiousApiException error) {
                if (!error.optionalEndpointUnavailable()) {
                    throw error;
                }
                // Suggestions are optional. Keep typed search and local history usable if
                // the selected instance blocks or rate limits this endpoint.
                return new ArrayList<>();
            }
            final List<String> result = new ArrayList<>();
            if (suggestions != null) {
                for (final Object suggestion : suggestions) {
                    if (suggestion instanceof String) {
                        result.add((String) suggestion);
                    }
                }
            }
            return result;
        }
    }

    public static final class Channel extends ChannelExtractor {
        private final InvidiousApi api = new InvidiousApi();
        private JsonObject channel;
        public Channel(final StreamingService service, final ListLinkHandler handler) {
            super(service, handler);
        }
        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            channel = api.object(api.url("channels/" + InvidiousApi.encode(api
                    .channelId(getUrl()))));
        }
        @Override
        public String getName() {
            return channel.getString("author", "");
        }
        @Override
        public String getId() {
            return "channel/" + channel.getString("authorId");
        }
        @Override
        public String getUrl() throws ParsingException {
            return channel == null ? super.getUrl() : InvidiousApi.channelUrl(channel);
        }
        @Override
        public String getDescription() {
            return channel.getString("description", "");
        }
        @Override
        public long getSubscriberCount() {
            return channel.getLong("subCount", -1);
        }
        @Override
        public boolean isVerified() {
            return channel.getBoolean("authorVerified");
        }
        @Override
        public List<Image> getAvatars() {
            return api.images(channel.getArray("authorThumbnails"));
        }
        @Override
        public String getAvatarUrl() {
            return api.firstImage(channel.getArray("authorThumbnails"));
        }
        @Override
        public List<Image> getBanners() {
            return api.images(channel.getArray("authorBanners"));
        }
        @Override
        public String getBannerUrl() {
            return api.firstImage(channel.getArray("authorBanners"));
        }
        @Override
        public List<ListLinkHandler> getTabs() throws ParsingException {
            final List<ListLinkHandler> tabs = new ArrayList<>();
            final JsonArray available = channel.getArray("tabs", new JsonArray());
            for (final String tab : Arrays.asList(ChannelTabs.VIDEOS, ChannelTabs.SHORTS,
                    ChannelTabs.LIVESTREAMS, ChannelTabs.PLAYLISTS, ChannelTabs.CHANNELS)) {
                if (available.contains(ChannelTabs.LIVESTREAMS.equals(tab) ? "streams" : tab)) {
                    tabs.add(YoutubeChannelTabLinkHandlerFactory.getInstance().fromQuery(getId(),
                            Collections
                                    .singletonList(new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN,
                                    tab)), null));
                }
            }
            return tabs;
        }
    }

    public static final class ChannelTab extends ChannelTabExtractor {
        private final InvidiousApi api = new InvidiousApi();
        private String path;
        private JsonObject first;
        public ChannelTab(final StreamingService service, final ListLinkHandler handler) {
            super(service, handler);
        }
        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            final String tab = ChannelTabs.LIVESTREAMS.equals(getTab()) ? "streams" : getTab();
            if (!Arrays.asList("videos", "shorts", "streams", "playlists", "channels")
                    .contains(tab)) {
                throw new ExtractionException("This channel tab is unavailable through Invidious");
            }
            path = "channels/" + InvidiousApi.encode(api.channelId(getUrl())) + "/" + tab;
            final List<FilterItem> sorts = getLinkHandler().getSortFilter();
            if (sorts != null && !sorts.isEmpty()) {
                final String sort = sorts.get(0).getName();
                if (("playlists".equals(tab) || "channels".equals(tab))
                        && !"latest".equals(sort)) {
                    throw new ExtractionException("This channel sort is unavailable through Inv"
                            + "idious");
                }
                path += "?sort_by=" + InvidiousApi.encode("latest".equals(sort) ? "newest" : sort);
            }
            first = api.object(api.url(path));
        }
        private InfoItemsPage<InfoItem> result(final JsonObject response) {
            final JsonArray items = response.getArray("videos", response.getArray("playlists",
                    response.getArray("relatedChannels", new JsonArray())));
            return new InfoItemsPage<>(InvidiousItems.mixed(items, getServiceId(), api),
                    continuation(api, path, response));
        }
        @Override
        public InfoItemsPage<InfoItem> getInitialPage() {
            return result(first);
        }
        @Override
        public InfoItemsPage<InfoItem> getPage(final Page page) throws IOException,
                ExtractionException {
            final String url = pageUrl(page);
            path = url.substring((api.base + "/api/v1/").length()).split("[?&]continuation=")[0];
            return result(api.object(url));
        }
    }

    public static final class Feed extends FeedExtractor {
        private final InvidiousApi api = new InvidiousApi();
        private JsonObject first;
        public Feed(final StreamingService service, final ListLinkHandler handler) {
            super(service, handler);
        }
        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            first = api.object(api.url("channels/" + InvidiousApi.encode(api.channelId(getUrl()))
                    + "/videos"));
        }
        @Override
        public String getName() {
            return "Invidious feed";
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getInitialPage() {
            return new InfoItemsPage<>(InvidiousItems.videos(first.getArray("videos"),
                    getServiceId(), api), null);
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
            return InfoItemsPage.emptyPage();
        }
    }

    public static final class Playlist extends PlaylistExtractor {
        private final InvidiousApi api = new InvidiousApi();
        private JsonObject first;
        private final boolean mix;
        private final String path;
        public Playlist(final StreamingService service, final ListLinkHandler handler) {
            super(service, handler);
            mix = handler.getId().startsWith("RD");
            path = (mix ? "mixes/" : "playlists/") + InvidiousApi.encode(handler.getId());
        }
        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            first = api.object(api.url(path + "?page=1"));
        }
        @Override
        public String getName() {
            return first.getString("title", "");
        }
        @Override
        public String getUploaderName() {
            return first.getString("author", "");
        }
        @Override
        public String getUploaderUrl() {
            return InvidiousApi.channelUrl(first);
        }
        @Override
        public String getUploaderAvatarUrl() {
            return api.firstImage(first.getArray("authorThumbnails"));
        }
        @Override
        public boolean isUploaderVerified() {
            return first.getBoolean("authorVerified");
        }
        @Override
        public long getStreamCount() {
            return first.getLong("videoCount", -1);
        }
        @Override
        public String getThumbnailUrl() {
            return api.image(first.getString("playlistThumbnail", ""));
        }
        private InfoItemsPage<StreamInfoItem> result(final JsonObject response, final int number) {
            final JsonArray videos = response.getArray("videos", new JsonArray());
            // Mixes are returned as a finite recommendation list, not ordinary numbered playlists.
            final Page next = mix || videos.isEmpty() ? null
                    : new Page(api.url(path + "?page=" + (number + 1)), Integer.toString(number
                            + 1));
            return new InfoItemsPage<>(InvidiousItems.videos(videos, getServiceId(), api), next);
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getInitialPage() {
            return result(first, 1);
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException,
                ExtractionException {
            return result(api.object(pageUrl(page)), Integer.parseInt(page.getId()));
        }
    }

    public static final class Kiosk extends KioskExtractor<StreamInfoItem> {
        private final InvidiousApi api = new InvidiousApi();
        private JsonArray first;
        public Kiosk(final StreamingService service, final ListLinkHandler handler,
                final String id) {
            super(service, handler, id);
        }
        @Override
        public String getName() {
            return getLinkHandler().getId();
        }
        @Override
        public void onFetchPage(final Downloader downloader) throws IOException,
                ExtractionException {
            final String id = getName();
            final String type;
            switch (id) {
                case "trending_music": type = "music"; break;
                case "trending_gaming": type = "gaming"; break;
                case "trending_movies_and_shows": type = "movies"; break;
                case "Trending": type = "default"; break;
                case "Recommended Lives":
                    first = api.array(api.url("search?q=" + InvidiousApi.encode("live")
                            + "&type=video&features=live"));
                    return;
                default:
                    throw new ExtractionException("This YouTube kiosk is unavailable through In"
                            + "vidious");
            }
            first = api.array(api.url("trending?type=" + type + "&region="
                    + InvidiousApi.encode(getExtractorContentCountry().getCountryCode())));
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getInitialPage() {
            return new InfoItemsPage<>(InvidiousItems.videos(first, getServiceId(), api), null);
        }
        @Override
        public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
            return InfoItemsPage.emptyPage();
        }
    }
}
