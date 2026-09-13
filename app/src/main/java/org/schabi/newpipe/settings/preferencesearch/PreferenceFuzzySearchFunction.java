package org.schabi.newpipe.settings.preferencesearch;

import org.apache.commons.text.similarity.FuzzyScore;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PreferenceFuzzySearchFunction
        implements PreferenceSearchConfiguration.PreferenceSearchFunction {
    private static final FuzzyScore FUZZY_SCORE = new FuzzyScore(Locale.ROOT);
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public Stream<PreferenceSearchItem> search(final Stream<PreferenceSearchItem> allAvailable,
                                              final String keyword) {
        final String query = normalize(keyword);
        if (query.isEmpty()) {
            return Stream.empty();
        }
        final String[] words = query.split(" ");
        return allAvailable.map(item -> new RankedItem(item, query, words))
                .filter(result -> result.score > 0)
                .sorted(Comparator.comparingInt((RankedItem result) -> result.score).reversed()
                        .thenComparing(result -> result.item.getTitle())
                        .thenComparing(result -> result.item.getBreadcrumbs())
                        .thenComparing(result -> result.item.getKey()))
                .map(result -> result.item);
    }

    private static String normalize(final String text) {
        return WHITESPACE.matcher(MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("").toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static final class RankedItem {
        private final PreferenceSearchItem item;
        private final int score;

        RankedItem(final PreferenceSearchItem item, final String query, final String[] words) {
            this.item = item;
            final String title = normalize(item.getTitle());
            final String summary = normalize(item.getSummary());
            final String entries = normalize(item.getEntries());
            final String breadcrumbs = normalize(item.getBreadcrumbs());
            int rank = title.equals(query) ? 100 : title.contains(query) ? 50 : 0;
            for (final String word : words) {
                if (title.contains(word)) {
                    rank += 10;
                } else if (summary.contains(word) || entries.contains(word)) {
                    rank += 5;
                } else if (breadcrumbs.contains(word)) {
                    rank += 1;
                } else if (word.length() >= 4
                        && FUZZY_SCORE.fuzzyScore(title, word) >= (3 * word.length() - 2) * 0.85) {
                    rank += 1;
                } else {
                    rank = 0;
                    break;
                }
            }
            score = rank;
        }
    }
}
