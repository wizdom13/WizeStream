package org.schabi.newpipe.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

public class AppProxySelectorTest {
    @Test
    public void localAndPrivateDestinationsBypassProxy() {
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://localhost")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("https://receiver.local/play")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://device")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://127.0.0.1")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://10.1.2.3")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://172.31.1.2")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://192.168.0.10")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://169.254.1.2")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://100.64.1.2")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://[::1]")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://[fd00::1]")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("http://[fe80::1]")));
    }

    @Test
    public void publicDestinationsUseConfiguredProxy() {
        assertFalse(AppProxySelector.shouldBypass(URI.create("https://www.youtube.com/watch?v=x")));
        assertFalse(AppProxySelector.shouldBypass(URI.create("https://googlevideo.com/video")));
        assertFalse(AppProxySelector.shouldBypass(URI.create("https://8.8.8.8/query")));
    }

    @Test
    public void nonHttpSchemesBypassProxy() {
        assertTrue(AppProxySelector.shouldBypass(URI.create("file:///storage/video.mp4")));
        assertTrue(AppProxySelector.shouldBypass(URI.create("content://media/video/1")));
    }

    @Test
    public void proxyAuthenticationIsScopedToConfiguredEndpoint() {
        assertTrue(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.PROXY, "proxy.example.com", 1080,
                "proxy.example.com", 1080, "user", "password"));
        assertFalse(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.SERVER, "proxy.example.com", 1080,
                "proxy.example.com", 1080, "user", "password"));
        assertFalse(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.PROXY, "other.example.com", 1080,
                "proxy.example.com", 1080, "user", "password"));
        assertFalse(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.PROXY, "proxy.example.com", 8080,
                "proxy.example.com", 1080, "user", "password"));
        assertFalse(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.PROXY, "proxy.example.com", 1080,
                "proxy.example.com", 1080, "", "password"));
        assertFalse(AppProxySelector.shouldAuthenticateProxy(
                Authenticator.RequestorType.PROXY, "proxy.example.com", 1080,
                "proxy.example.com", 1080, "user", null));
    }

    @Test
    public void okHttpProxyEndpointMatchingUsesHostAndPort() {
        final Proxy proxy = new Proxy(Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved("proxy.example.com", 8080));
        assertTrue(AppProxySelector.isConfiguredProxy(proxy, "proxy.example.com", 8080));
        assertTrue(AppProxySelector.isConfiguredProxy(proxy, "PROXY.EXAMPLE.COM", 8080));
        assertFalse(AppProxySelector.isConfiguredProxy(proxy, "other.example.com", 8080));
        assertFalse(AppProxySelector.isConfiguredProxy(proxy, "proxy.example.com", 1080));
        assertFalse(AppProxySelector.isConfiguredProxy(Proxy.NO_PROXY,
                "proxy.example.com", 8080));
    }
}
