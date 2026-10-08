package org.schabi.newpipe.player.datasource;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reject instance error pages before they reach a media, manifest or caption parser. */
public final class InvidiousMediaResponse {
    private static final int PREFIX_SIZE = 128;

    private InvidiousMediaResponse() { }

    public static final class InvalidResponseException extends HttpDataSourceException {
        public InvalidResponseException(final String message, final DataSpec dataSpec) {
            this(new IOException(message), dataSpec);
        }

        public InvalidResponseException(final IOException cause, final DataSpec dataSpec) {
            super(cause, dataSpec,
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE, TYPE_OPEN);
        }
    }

    static void checkContentType(final String contentType, final DataSpec dataSpec)
            throws InvalidResponseException {
        final String mime = contentType == null ? "" : contentType.split(";", 2)[0]
                .trim().toLowerCase(Locale.ROOT);
        if (mime.equals("text/html") || mime.equals("application/xhtml+xml")
                || mime.equals("application/json") || mime.equals("text/json")
                || mime.endsWith("+json")) {
            throw new InvalidResponseException("Invidious returned an error response ("
                    + mime + ") instead of media", dataSpec);
        }
    }

    static InputStream checkBody(final InputStream input, final DataSpec dataSpec)
            throws IOException {
        // Mid-stream ranges may begin with arbitrary bytes. Headers are still checked above.
        if (dataSpec.position != 0) {
            return input;
        }
        final PushbackInputStream buffered = new PushbackInputStream(input, PREFIX_SIZE);
        final byte[] prefix = new byte[PREFIX_SIZE];
        int count = 0;
        while (count < prefix.length) {
            final int read = buffered.read(prefix, count, prefix.length - count);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                break;
            }
            count += read;
        }
        final String text = new String(prefix, 0, count, StandardCharsets.UTF_8)
                .replaceFirst("^\uFEFF", "").trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("<!doctype html") || text.startsWith("<html")
                || text.startsWith("<head") || text.startsWith("<body")) {
            throw new InvalidResponseException("Invidious returned an HTML page instead of"
                    + " media", dataSpec);
        }
        if (text.matches("(?s)^\\{\\s*\"(?:error|message|detail)\"\\s*:.*")) {
            throw new InvalidResponseException("Invidious returned a JSON error instead of"
                    + " media", dataSpec);
        }
        buffered.unread(prefix, 0, count);
        return buffered;
    }
}
