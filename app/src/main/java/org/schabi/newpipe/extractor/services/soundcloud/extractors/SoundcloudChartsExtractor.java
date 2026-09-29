package org.schabi.newpipe.extractor.services.soundcloud.extractors;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Locale;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;
import static org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper.SOUNDCLOUD_API_V2_URL;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class SoundcloudChartsExtractor extends KioskExtractor<StreamInfoItem> {
    private static final String UNITED_KINGDOM = "GB";
    private static final String CURATED_CHARTS_BASE_URL = "https://soundcloud.com/music-charts-";

    public SoundcloudChartsExtractor(final StreamingService service,
                                     final ListLinkHandler linkHandler,
                                     final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    @Nonnull
    @Override
    public String getName() {
        return getId();
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final String nextPageUrl = SoundcloudParsingHelper.getStreamsFromApi(collector,
                page.getUrl(), true);

        return new InfoItemsPage<>(collector, new Page(nextPageUrl));
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        String apiUrl = SOUNDCLOUD_API_V2_URL + "charts" + "?genre=soundcloud:genres:all-music"
                + "&client_id=" + SoundcloudParsingHelper.clientId();

        if (getId().equals("Top 50")) {
            apiUrl += "&kind=top";
        } else {
            apiUrl += "&kind=trending";
        }

        final ContentCountry contentCountry = SoundCloud.getContentCountry();
        String apiUrlWithRegion = null;
        if (getService().getSupportedCountries().contains(contentCountry)) {
            apiUrlWithRegion = apiUrl + "&region=soundcloud:regions:"
                    + contentCountry.getCountryCode();
        }

        try {
            return loadLegacyCharts(collector,
                    apiUrlWithRegion == null ? apiUrl : apiUrlWithRegion);
        } catch (final IOException regionalFailure) {
            if (apiUrlWithRegion != null) {
                try {
                    return loadLegacyCharts(collector, apiUrl);
                } catch (final IOException unregionalFailure) {
                    unregionalFailure.addSuppressed(regionalFailure);
                }
            }

            // SoundCloud replaced the legacy /charts endpoint with maintained public playlists.
            // Use those playlists when the old endpoint is unavailable instead of surfacing the
            // endpoint's 404 to users.
            return loadCuratedChartsPlaylist(contentCountry);
        }
    }

    @Nonnull
    private InfoItemsPage<StreamInfoItem> loadLegacyCharts(
            final StreamInfoItemsCollector collector,
            final String apiUrl) throws IOException, ExtractionException {
        final String nextPageUrl = SoundcloudParsingHelper.getStreamsFromApi(
                collector, apiUrl, true);
        return new InfoItemsPage<>(collector, new Page(nextPageUrl));
    }

    @Nonnull
    private InfoItemsPage<StreamInfoItem> loadCuratedChartsPlaylist(
            final ContentCountry contentCountry) throws IOException, ExtractionException {
        final String playlistUrl = getCuratedChartsPlaylistUrl(contentCountry, getId());
        final SoundcloudPlaylistExtractor playlistExtractor = new SoundcloudPlaylistExtractor(
                getService(), getService().getPlaylistLHFactory().fromUrl(playlistUrl));
        playlistExtractor.fetchPage();
        return playlistExtractor.getInitialPage();
    }

    static String getCuratedChartsPlaylistUrl(final ContentCountry contentCountry,
                                              final String kioskId) {
        final String market = UNITED_KINGDOM.equals(contentCountry.getCountryCode())
                ? "uk" : "us";
        final String playlist = "Top 50".equals(kioskId)
                ? "all-music-genres" : "new-hot";
        return CURATED_CHARTS_BASE_URL + market.toLowerCase(Locale.ROOT)
                + "/sets/" + playlist;
    }
}
