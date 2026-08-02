package org.schabi.newpipe.extractor.services.youtube.sabr;

/** A protected SABR response could not be recovered within the bounded PO-token refresh budget. */
public final class SabrPoTokenRefreshException extends SabrProtocolException {
    public SabrPoTokenRefreshException(final String message) {
        super(message);
    }
}
