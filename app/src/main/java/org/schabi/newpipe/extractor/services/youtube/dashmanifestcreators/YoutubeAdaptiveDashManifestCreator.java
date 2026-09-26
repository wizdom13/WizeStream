package org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators;

import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.ManifestCreatorCache;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.ADAPTATION_SET;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.BASE_URL;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.INITIALIZATION;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.REPRESENTATION;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.SEGMENT_BASE;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.buildAndCacheResult;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateAdaptationSetElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateDocumentAndMpdElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generatePeriodElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateRepresentationElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateRoleElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.setAttribute;

/**
 * Generates one static DASH adaptation set from compatible YouTube progressive video-only streams.
 */
public final class YoutubeAdaptiveDashManifestCreator {
    private static final ManifestCreatorCache<String, String> ADAPTIVE_STREAMS_CACHE =
            new ManifestCreatorCache<>();

    private YoutubeAdaptiveDashManifestCreator() {
    }

    @Nonnull
    public static String fromProgressiveVideoStreams(@Nonnull final List<VideoStream> streams,
                                                     final long durationSecondsFallback)
            throws CreationException {
        if (streams.size() < 2) {
            throw new CreationException("Adaptive DASH requires at least two video streams");
        }

        final VideoStream first = streams.get(0);
        final ItagItem firstItag = requireProgressiveVideoStream(first);
        final String cacheKey = first.getContent() + "#adaptive#"
                + streams.stream()
                .map(stream -> Integer.toString(stream.getItag()))
                .collect(Collectors.joining(","));
        if (ADAPTIVE_STREAMS_CACHE.containsKey(cacheKey)) {
            return Objects.requireNonNull(ADAPTIVE_STREAMS_CACHE.get(cacheKey)).getSecond();
        }

        final long itagDuration = firstItag.getApproxDurationMs();
        final long durationMillis = itagDuration > 0
                ? itagDuration
                : durationSecondsFallback * 1000L;
        if (durationMillis <= 0) {
            throw new CreationException("Adaptive DASH stream duration is unavailable");
        }

        final Document document = generateDocumentAndMpdElement(durationMillis);
        generatePeriodElement(document);
        generateAdaptationSetElement(document, firstItag);
        generateRoleElement(document);

        for (final VideoStream stream : streams) {
            final ItagItem itag = requireProgressiveVideoStream(stream);
            if (itag.getMediaFormat() != firstItag.getMediaFormat()) {
                throw new CreationException("Adaptive DASH streams use different media formats");
            }
            generateRepresentationElement(document, itag);
            final Element representation = lastElement(document, REPRESENTATION);
            appendBaseUrl(document, representation, stream.getContent());
            appendSegmentBase(document, representation, itag);
        }

        return buildAndCacheResult(cacheKey, document, ADAPTIVE_STREAMS_CACHE);
    }

    @Nonnull
    public static ManifestCreatorCache<String, String> getCache() {
        return ADAPTIVE_STREAMS_CACHE;
    }

    @Nonnull
    private static ItagItem requireProgressiveVideoStream(@Nonnull final VideoStream stream)
            throws CreationException {
        if (!stream.isVideoOnly()
                || !stream.isUrl()
                || stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP
                || stream.getItagItem() == null) {
            throw new CreationException("Stream cannot be used in an adaptive progressive ladder");
        }
        return stream.getItagItem();
    }

    private static void appendBaseUrl(final Document document,
                                      final Element representation,
                                      final String url) {
        final Element baseUrl = document.createElement(BASE_URL);
        baseUrl.setTextContent(url);
        representation.appendChild(baseUrl);
    }

    private static void appendSegmentBase(final Document document,
                                          final Element representation,
                                          final ItagItem itag) throws CreationException {
        if (itag.getIndexStart() < 0 || itag.getIndexEnd() < 0
                || itag.getInitStart() < 0 || itag.getInitEnd() < 0) {
            throw new CreationException("Adaptive DASH stream range metadata is unavailable");
        }

        final Element segmentBase = document.createElement(SEGMENT_BASE);
        setAttribute(segmentBase, document, "indexRange",
                itag.getIndexStart() + "-" + itag.getIndexEnd());
        final Element initialization = document.createElement(INITIALIZATION);
        setAttribute(initialization, document, "range",
                itag.getInitStart() + "-" + itag.getInitEnd());
        segmentBase.appendChild(initialization);
        representation.appendChild(segmentBase);
    }

    @Nonnull
    private static Element lastElement(final Document document, final String name)
            throws CreationException {
        final NodeList elements = document.getElementsByTagName(name);
        if (elements.getLength() == 0) {
            throw new CreationException("Missing " + name + " while building adaptive DASH");
        }
        return (Element) elements.item(elements.getLength() - 1);
    }
}
