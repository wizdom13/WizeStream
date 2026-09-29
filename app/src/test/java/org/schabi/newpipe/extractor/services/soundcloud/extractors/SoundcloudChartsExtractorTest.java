package org.schabi.newpipe.extractor.services.soundcloud.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.localization.ContentCountry;

class SoundcloudChartsExtractorTest {

    @Test
    void fallsBackToUnitedStatesTopChartsForUnsupportedCountries() {
        assertEquals("https://soundcloud.com/music-charts-us/sets/all-music-genres",
                SoundcloudChartsExtractor.getCuratedChartsPlaylistUrl(
                        new ContentCountry("VN"), "Top 50"));
    }

    @Test
    void usesUnitedKingdomCuratedChartsForGreatBritain() {
        assertEquals("https://soundcloud.com/music-charts-uk/sets/new-hot",
                SoundcloudChartsExtractor.getCuratedChartsPlaylistUrl(
                        new ContentCountry("GB"), "New & hot"));
    }

    @Test
    void keepsUnitedStatesCuratedChartsForUnitedStates() {
        assertEquals("https://soundcloud.com/music-charts-us/sets/new-hot",
                SoundcloudChartsExtractor.getCuratedChartsPlaylistUrl(
                        new ContentCountry("US"), "New & hot"));
    }
}
