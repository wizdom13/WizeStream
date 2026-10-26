package org.schabi.newpipe.util;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, linear-time regex rules kept separate from legacy literal keyword rules. */
public final class RegexFilterRules {
    private final List<Pattern> patterns;

    private RegexFilterRules(final List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public static RegexFilterRules parse(final String text) {
        final List<Pattern> patterns = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return new RegexFilterRules(patterns);
        }
        if (text.length() > 16384) {
            throw new IllegalArgumentException("Rule text is too long");
        }
        for (final String line : text.split("\\n")) {
            final String expression = line.trim();
            if (expression.isEmpty()) {
                continue;
            }
            if (expression.length() > 512 || patterns.size() >= 32) {
                throw new IllegalArgumentException("Too many or overly long expressions");
            }
            try {
                final Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
                if (pattern.programSize() > 4096) {
                    throw new IllegalArgumentException("Expression is too complex");
                }
                patterns.add(pattern);
            } catch (final PatternSyntaxException error) {
                throw new IllegalArgumentException("Invalid or unsupported expression", error);
            }
        }
        return new RegexFilterRules(patterns);
    }

    public static RegexFilterRules fromStored(final String text) {
        try {
            return parse(text);
        } catch (final IllegalArgumentException error) {
            // Invalid imported settings must not crash list rendering or hide all content.
            return new RegexFilterRules(Collections.emptyList());
        }
    }

    public boolean matches(final String text) {
        if (text == null) {
            return false;
        }
        for (final Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
