package org.schabi.newpipe.extractor.services.youtube.invidious;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** Process-wide policy. Settings changes restart the process to retire active direct connections. */
public final class InvidiousBackend {
    private static volatile boolean enabled;
    private static volatile String instance = "";

    private InvidiousBackend() { }

    public static void configure(final boolean useInvidious, final String instanceUrl) {
        instance = instanceUrl == null ? "" : instanceUrl;
        enabled = useInvidious;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getInstance() {
        // Invalid restored settings must fail closed, rather than selecting the direct extractor.
        return normalizeInstance(instance);
    }

    public static String normalizeInstance(final String value) {
        try {
            final URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || isYoutubeHost(uri.getHost())
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Use the HTTPS root URL of an Invidious inst"
                        + "ance");
            }
            return "https://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
        } catch (final java.net.URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid Invidious instance URL", e);
        }
    }

    public static boolean isYoutubeHost(final String value) {
        if (value == null) {
            return false;
        }
        final String host = value.toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        for (final String domain : new String[]{"youtube.com", "youtu.be", "youtube-nocookie.com",
                "googlevideo.com", "ytimg.com", "ggpht.com", "googleusercontent.com",
                "youtubei.googleapis.com", "youtube.googleapis.com", "jnn-pa.googleapis.com"}) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    public static void checkRequest(final URI uri) throws IOException {
        if (enabled && isYoutubeHost(uri.getHost())) {
            throw new IOException("Direct YouTube requests are disabled while using Invidious");
        }
    }

    public static void checkRedirect(final URI source, final URI target) throws IOException {
        checkRequest(target);
        if (enabled && isInstanceOrigin(source)
                && !sameOrigin(source, target)) {
            throw new IOException("Invidious redirected outside the configured instance");
        }
    }

    public static void checkInstanceRequest(final URI uri) throws IOException {
        if (!enabled || !isInstanceOrigin(uri)) {
            throw new IOException("Invidious media must stay on the configured instance");
        }
    }

    public static boolean isInstanceOrigin(final URI uri) {
        try {
            return sameOrigin(URI.create(getInstance()), uri);
        } catch (final IllegalArgumentException e) {
            // Invalid restored settings must not break unrelated services.
            return false;
        }
    }

    public static boolean isYoutubeImageHost(final String value) {
        if (value == null) {
            return false;
        }
        final String host = value.toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        for (final String domain : new String[]{"ytimg.com", "ggpht.com",
                "googleusercontent.com"}) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    public static boolean sameOrigin(final URI first, final URI second) {
        return first.getHost() != null && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && port(first) == port(second) && second.getRawUserInfo() == null;
    }

    private static int port(final URI uri) {
        return uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                : uri.getPort();
    }

    /** Route old thumbnail/avatar URLs, including URLs already stored in the database. */
    public static String routeImage(final String url, final String base) {
        final URI uri = URI.create(url.startsWith("//") ? "https:" + url : url);
        final String host = uri.getHost() == null ? null
                : uri.getHost().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        final String path = uri.getRawPath();
        final String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        if (host != null && (host.equals("ytimg.com") || host.endsWith(".ytimg.com"))) {
            return base + path + query;
        }
        if (host != null && (host.equals("ggpht.com") || host.endsWith(".ggpht.com")
                || host.equals("googleusercontent.com")
                || host.endsWith(".googleusercontent.com"))) {
            return base + "/ggpht" + path + query;
        }
        return url;
    }
}
