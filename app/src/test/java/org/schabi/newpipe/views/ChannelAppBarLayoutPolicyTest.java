package org.schabi.newpipe.views;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChannelAppBarLayoutPolicyTest {
    @Test
    public void expandsOnlyWhenBannerBecomesVisible() {
        assertTrue(ChannelAppBarLayout.shouldExpandForBanner(false, true));
        assertFalse(ChannelAppBarLayout.shouldExpandForBanner(false, false));
        assertFalse(ChannelAppBarLayout.shouldExpandForBanner(true, true));
        assertFalse(ChannelAppBarLayout.shouldExpandForBanner(true, false));
    }
}
