package org.schabi.newpipe.extractor.services.bitchute.misc;

import com.grack.nanojson.JsonObject;

import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.services.bitchute.extractor.BitchuteStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public final class BitchuteHelpers {

    private BitchuteHelpers() {
    }

    /**
     * sometimes nanojson threats the content as Integer and not as String if it is
     * internally declares as integer. This is a workaround to give always the result as String
     */
    public static String getIntAlwaysAsString(final JsonObject result, final String jsonKey) {
        String strNumber = result.getString(jsonKey);
        if (null == strNumber) {
            final int number = result.getInt(jsonKey);
            strNumber = String.valueOf(number);
        }
        return strNumber;
    }

    /**
     * As the {@link BitchuteStreamExtractor} is not able to extract the duration from html
     * because it is later added to the html that we cannot extract.
     *
     * This {@link VideoDurationCache} provides the following:
     * - methods to extract the video id
     * - a map to store the videoId and duration
     */
    public static final class VideoDurationCache {

        private VideoDurationCache() { }

        private static Map<String, Long> idToDurationMap = new HashMap<>();

        /**
         * for testing purposes (we need to have a fresh start before unit tests).
         */
        public static void resetCache() {
            idToDurationMap.clear();
        }

        public static long getDurationForVideoId(final String videoId)
                throws NoSuchElementException {
            if (idToDurationMap.containsKey(videoId)) {
                return idToDurationMap.get(videoId);
            } else {
                throw new NoSuchElementException();
            }
        }

        public static void addDurationToMap(final String videoId, final long duration) {
            idToDurationMap.put(videoId, duration);
        }

        public static void extractVideoIdAndAddItToDurationCache(
                final Element element,
                final StreamInfoItemExtractor infoItemExtractor,
                final String cssQuery) {
            try {
                final String stringContainingId = element.select(cssQuery)
                        .attr("href");

                final String id = extractVideoId(stringContainingId);
                if (id != null) {
                    final long duration = infoItemExtractor.getDuration();
                    addDurationToMap(id, duration);
                } else {
                    System.err.println(
                            "Could not extract video id for \""
                                    + cssQuery + "\" in "
                                    + element.className());
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }

        public static String extractVideoId(final String stringThatContainsId) {
            final String matchString = "/video/";
            if (stringThatContainsId != null && stringThatContainsId.contains(matchString)) {
                final String[] parts = stringThatContainsId.split(matchString);
                if (parts.length > 1) {
                    final String[] parts2 = parts[1].split("/");
                    if (parts2.length > 0) {
                        return parts2[0];
                    }
                }
            }
            return null;
        }
    }
}
