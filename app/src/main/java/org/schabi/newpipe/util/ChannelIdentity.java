package org.schabi.newpipe.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Stable YouTube IDs encoded without losing their case through legacy sync normalization. */
public final class ChannelIdentity {
    private ChannelIdentity() {
    }

    public static String stableKey(final String url) {
        if (url == null) {
            return "";
        }
        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("youtube.com")
                    || host.equalsIgnoreCase("www.youtube.com")
                    || host.equalsIgnoreCase("m.youtube.com")
                    || host.equalsIgnoreCase("music.youtube.com"))) {
                return "";
            }
            final String[] parts = uri.getPath().split("/");
            if (parts.length < 3 || !"channel".equals(parts[1])
                    || !parts[2].matches("UC[A-Za-z0-9_-]{22}")) {
                return "";
            }
            final StringBuilder key = new StringBuilder("youtube-channel-id:");
            for (final char value : parts[2].toCharArray()) {
                key.append(String.format(Locale.ROOT, "%02x", (int) value));
            }
            return key.toString();
        } catch (final URISyntaxException error) {
            return "";
        }
    }
}
