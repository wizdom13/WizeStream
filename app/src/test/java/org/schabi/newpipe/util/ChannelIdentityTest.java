package org.schabi.newpipe.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChannelIdentityTest {
    @Test
    void canonicalIdIgnoresTabAndHostVariantsButPreservesCase() {
        final String url = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv";
        assertEquals(ChannelIdentity.stableKey(url), ChannelIdentity.stableKey(
                "https://music.youtube.com/channel/UCabcdefghijklmnopqrstuv/releases?view=0"));
        assertNotEquals(ChannelIdentity.stableKey(url), ChannelIdentity.stableKey(
                "https://www.youtube.com/channel/UCAbcdefghijklmnopqrstuv"));
        assertEquals("", ChannelIdentity.stableKey("https://youtube.com/@handle"));
        assertEquals("", ChannelIdentity.stableKey(
                "https://youtube.com.example/channel/UCabcdefghijklmnopqrstuv"));
        assertEquals("", ChannelIdentity.stableKey(null));
    }
}
