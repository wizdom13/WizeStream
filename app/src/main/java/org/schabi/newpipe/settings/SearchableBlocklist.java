package org.schabi.newpipe.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Keeps selection in the complete list while the visible rows change.
 *
 * @param <T> the blocked entry type
 */
final class SearchableBlocklist<T> {
    private final List<T> values = new ArrayList<>();
    private final List<Boolean> checked = new ArrayList<>();
    private final Function<T, String> label;
    private final Function<T, String> searchableText;

    SearchableBlocklist(final List<T> entries, final Function<T, String> label,
                       final Function<T, String> searchableText) {
        this.label = label;
        this.searchableText = searchableText;
        replace(entries);
    }

    void replace(final List<T> entries) {
        values.clear();
        checked.clear();
        values.addAll(entries);
        entries.forEach(entry -> checked.add(true));
    }

    List<Integer> matchingIndices(final String query) {
        final String normalized = query.trim().toLowerCase(Locale.ROOT);
        final List<Integer> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if (searchableText.apply(values.get(index)).toLowerCase(Locale.ROOT)
                    .contains(normalized)) {
                result.add(index);
            }
        }
        return result;
    }

    String label(final int index) {
        return label.apply(values.get(index));
    }

    boolean isChecked(final int index) {
        return checked.get(index);
    }

    void toggle(final int index) {
        checked.set(index, !checked.get(index));
    }

    List<T> selectedValues() {
        final List<T> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            if (checked.get(index)) {
                result.add(values.get(index));
            }
        }
        return result;
    }
}
