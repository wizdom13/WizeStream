/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PeertubeInstanceUrlTest {
    @Test
    public void normalizesInstanceInputToHttpsOrigin() {
        assertEquals("https://peertube.wtf",
                PeertubeInstanceUrl.normalize("peertube.wtf"));
        assertEquals("https://peertube.wtf",
                PeertubeInstanceUrl.normalize("https://peertube.wtf/"));
        assertEquals("https://peertube.wtf",
                PeertubeInstanceUrl.normalize("https://peertube.wtf/home"));
        assertEquals("https://example.org:8443",
                PeertubeInstanceUrl.normalize(
                        "https://Example.ORG:8443/videos/local?sort=-publishedAt#top"));
    }

    @Test
    public void rejectsNonHttpsAndMalformedAddresses() {
        assertNull(PeertubeInstanceUrl.normalize(null));
        assertNull(PeertubeInstanceUrl.normalize(""));
        assertNull(PeertubeInstanceUrl.normalize("http://peertube.wtf"));
        assertNull(PeertubeInstanceUrl.normalize("ftp://peertube.wtf"));
        assertNull(PeertubeInstanceUrl.normalize("https://user@example.org"));
        assertNull(PeertubeInstanceUrl.normalize("https://bad host"));
    }

    @Test
    public void detectsExplicitInsecureHttpScheme() {
        assertTrue(PeertubeInstanceUrl.hasInsecureHttpScheme("http://example.org"));
        assertTrue(PeertubeInstanceUrl.hasInsecureHttpScheme(" HTTP://example.org/path "));
        assertFalse(PeertubeInstanceUrl.hasInsecureHttpScheme("example.org"));
        assertFalse(PeertubeInstanceUrl.hasInsecureHttpScheme("https://example.org"));
    }
}
