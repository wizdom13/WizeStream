package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoPatternsManager;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YoutubeLockupUploadDateTest {
    @Test
    public void compactDateUsesAccessibleLabelFromReportedMissingVideo() throws Exception {
        final var extractor = extractor("9d ago", "9 days ago");
        assertEquals("9 days ago", extractor.getTextualUploadDate());
        assertNotNull(extractor.getUploadDate());
        final long age = ChronoUnit.HOURS.between(
                extractor.getUploadDate().offsetDateTime(), OffsetDateTime.now());
        assertTrue(age >= 215 && age <= 217);
    }

    @Test
    public void fullVisibleDateStillTakesPrecedence() throws Exception {
        assertEquals("2 days ago", extractor("2 days ago", "3 days ago")
                .getTextualUploadDate());
    }

    @Test
    public void missingVisibleDateCanUseAccessibleLabel() throws Exception {
        assertEquals("4 hours ago", extractor("", "4 hours ago").getTextualUploadDate());
    }

    @Test
    public void viewCountsAndUnparseableLabelsDoNotBecomeUploadDates() throws Exception {
        final var extractor = extractor("777K", "777 thousand views");
        assertNull(extractor.getTextualUploadDate());
        assertNull(extractor.getUploadDate());
    }

    private YoutubeLockupStreamInfoItemExtractor extractor(final String text,
                                                           final String label) throws Exception {
        // Shape returned by the channel's richItemRenderer/lockupViewModel for RlHdsh4HtCQ.
        final String json = "{\"contentId\":\"RlHdsh4HtCQ\","
                + "\"contentImage\":{\"thumbnailViewModel\":{}},"
                + "\"metadata\":{\"lockupMetadataViewModel\":{\"metadata\":{"
                + "\"contentMetadataViewModel\":{\"metadataRows\":[{\"metadataParts\":["
                + "{\"text\":{\"content\":\"777K\"},\"accessibilityLabel\":\"777 thousand views\"},"
                + "{\"text\":{\"content\":\"" + text + "\"},\"accessibilityLabel\":\""
                + label + "\"}]}]}}}}}";
        return new YoutubeLockupStreamInfoItemExtractor(JsonParser.object().from(json),
                TimeAgoPatternsManager.getTimeAgoParserFor(new Localization("en", "GB")));
    }
}
