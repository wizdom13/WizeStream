package org.schabi.newpipe.player.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class ChannelPlaybackProfileManagerTest {
    @Test
    public void profileKeyIsStable() {
        assertEquals(
                "channel_playback_profile.v1.0."
                        + "9f272e3636ce5bfe795ada3d34acea63aba896ea55743816b3fa5aa320ec6a71",
                ChannelPlaybackProfileManager.profileKey(0, "https://example.com/channel/test"));
    }

    @Test
    public void profileKeyIsScopedByServiceAndChannel() {
        final String channel = "https://example.com/channel/test";
        assertNotEquals(
                ChannelPlaybackProfileManager.profileKey(0, channel),
                ChannelPlaybackProfileManager.profileKey(1, channel));
        assertNotEquals(
                ChannelPlaybackProfileManager.profileKey(0, channel),
                ChannelPlaybackProfileManager.profileKey(0, channel + "-other"));
    }

    @Test
    public void profileKeyRejectsMissingChannelIdentity() {
        assertNull(ChannelPlaybackProfileManager.profileKey(0, null));
        assertNull(ChannelPlaybackProfileManager.profileKey(0, ""));
        assertNull(ChannelPlaybackProfileManager.profileKey(0, "   "));
    }
}
