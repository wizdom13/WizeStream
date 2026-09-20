package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommentTranslationPolicyTest {
    @Test
    public void treatsRegionalVariantsAsTheSameLanguage() {
        assertTrue(CommentTranslationPolicy.isSameLanguage("en-US", "en-GB"));
        assertTrue(CommentTranslationPolicy.isSameLanguage("pt-BR", "pt-PT"));
    }

    @Test
    public void distinguishesExplicitScriptsWithinTheSameLanguage() {
        assertFalse(CommentTranslationPolicy.isSameLanguage("zh-Hans", "zh-Hant"));
    }

    @Test
    public void capabilityMatchingAcceptsGenericLocalesButHonorsExplicitScripts() {
        assertTrue(CommentTranslationPolicy.capabilityMatches("en", "en-US"));
        assertTrue(CommentTranslationPolicy.capabilityMatches("zh", "zh-Hant"));
        assertTrue(CommentTranslationPolicy.capabilityMatches("zh-Hant", "zh-Hant"));
        assertFalse(CommentTranslationPolicy.capabilityMatches("zh-Hans", "zh-Hant"));
        assertFalse(CommentTranslationPolicy.capabilityMatches("fr", "de-DE"));
    }
}
