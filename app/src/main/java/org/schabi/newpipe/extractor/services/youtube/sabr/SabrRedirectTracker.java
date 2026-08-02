package org.schabi.newpipe.extractor.services.youtube.sabr;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Tracks one consecutive server-directed redirect chain and rejects cycles. */
final class SabrRedirectTracker {
    private final int maxRedirects;
    private final Set<String> visitedUrls = new HashSet<>();
    private int followedRedirects;

    SabrRedirectTracker(final String initialUrl, final int maxRedirects) {
        if (maxRedirects < 1) {
            throw new IllegalArgumentException("SABR redirect limit must be positive");
        }
        this.maxRedirects = maxRedirects;
        reset(initialUrl);
    }

    synchronized void follow(final String redirectUrl) throws SabrRedirectException {
        final String redirectKey = canonicalKey(redirectUrl);
        if (visitedUrls.contains(redirectKey)) {
            throw new SabrRedirectException("SABR redirect loop detected: redirects="
                    + (followedRedirects + 1));
        }
        if (followedRedirects >= maxRedirects) {
            throw new SabrRedirectException("SABR redirect limit exceeded: redirects="
                    + (followedRedirects + 1));
        }
        visitedUrls.add(redirectKey);
        followedRedirects++;
    }

    synchronized void reset(final String currentUrl) {
        visitedUrls.clear();
        visitedUrls.add(canonicalKey(currentUrl));
        followedRedirects = 0;
    }

    private static String canonicalKey(final String url) {
        final URI uri = URI.create(url).normalize();
        final String scheme = uri.getScheme();
        final String host = uri.getHost();
        if (scheme == null || host == null) {
            return uri.toString();
        }
        final StringBuilder key = new StringBuilder()
                .append(scheme.toLowerCase(Locale.ROOT))
                .append("://")
                .append(host.toLowerCase(Locale.ROOT));
        if (uri.getPort() >= 0
                && !("https".equalsIgnoreCase(scheme) && uri.getPort() == 443)) {
            key.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
            key.append('/');
        } else {
            key.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            key.append('?').append(uri.getRawQuery());
        }
        return key.toString();
    }
}
