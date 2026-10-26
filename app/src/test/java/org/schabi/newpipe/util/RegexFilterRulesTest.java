package org.schabi.newpipe.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexFilterRulesTest {
    @Test
    void supportsAlternativesBoundariesAndCommasWithoutChangingLiteralRules() {
        final var rules = RegexFilterRules.parse("\\b(spoiler|leak)\\b\npart [0-9]{1,3}");
        assertTrue(rules.matches("A SPOILER ahead"));
        assertTrue(rules.matches("Part 123"));
        assertFalse(rules.matches("spoilers"));
        assertFalse(rules.matches(null));
    }

    @Test
    void rejectsInvalidUnsupportedAndOversizedRules() {
        assertThrows(IllegalArgumentException.class, () -> RegexFilterRules.parse("["));
        assertThrows(IllegalArgumentException.class, () -> RegexFilterRules.parse("(?=abc)"));
        assertThrows(IllegalArgumentException.class, () -> RegexFilterRules.parse("(a)\\1"));
        assertThrows(IllegalArgumentException.class,
                () -> RegexFilterRules.parse("a".repeat(513)));
        assertThrows(IllegalArgumentException.class,
                () -> RegexFilterRules.parse("a\n".repeat(33)));
        assertFalse(RegexFilterRules.fromStored("[").matches("anything"));
    }

    @Test
    void nestedQuantifiersDoNotBacktrackCatastrophically() {
        assertFalse(RegexFilterRules.parse("^(a+)+$").matches("a".repeat(10000) + "!"));
    }
}
