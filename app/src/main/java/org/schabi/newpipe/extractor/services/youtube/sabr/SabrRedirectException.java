package org.schabi.newpipe.extractor.services.youtube.sabr;

/** A bounded SABR redirect chain either repeated a URL or exceeded its hop budget. */
public final class SabrRedirectException extends SabrProtocolException {
    public SabrRedirectException(final String message) {
        super(message);
    }
}
