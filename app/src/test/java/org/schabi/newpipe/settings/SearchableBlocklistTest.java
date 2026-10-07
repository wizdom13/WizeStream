package org.schabi.newpipe.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

class SearchableBlocklistTest {
    @Test
    void savingFilteredSelectionKeepsHiddenEntriesAndPreviousChanges() {
        final SearchableBlocklist<String> model = strings(List.of("Cars", "Music", "Movies"));
        model.toggle(model.matchingIndices("cars").get(0));
        model.toggle(model.matchingIndices("music").get(0));
        assertEquals(List.of("Movies"), model.selectedValues());
        assertEquals(List.of(2), model.matchingIndices(" movies "));
        assertFalse(model.isChecked(model.matchingIndices("cars").get(0)));
    }

    @Test
    void searchMatchesUrlsAndIsIndependentOfDeviceLocale() {
        final Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            final SearchableBlocklist<String> model = new SearchableBlocklist<>(
                    List.of("MUSIC"), value -> value,
                    value -> value + " https://example.com/Channel42");
            assertEquals(List.of(0), model.matchingIndices("music"));
            assertEquals(List.of(0), model.matchingIndices("CHANNEL42"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void identicalLabelsKeepSeparateSelections() {
        final SearchableBlocklist<String> model = new SearchableBlocklist<>(
                List.of("channel-one", "channel-two"), value -> "Same title", value -> value);
        model.toggle(model.matchingIndices("two").get(0));
        assertEquals(List.of("channel-one"), model.selectedValues());
        assertEquals(List.of(), model.matchingIndices("missing"));
        assertEquals(List.of(0, 1), model.matchingIndices(""));
    }

    private static SearchableBlocklist<String> strings(final List<String> values) {
        return new SearchableBlocklist<>(values, value -> value, value -> value);
    }
}
