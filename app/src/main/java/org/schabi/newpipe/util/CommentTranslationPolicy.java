package org.schabi.newpipe.util;

import java.util.Locale;

public final class CommentTranslationPolicy {
    private CommentTranslationPolicy() {
    }

    static boolean isSameLanguage(final String firstTag, final String secondTag) {
        final Locale first = Locale.forLanguageTag(firstTag);
        final Locale second = Locale.forLanguageTag(secondTag);
        if (first.getLanguage().isEmpty() || second.getLanguage().isEmpty()
                || !first.getLanguage().equalsIgnoreCase(second.getLanguage())) {
            return false;
        }

        final String firstScript = first.getScript();
        final String secondScript = second.getScript();
        return firstScript.isEmpty() || secondScript.isEmpty()
                || firstScript.equalsIgnoreCase(secondScript);
    }

    static boolean capabilityMatches(final String capabilityTag, final String requestedTag) {
        final Locale capability = Locale.forLanguageTag(capabilityTag);
        final Locale requested = Locale.forLanguageTag(requestedTag);
        if (capability.getLanguage().isEmpty() || requested.getLanguage().isEmpty()
                || !capability.getLanguage().equalsIgnoreCase(requested.getLanguage())) {
            return false;
        }

        final String requestedScript = requested.getScript();
        final String capabilityScript = capability.getScript();
        return requestedScript.isEmpty() || capabilityScript.isEmpty()
                || requestedScript.equalsIgnoreCase(capabilityScript);
    }
}
