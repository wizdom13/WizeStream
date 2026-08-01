package org.schabi.newpipe.extractor.exceptions;

/** Exception for content that a service does not provide in the selected country. */
public class UnsupportedContentInCountryException extends ContentNotAvailableException {
    public UnsupportedContentInCountryException(final String message) {
        super(message);
    }
}
