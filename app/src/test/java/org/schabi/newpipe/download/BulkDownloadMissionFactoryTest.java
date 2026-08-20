package org.schabi.newpipe.download;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BulkDownloadMissionFactoryTest {
    @Test
    void numberedTitlePadsToAtLeastTwoDigits() {
        assertEquals("01 - First", BulkDownloadMissionFactory.numberedTitle(
                "First", 1, 8, true));
    }

    @Test
    void numberedTitleUsesEnoughDigitsForLargeLists() {
        assertEquals("007 - Seventh", BulkDownloadMissionFactory.numberedTitle(
                "Seventh", 7, 120, true));
    }

    @Test
    void numberedTitleCanBeDisabled() {
        assertEquals("Original", BulkDownloadMissionFactory.numberedTitle(
                "Original", 4, 20, false));
    }
}
