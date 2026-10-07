package org.schabi.newpipe.extractor.services.youtube.invidious;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

/** An HTTP rejection from the selected instance, distinct from malformed successful API data. */
final class InvidiousApiException extends ExtractionException {
    private final int status;

    InvidiousApiException(final int status, final String detail) {
        super(message(status, detail));
        this.status = status;
    }

    boolean optionalEndpointUnavailable() {
        return status == 401 || status == 403 || status == 404 || status == 429;
    }

    private static String message(final int status, final String detail) {
        final String prefix = "Invidious returned HTTP " + status + explanation(status);
        // Keep the complete diagnostic bounded even after adding actionable status guidance.
        final int remaining = Math.max(0, 299 - prefix.length());
        return prefix + detail.substring(0, Math.min(detail.length(), remaining));
    }

    private static String explanation(final int status) {
        if (status == 401 || status == 403) {
            return ": This instance denied API access. Try another saved Invidious instance.";
        }
        if (status == 429) {
            return ": This instance is rate limiting requests. Try again later or switch instance.";
        }
        if (status >= 500) {
            return ": This instance is unavailable. Try again later or switch instance.";
        }
        return "";
    }
}
