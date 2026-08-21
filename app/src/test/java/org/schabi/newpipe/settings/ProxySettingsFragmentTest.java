package org.schabi.newpipe.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProxySettingsFragmentTest {
    @Test
    public void validatesProxyHosts() {
        assertTrue(ProxySettingsFragment.isValidHost("proxy.example.com"));
        assertTrue(ProxySettingsFragment.isValidHost("127.0.0.1"));
        assertTrue(ProxySettingsFragment.isValidHost("::1"));
        assertFalse(ProxySettingsFragment.isValidHost(""));
        assertFalse(ProxySettingsFragment.isValidHost("https://proxy.example.com"));
        assertFalse(ProxySettingsFragment.isValidHost("proxy.example.com/path"));
        assertFalse(ProxySettingsFragment.isValidHost("proxy example.com"));
    }

    @Test
    public void validatesProxyPorts() {
        assertTrue(ProxySettingsFragment.isValidPort("1"));
        assertTrue(ProxySettingsFragment.isValidPort("8080"));
        assertTrue(ProxySettingsFragment.isValidPort("65535"));
        assertFalse(ProxySettingsFragment.isValidPort("0"));
        assertFalse(ProxySettingsFragment.isValidPort("65536"));
        assertFalse(ProxySettingsFragment.isValidPort("proxy"));
    }
}
