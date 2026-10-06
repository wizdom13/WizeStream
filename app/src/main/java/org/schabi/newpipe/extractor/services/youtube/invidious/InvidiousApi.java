package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** An extractor captures its instance so continuations cannot silently switch backend. */
final class InvidiousApi {
    final String base = InvidiousBackend.getInstance();

    String url(final String path) {
        return base + "/api/v1/" + path;
    }

    static String encode(final String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (final java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    String body(final String url) throws IOException, ExtractionException {
        if (!InvidiousBackend.isEnabled() || !base.equals(InvidiousBackend.getInstance())) {
            throw new ExtractionException("Invidious settings changed; reload this page");
        }
        if (!InvidiousBackend.sameOrigin(URI.create(base), URI.create(url))) {
            throw new ExtractionException("Invalid Invidious continuation URL");
        }
        final Response response = NewPipe.getDownloader().get(url);
        if (response.responseCode() < 200 || response.responseCode() >= 300) {
            throw new ExtractionException("Invidious returned HTTP " + response.responseCode()
                    + errorDetail(response.responseBody()));
        }
        final String mime = response.getHeader("Content-Type");
        if (mime != null && mime.toLowerCase(java.util.Locale.ROOT).contains("text/html")) {
            throw new ExtractionException("The Invidious instance returned an HTML page instead"
                    + " of API data. Check that this instance is available and supports the API.");
        }
        return response.responseBody();
    }

    private static String errorDetail(final String body) {
        try {
            final String error = JsonParser.object().from(body).getString("error", "")
                    .replaceAll("https?://[^\\s\"]+", "[URL]")
                    .replaceAll("[\\r\\n\\t]+", " ");
            if (!error.isEmpty()) {
                return ": " + error.substring(0, Math.min(error.length(), 240));
            }
        } catch (final JsonParserException ignored) {
            // Error pages can be HTML or plain text; do not include their bodies in reports.
        }
        return "";
    }

    JsonObject object(final String url) throws IOException, ExtractionException {
        try {
            final JsonObject result = JsonParser.object().from(body(url));
            if (result.get("error") != null && !result.get("error").toString().isEmpty()) {
                throw new ExtractionException("Invidious: " + result.get("error"));
            }
            return result;
        } catch (final JsonParserException e) {
            throw new ExtractionException("Invalid Invidious response", e);
        }
    }

    JsonArray array(final String url) throws IOException, ExtractionException {
        try {
            return JsonParser.array().from(body(url));
        } catch (final JsonParserException e) {
            throw new ExtractionException("Invalid Invidious response", e);
        }
    }

    String channelId(final String canonicalUrl) throws IOException, ExtractionException {
        final URI uri = URI.create(canonicalUrl);
        final String path = uri.getPath();
        if (path.startsWith("/channel/")) {
            return path.substring("/channel/".length()).split("/")[0];
        }
        final JsonObject resolved = object(url("resolveurl?url=" + encode(canonicalUrl)));
        final String id = resolved.getString("ucid", resolved.getString("browseId", ""));
        if (!id.startsWith("UC")) {
            throw new ExtractionException("Invidious could not resolve this channel");
        }
        return id;
    }

    /** Media returned by local=true may still be direct on misconfigured instances. */
    String localUrl(final String value) throws ExtractionException {
        if (value == null || value.isEmpty()) {
            throw new ExtractionException("Invidious returned an empty media URL");
        }
        final URI uri;
        try {
            uri = URI.create(base + "/").resolve(value);
        } catch (final IllegalArgumentException e) {
            throw new ExtractionException("Invalid Invidious media URL", e);
        }
        if (!InvidiousBackend.sameOrigin(URI.create(base), uri)) {
            throw new ExtractionException("The Invidious instance must proxy media locally");
        }
        return uri.toString();
    }

    String image(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final String routed = InvidiousBackend.routeImage(value, base);
        final URI uri = URI.create(base + "/").resolve(routed);
        // Do not load arbitrary external images supplied by an instance.
        return InvidiousBackend.sameOrigin(URI.create(base), uri) ? uri.toString() : "";
    }

    List<Image> images(final JsonArray array) {
        final List<Image> images = new ArrayList<>();
        if (array != null) {
            for (final Object entry : array) {
                if (!(entry instanceof JsonObject)) {
                    continue;
                }
                final JsonObject json = (JsonObject) entry;
                final String url = image(json.getString("url", ""));
                if (!url.isEmpty()) {
                    final int height = json.getInt("height", -1);
                    images.add(new Image(url, height, json.getInt("width", -1),
                            Image.ResolutionLevel.fromHeight(height)));
                }
            }
        }
        return images;
    }

    String firstImage(final JsonArray array) {
        final List<Image> images = images(array);
        return images.isEmpty() ? "" : images.get(0).getUrl();
    }

    static DateWrapper date(final JsonObject json) {
        final Object published = json.get("published");
        if (published instanceof Number) {
            return new DateWrapper(OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(((Number) published).longValue()),
                    java.time.ZoneOffset.UTC));
        }
        if (published instanceof String && !((String) published).isEmpty()) {
            try {
                return new DateWrapper(OffsetDateTime.parse((String) published));
            } catch (final java.time.format.DateTimeParseException ignored) {
                // Recommended video dates can be unavailable or textual.
            }
        }
        return null;
    }

    static String channelUrl(final JsonObject json) {
        final String id = json.getString("authorId", "");
        return id.isEmpty() ? "" : "https://www.youtube.com/channel/" + id;
    }
}
