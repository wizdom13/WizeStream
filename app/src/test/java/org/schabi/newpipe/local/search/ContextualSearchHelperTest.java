package org.schabi.newpipe.local.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Arrays;
import java.util.List;

public class ContextualSearchHelperTest {
    @Test
    public void normalizesWhitespaceAndMatchesCaseInsensitively() {
        assertEquals("spice", ContextualSearchHelper.normalizeQuery("  spice  "));
        assertTrue(ContextualSearchHelper.matches("  SPICE ", "Adonis Spice Factory"));
        assertTrue(ContextualSearchHelper.matches("adonis", "Unrelated title", "Adonis"));
        assertFalse(ContextualSearchHelper.matches("pepper", "Cinnamon", null));
    }

    @Test
    public void emptyQueryRestoresEveryItemInCanonicalOrder() {
        final List<SearchItem> items = Arrays.asList(
                new SearchItem("Third", "Uploader C"),
                new SearchItem("First", "Uploader A"),
                new SearchItem("Second", "Uploader B"));

        final List<SearchItem> filtered = ContextualSearchHelper.filter(
                items, "", item -> new String[]{item.title, item.uploader});

        assertEquals(items, filtered);
    }

    @Test
    public void filtersAcrossTitleAndUploaderWithoutMutatingCanonicalList() {
        final SearchItem first = new SearchItem("Fattoush", "Adonis");
        final SearchItem second = new SearchItem("Tandoori", "Shan");
        final SearchItem third = new SearchItem("Zaatar", "Adonis");
        final List<SearchItem> items = Arrays.asList(first, second, third);

        final List<SearchItem> filtered = ContextualSearchHelper.filter(
                items, "adonis", item -> new String[]{item.title, item.uploader});

        assertEquals(Arrays.asList(first, third), filtered);
        assertEquals(Arrays.asList(first, second, third), items);
    }

    @Test
    public void matchesRemoteInfoItemsByTitleOrUploader() {
        final StreamInfoItem item = new StreamInfoItem(
                0, "https://example.com/watch", "Quiet documentary", StreamType.VIDEO_STREAM);
        item.setUploaderName("Science Channel");

        assertTrue(ContextualSearchHelper.matchesInfoItem("documentary", item));
        assertTrue(ContextualSearchHelper.matchesInfoItem("SCIENCE", item));
        assertFalse(ContextualSearchHelper.matchesInfoItem("cooking", item));
    }

    private static final class SearchItem {
        private final String title;
        private final String uploader;

        private SearchItem(final String title, final String uploader) {
            this.title = title;
            this.uploader = uploader;
        }
    }
}
