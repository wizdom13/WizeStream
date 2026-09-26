package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Extracts NicoNico's regular trending list and live discovery surfaces.
 *
 * The live ranking page currently exposes its payload through script#embedded-data rather than
 * through the older card-class DOM used by previous versions of the service.
 */
public class NiconicoTrendExtractor extends KioskExtractor<StreamInfoItem> {
    private JsonArray data;
    private Document document;

    public NiconicoTrendExtractor(final StreamingService streamingService,
                                  final ListLinkHandler linkHandler,
                                  final String kioskId) {
        super(streamingService, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        switch (getId()) {
            case "Recommended Lives":
                try {
                    data = JsonParser.object()
                            .from(downloader.get(getUrl(), getExtractorLocalization()).responseBody())
                            .getObject("data")
                            .getArray("values");
                    return;
                } catch (final JsonParserException e) {
                    throw new ParsingException("Could not parse recommended live streams", e);
                }

            case "Top Lives":
                document = Jsoup.parse(
                        downloader.get(getUrl(), getExtractorLocalization()).responseBody());
                return;

            case "Trending":
            default:
                document = Jsoup.parse(getDownloader().get(
                        NiconicoService.DAILY_TREND_URL,
                        getExtractorLocalization()).responseBody());
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        switch (getId()) {
            case "Recommended Lives":
                if (data != null) {
                    for (int i = 0; i < data.size(); i++) {
                        collector.commit(new NiconicoLiveRecommendVideoExtractor(
                                data.getObject(i), null, null));
                    }
                }
                break;

            case "Top Lives":
                final Element embeddedData = document == null
                        ? null
                        : document.selectFirst("script#embedded-data");
                if (embeddedData == null) {
                    throw new ParsingException("Could not find live ranking data");
                }
                try {
                    final JsonObject ranking = JsonParser.object()
                            .from(embeddedData.attr("data-props"))
                            .getObject("ranking");
                    collectRankingPrograms(
                            collector, ranking.getArray("officialAndChannelPrograms"));
                    collectRankingPrograms(collector, ranking.getArray("userPrograms"));
                } catch (final JsonParserException e) {
                    throw new ParsingException("Could not parse live ranking data", e);
                }
                break;

            case "Trending":
            default:
                final Element responseData = document == null
                        ? null
                        : document.selectFirst("meta[name=server-response]");
                if (responseData == null) {
                    throw new ParsingException("Could not find NicoNico trending data");
                }
                final String unescapedContent =
                        Parser.unescapeEntities(responseData.attr("content"), false);
                try {
                    final JsonArray dataJson = JsonParser.object()
                            .from(unescapedContent)
                            .getObject("data")
                            .getObject("response")
                            .getObject("$getTeibanRanking")
                            .getObject("data")
                            .getArray("items");
                    for (int i = 0; i < dataJson.size(); i++) {
                        collector.commit(
                                new NiconicoSeriesContentItemExtractor(dataJson.getObject(i)));
                    }
                } catch (final Exception e) {
                    throw new ParsingException("Could not parse NicoNico trending data", e);
                }
                break;
        }

        if (ServiceList.NicoNico.getFilterTypes().contains("recommendations")) {
            collector.applyBlocking(ServiceList.NicoNico.getFilterConfig());
        }
        return new InfoItemsPage<>(collector, null);
    }

    private void collectRankingPrograms(final StreamInfoItemsCollector collector,
                                        final JsonArray programs) {
        if (programs == null) {
            return;
        }
        for (final Object entry : programs) {
            if (entry instanceof JsonObject) {
                final JsonObject program = ((JsonObject) entry).getObject("value");
                if (program != null) {
                    collector.commit(new NiconicoLiveSearchInfoItemExtractor(program));
                }
            }
        }
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return getId();
    }
}
