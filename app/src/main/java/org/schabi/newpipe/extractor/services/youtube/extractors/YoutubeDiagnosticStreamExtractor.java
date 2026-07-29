package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.services.youtube.WatchDataCache;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Temporary diagnostics for YouTube player-client fallback failures.
 */
public final class YoutubeDiagnosticStreamExtractor
        extends YoutubeMaterialFallbackStreamExtractor {

    public YoutubeDiagnosticStreamExtractor(final StreamingService service,
                                            final LinkHandler linkHandler,
                                            final WatchDataCache watchDataCache) {
        super(service, linkHandler, watchDataCache);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        try {
            super.onFetchPage(downloader);
        } catch (final ExtractionException error) {
            attachDiagnostics(error);
            throw error;
        } catch (final IOException error) {
            attachDiagnostics(error);
            throw error;
        }
    }

    private void attachDiagnostics(@Nonnull final Throwable error) {
        try {
            error.addSuppressed(new ParsingException(buildDiagnostics()));
        } catch (final Exception ignored) {
            // Diagnostics must never replace the original extraction failure.
        }
    }

    @Nonnull
    private String buildDiagnostics() {
        String requestedVideoId = "";
        try {
            requestedVideoId = getId();
        } catch (final Exception ignored) {
            // Optional diagnostic field.
        }

        final StringBuilder summary = new StringBuilder(
                "YouTube player fallback diagnostics: ");
        appendSelectedPlayer(summary, requestedVideoId);
        appendStreamingData(summary, "web", "webStreamingData");
        appendStreamingData(summary, "mweb", "mwebStreamingData");
        appendResponseState(summary, "next", "nextResponse");
        appendErrors(summary);
        return summary.toString();
    }

    private void appendSelectedPlayer(@Nonnull final StringBuilder summary,
                                      @Nonnull final String requestedVideoId) {
        final JsonObject response = playerResponse;
        JsonObject streamingData = getPrivateJsonObject("configuredStreamingData");
        if ((streamingData == null || streamingData.isEmpty()) && response != null) {
            streamingData = response.getObject("streamingData");
        }

        final boolean responseReceived = response != null && !response.isEmpty();
        final boolean streamingDataPresent = streamingData != null && !streamingData.isEmpty();
        final JsonObject details = responseReceived ? response.getObject("videoDetails") : null;
        final String responseVideoId = details == null
                ? "" : details.getString("videoId", "");
        final String idMatch = requestedVideoId.isEmpty() || responseVideoId.isEmpty()
                ? "unknown" : Boolean.toString(requestedVideoId.equals(responseVideoId));
        final JsonObject playability = responseReceived
                ? response.getObject("playabilityStatus") : null;
        final String status = playability == null
                ? "" : playability.getString("status", "");
        final String reason = playability == null
                ? "" : sanitize(playability.getString("reason", ""));
        final JsonArray formats = streamingDataPresent
                ? streamingData.getArray("formats") : null;
        final JsonArray adaptiveFormats = streamingDataPresent
                ? streamingData.getArray("adaptiveFormats") : null;

        summary.append("selected{client=").append(NewPipe.getYoutubePlayerClient())
                .append(",response=").append(responseReceived)
                .append(",videoIdMatch=").append(idMatch)
                .append(",status=").append(status.isEmpty() ? "none" : status)
                .append(",reason=").append(reason.isEmpty() ? "none" : reason)
                .append(",streamingData=").append(streamingDataPresent)
                .append(",formats=").append(formats == null ? 0 : formats.size())
                .append(",adaptiveFormats=")
                .append(adaptiveFormats == null ? 0 : adaptiveFormats.size())
                .append(",hls=").append(hasValue(streamingData, "hlsManifestUrl"))
                .append(",dash=").append(hasValue(streamingData, "dashManifestUrl"))
                .append(",sabr=").append(hasValue(streamingData, "serverAbrStreamingUrl"))
                .append("}");
    }

    private void appendStreamingData(@Nonnull final StringBuilder summary,
                                     @Nonnull final String label,
                                     @Nonnull final String fieldName) {
        final JsonObject streamingData = getPrivateJsonObject(fieldName);
        final JsonArray formats = streamingData == null
                ? null : streamingData.getArray("formats");
        final JsonArray adaptiveFormats = streamingData == null
                ? null : streamingData.getArray("adaptiveFormats");
        summary.append("; ").append(label).append("{streamingData=")
                .append(streamingData != null && !streamingData.isEmpty())
                .append(",formats=").append(formats == null ? 0 : formats.size())
                .append(",adaptiveFormats=")
                .append(adaptiveFormats == null ? 0 : adaptiveFormats.size())
                .append(",hls=").append(hasValue(streamingData, "hlsManifestUrl"))
                .append(",dash=").append(hasValue(streamingData, "dashManifestUrl"))
                .append(",sabr=").append(hasValue(streamingData, "serverAbrStreamingUrl"))
                .append("}");
    }

    private void appendResponseState(@Nonnull final StringBuilder summary,
                                     @Nonnull final String label,
                                     @Nonnull final String fieldName) {
        final JsonObject response = getPrivateJsonObject(fieldName);
        summary.append("; ").append(label).append("{response=")
                .append(response != null && !response.isEmpty())
                .append("}");
    }

    private void appendErrors(@Nonnull final StringBuilder summary) {
        summary.append("; collectedErrors=[");
        final int limit = Math.min(errors.size(), 8);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                summary.append(" | ");
            }
            final Throwable error = errors.get(i);
            appendThrowable(summary, error);
            if (error != null && error.getCause() != null) {
                summary.append(" <- ");
                appendThrowable(summary, error.getCause());
            }
        }
        if (errors.size() > limit) {
            summary.append(" | ...+").append(errors.size() - limit);
        }
        summary.append("]");
    }

    private void appendThrowable(@Nonnull final StringBuilder summary,
                                 @Nullable final Throwable error) {
        if (error == null) {
            summary.append("null");
            return;
        }
        summary.append(error.getClass().getSimpleName());
        final String message = sanitize(error.getMessage());
        if (!message.isEmpty()) {
            summary.append(":").append(message);
        }
    }

    private boolean hasValue(@Nullable final JsonObject object,
                             @Nonnull final String key) {
        return object != null && !object.getString(key, "").isEmpty();
    }

    @Nullable
    private JsonObject getPrivateJsonObject(@Nonnull final String fieldName) {
        try {
            final Field field = YoutubeStreamExtractor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (JsonObject) field.get(this);
        } catch (final ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nonnull
    private String sanitize(@Nullable final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)https?://\\S+", "<url>")
                .replaceAll("(?i)(authorization|cookie|pot|poToken|token)"
                                + "\\s*[:=]\\s*[^\\s,;]+",
                        "$1=<redacted>")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (sanitized.length() > 160) {
            sanitized = sanitized.substring(0, 160) + "...";
        }
        return sanitized;
    }
}
