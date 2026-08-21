package org.schabi.newpipe.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.util.InfoCache;

import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Application-wide HTTP/SOCKS proxy selector with local-network bypass rules. */
public final class AppProxySelector extends ProxySelector {
    private static AppProxySelector instance;

    @NonNull
    private final Context context;
    @NonNull
    private final SharedPreferences preferences;
    @NonNull
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    @Nullable
    private final ProxySelector fallbackSelector;
    @NonNull
    private final Authenticator scopedAuthenticator;
    @NonNull
    private final okhttp3.Authenticator okHttpAuthenticator;
    @NonNull
    private final ProxyCredentialStore credentialStore;
    @NonNull
    private volatile ProxyConfiguration configuration;

    private AppProxySelector(@NonNull final Context context,
                             @Nullable final ProxySelector fallbackSelector) {
        this.context = context.getApplicationContext();
        this.fallbackSelector = fallbackSelector;
        credentialStore = new ProxyCredentialStore(this.context);
        preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        configuration = readConfiguration();
        scopedAuthenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                final ProxyConfiguration current = configuration;
                if (!shouldAuthenticateProxy(getRequestorType(), getRequestingHost(),
                        getRequestingPort(), current.host, current.port,
                        current.username, current.password)) {
                    return null;
                }
                return new PasswordAuthentication(current.username,
                        current.password.toCharArray());
            }
        };
        okHttpAuthenticator = this::authenticateOkHttp;
        preferenceListener = (sharedPreferences, key) -> {
            if (isProxyPreference(key)) {
                refresh();
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        updateDefaultAuthenticator();
    }

    @NonNull
    public static synchronized AppProxySelector install(@NonNull final Context context) {
        if (instance == null) {
            instance = new AppProxySelector(context, ProxySelector.getDefault());
        }
        if (ProxySelector.getDefault() != instance) {
            ProxySelector.setDefault(instance);
        }
        instance.updateDefaultAuthenticator();
        return instance;
    }

    public static synchronized void refreshInstalled() {
        if (instance != null) {
            instance.refresh();
        }
    }

    @NonNull
    public okhttp3.Authenticator getOkHttpAuthenticator() {
        return okHttpAuthenticator;
    }

    public void refresh() {
        configuration = readConfiguration();
        updateDefaultAuthenticator();
        InfoCache.getInstance().clearCache();
        PlayerDataSource.invalidateYoutubeManifestCaches();
        final DownloaderImpl downloader = DownloaderImpl.getInstance();
        if (downloader != null) {
            downloader.getClient().connectionPool().evictAll();
        }
    }

    @NonNull
    @Override
    public List<Proxy> select(@NonNull final URI uri) {
        final ProxyConfiguration current = configuration;
        if (!current.isEnabled()) {
            return selectFallback(uri);
        }
        if (shouldBypass(uri)) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
        return Collections.singletonList(current.toProxy());
    }

    @Override
    public void connectFailed(@NonNull final URI uri,
                              @NonNull final SocketAddress socketAddress,
                              @NonNull final IOException exception) {
        if (!configuration.isEnabled() && fallbackSelector != null
                && fallbackSelector != this) {
            fallbackSelector.connectFailed(uri, socketAddress, exception);
        }
    }

    @NonNull
    public static HttpURLConnection openConnection(@NonNull final URL url) throws IOException {
        final AppProxySelector current = instance;
        if (current == null) {
            return (HttpURLConnection) url.openConnection();
        }
        try {
            final List<Proxy> proxies = current.select(url.toURI());
            final Proxy proxy = proxies.isEmpty() ? Proxy.NO_PROXY : proxies.get(0);
            return (HttpURLConnection) url.openConnection(proxy);
        } catch (final URISyntaxException e) {
            throw new IOException("Invalid URL for proxy selection", e);
        }
    }

    private boolean isProxyPreference(@Nullable final String key) {
        return key != null && (key.equals(context.getString(R.string.proxy_mode_key))
                || key.equals(context.getString(R.string.proxy_host_key))
                || key.equals(context.getString(R.string.proxy_port_key))
                || key.equals(context.getString(R.string.proxy_username_key)));
    }

    @NonNull
    private ProxyConfiguration readConfiguration() {
        final String mode = preferences.getString(context.getString(R.string.proxy_mode_key),
                context.getString(R.string.proxy_mode_disabled_value));
        final String host = preferences.getString(context.getString(R.string.proxy_host_key), "");
        final String portValue = preferences.getString(
                context.getString(R.string.proxy_port_key), "8080");
        final String username = preferences.getString(
                context.getString(R.string.proxy_username_key), "");
        final String password = credentialStore.readPassword();
        final int port;
        try {
            port = Integer.parseInt(portValue == null ? "" : portValue);
        } catch (final NumberFormatException ignored) {
            return ProxyConfiguration.create(mode, host, 0, username, password,
                    context.getString(R.string.proxy_mode_http_value),
                    context.getString(R.string.proxy_mode_socks_value));
        }
        return ProxyConfiguration.create(mode, host, port, username, password,
                context.getString(R.string.proxy_mode_http_value),
                context.getString(R.string.proxy_mode_socks_value));
    }

    private void updateDefaultAuthenticator() {
        Authenticator.setDefault(scopedAuthenticator);
    }

    @Nullable
    private okhttp3.Request authenticateOkHttp(@Nullable final okhttp3.Route route,
                                               @NonNull final okhttp3.Response response) {
        final ProxyConfiguration current = configuration;
        if (!current.hasAuthentication() || route == null
                || !isConfiguredProxy(route.proxy(), current.host, current.port)
                || response.request().header("Proxy-Authorization") != null) {
            return null;
        }
        return response.request().newBuilder()
                .header("Proxy-Authorization", okhttp3.Credentials.basic(
                        current.username, current.password))
                .build();
    }

    static boolean shouldAuthenticateProxy(@Nullable final Authenticator.RequestorType requestType,
                                           @Nullable final String requestHost,
                                           final int requestPort,
                                           @NonNull final String configuredHost,
                                           final int configuredPort,
                                           @NonNull final String username,
                                           @Nullable final String password) {
        return requestType == Authenticator.RequestorType.PROXY
                && requestHost != null && requestHost.equalsIgnoreCase(configuredHost)
                && requestPort == configuredPort
                && !username.isBlank() && password != null;
    }

    static boolean isConfiguredProxy(@Nullable final Proxy proxy,
                                     @NonNull final String configuredHost,
                                     final int configuredPort) {
        if (proxy == null || !(proxy.address() instanceof InetSocketAddress)) {
            return false;
        }
        final InetSocketAddress address = (InetSocketAddress) proxy.address();
        return address.getHostString().equalsIgnoreCase(configuredHost)
                && address.getPort() == configuredPort;
    }

    @NonNull
    private List<Proxy> selectFallback(@NonNull final URI uri) {
        if (fallbackSelector == null || fallbackSelector == this) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
        try {
            final List<Proxy> selected = fallbackSelector.select(uri);
            return selected == null || selected.isEmpty()
                    ? Collections.singletonList(Proxy.NO_PROXY) : selected;
        } catch (final RuntimeException ignored) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }
    }

    static boolean shouldBypass(@NonNull final URI uri) {
        final String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            return true;
        }
        final String rawHost = uri.getHost();
        if (rawHost == null || rawHost.isBlank()) {
            return true;
        }
        final String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".lan")
                || host.endsWith(".home") || host.endsWith(".internal")
                || !host.contains(".")) {
            return true;
        }
        if (host.contains(":")) {
            return isPrivateIpv6(host);
        }
        return isPrivateIpv4(host);
    }

    private static boolean isPrivateIpv4(@NonNull final String host) {
        final String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        final int[] octets = new int[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (final NumberFormatException ignored) {
            return false;
        }
        return octets[0] == 0 || octets[0] == 10 || octets[0] == 127
                || octets[0] == 169 && octets[1] == 254
                || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
                || octets[0] == 192 && octets[1] == 168
                || octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127
                || octets[0] >= 224;
    }

    private static boolean isPrivateIpv6(@NonNull final String host) {
        final String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        return normalized.equals("::1") || normalized.equals("::")
                || normalized.startsWith("fc") || normalized.startsWith("fd")
                || normalized.matches("^fe[89ab].*")
                || normalized.startsWith("::ffff:127.")
                || normalized.startsWith("::ffff:10.")
                || normalized.startsWith("::ffff:192.168.")
                || normalized.matches("^::ffff:172\\.(1[6-9]|2[0-9]|3[01])\\..*")
                || normalized.matches("^::ffff:100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\..*");
    }

    private static final class ProxyConfiguration {
        @Nullable
        private final Proxy.Type type;
        @NonNull
        private final String host;
        private final int port;
        @NonNull
        private final String username;
        @Nullable
        private final String password;

        private ProxyConfiguration(@Nullable final Proxy.Type type,
                                   @NonNull final String host,
                                   final int port,
                                   @NonNull final String username,
                                   @Nullable final String password) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        @NonNull
        static ProxyConfiguration create(@Nullable final String mode,
                                         @Nullable final String host,
                                         final int port,
                                         @Nullable final String username,
                                         @Nullable final String password,
                                         @NonNull final String httpValue,
                                         @NonNull final String socksValue) {
            final Proxy.Type type;
            if (httpValue.equals(mode)) {
                type = Proxy.Type.HTTP;
            } else if (socksValue.equals(mode)) {
                type = Proxy.Type.SOCKS;
            } else {
                return disabled();
            }
            final String normalizedHost = host == null ? "" : host.trim();
            final String normalizedUsername = username == null ? "" : username;
            if (normalizedHost.isEmpty() || port < 1 || port > 65_535) {
                // Fail closed if malformed settings arrive through a restored backup.
                return new ProxyConfiguration(type, "127.0.0.1", 1,
                        normalizedUsername, password);
            }
            return new ProxyConfiguration(type, normalizedHost, port,
                    normalizedUsername, password);
        }

        @NonNull
        static ProxyConfiguration disabled() {
            return new ProxyConfiguration(null, "", 0, "", null);
        }

        boolean isEnabled() {
            return type != null;
        }

        boolean hasAuthentication() {
            return isEnabled() && !username.isBlank() && password != null;
        }

        @NonNull
        Proxy toProxy() {
            return new Proxy(type, InetSocketAddress.createUnresolved(host, port));
        }
    }
}
