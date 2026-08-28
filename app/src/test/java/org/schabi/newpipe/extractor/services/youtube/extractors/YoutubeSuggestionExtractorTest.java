package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.Arrays;
import java.util.Collections;

public class YoutubeSuggestionExtractorTest {

    @Test
    public void parsesJsonSuggestionsWithoutJsonpTrimming() throws Exception {
        final String response = "[\"query\",[[\"first result\"],[\"second result\"],[\"\"]]]";

        assertEquals(
                Arrays.asList("first result", "second result"),
                YoutubeSuggestionExtractor.parseSuggestions(response));
    }

    @Test
    public void ignoresEntriesThatAreNotSuggestionArrays() throws Exception {
        final String response = "[\"query\",[\"tracking\",[\"valid result\"]]]";

        assertEquals(
                Collections.singletonList("valid result"),
                YoutubeSuggestionExtractor.parseSuggestions(response));
    }

    @Test
    public void rejectsMalformedResponses() {
        assertThrows(
                ExtractionException.class,
                () -> YoutubeSuggestionExtractor.parseSuggestions("ml-not-json"));
    }

    @Test
    public void rejectsEmptyResponses() {
        assertThrows(
                ExtractionException.class,
                () -> YoutubeSuggestionExtractor.parseSuggestions(""));
    }
}
