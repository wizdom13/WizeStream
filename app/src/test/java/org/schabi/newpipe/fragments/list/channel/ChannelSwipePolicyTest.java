package org.schabi.newpipe.fragments.list.channel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChannelSwipePolicyTest {
    @Test
    public void pinnedChannelUsesMainTabSwipeWhenPreferenceEnabled() {
        assertFalse(ChannelSwipePolicy.isChannelSwipeEnabled(true, true));
    }

    @Test
    public void pinnedChannelKeepsSubTabSwipeWhenPreferenceDisabled() {
        assertTrue(ChannelSwipePolicy.isChannelSwipeEnabled(true, false));
    }

    @Test
    public void dedicatedChannelAlwaysKeepsSubTabSwipe() {
        assertTrue(ChannelSwipePolicy.isChannelSwipeEnabled(false, true));
        assertTrue(ChannelSwipePolicy.isChannelSwipeEnabled(false, false));
    }
}
