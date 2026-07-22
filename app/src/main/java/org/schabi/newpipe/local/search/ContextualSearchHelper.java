package org.schabi.newpipe.local.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Shared, locale-stable matching used by contextual local search.
 */
public final class ContextualSearchHelper {
    private ContextualSearchHelper() {
    }

    @NonNull
    public static String normalizeQuery(@Nullable final CharSequence query) {
        return query == null ? "" : query.toString().trim();
    }

    public static boolean isActive(@Nullable final CharSequence query) {
        return !normalizeQuery(query).isEmpty();
    }

    public static boolean matches(@Nullable final CharSequence query,
                                  @Nullable final String... candidates) {
        final String normalizedQuery = normalizeQuery(query).toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        if (candidates == null) {
            return false;
        }
        for (final String candidate : candidates) {
            if (candidate != null
                    && candidate.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static <T> List<T> filter(@NonNull final List<T> items,
                                     @Nullable final CharSequence query,
                                     @NonNull final Function<T, String[]> searchableText) {
        final List<T> filtered = new ArrayList<>();
        for (final T item : items) {
            if (matches(query, searchableText.apply(item))) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
