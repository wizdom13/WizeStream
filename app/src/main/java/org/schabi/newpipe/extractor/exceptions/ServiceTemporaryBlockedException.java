package org.schabi.newpipe.extractor.exceptions;

/**
 * Indicates that a service temporarily rejected otherwise valid requests through its anti-bot or
 * risk-control system.
 *
 * <p>This is an expected, retryable service condition rather than evidence that the extractor no
 * longer understands the response format.</p>
 */
public class ServiceTemporaryBlockedException extends AntiBotException {

    public ServiceTemporaryBlockedException(final String message) {
        super(message);
    }

    public ServiceTemporaryBlockedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
