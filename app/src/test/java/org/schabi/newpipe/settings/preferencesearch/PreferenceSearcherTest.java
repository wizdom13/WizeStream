package org.schabi.newpipe.settings.preferencesearch;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreferenceSearcherTest {
    private final PreferenceSearcher searcher =
            new PreferenceSearcher(new PreferenceSearchConfiguration());

    @Test
    public void blankAndUnrelatedQueriesHaveNoResults() {
        searcher.add(List.of(item("resolution", "Default resolution", "", "720p", "Video")));
        assertTrue(searcher.searchFor(null).isEmpty());
        assertTrue(searcher.searchFor("  \t ").isEmpty());
        assertTrue(searcher.searchFor("xzyq987654").isEmpty());
    }

    @Test
    public void exactTitlesRankFirstAndChoicesAndSummariesAreSearchable() {
        final PreferenceSearchItem exact = item("exact", "Resolution", "", "", "");
        final PreferenceSearchItem partial = item("partial", "Default resolution", "", "720p", "");
        final PreferenceSearchItem description = item("description", "Playback",
                "Limit resolution on mobile data", "", "Video and audio");
        searcher.add(List.of(description, partial, exact));
        assertEquals(List.of(exact, partial, description), searcher.searchFor("resolution"));
        assertEquals(List.of(partial), searcher.searchFor("720p"));
        assertEquals(List.of(description), searcher.searchFor("mobile data"));
    }

    @Test
    public void queryWordsCanSpanFieldsInAnyOrder() {
        final PreferenceSearchItem result = item("translation", "Target language", "", "French",
                "Video and audio > Caption translation");
        searcher.add(List.of(result));
        assertEquals(List.of(result), searcher.searchFor("  FRENCH   caption  "));
        assertTrue(searcher.searchFor("French proxy").isEmpty());
    }

    @Test
    public void localizedTitlesIgnoreCaseAndDiacritics() {
        final PreferenceSearchItem french = item("fr", "Qualité vidéo", "", "", "");
        final PreferenceSearchItem arabic = item("ar", "الترجمة", "", "", "");
        searcher.add(List.of(french, arabic));
        assertEquals(List.of(french), searcher.searchFor("QUALITE"));
        assertEquals(List.of(arabic), searcher.searchFor("الترجمة"));
    }

    @Test
    public void relevantResultsAreNotTruncatedAtTwenty() {
        searcher.add(IntStream.range(0, 30)
                .mapToObj(i -> item("key" + i, "Option " + i, "", "", "Video"))
                .collect(Collectors.toList()));
        assertEquals(30, searcher.searchFor("video").size());
    }

    private static PreferenceSearchItem item(final String key, final String title,
                                             final String summary, final String entries,
                                             final String breadcrumbs) {
        return new PreferenceSearchItem(key, title, summary, entries, breadcrumbs, 1);
    }
}
