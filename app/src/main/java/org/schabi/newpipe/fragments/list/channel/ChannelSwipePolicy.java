package org.schabi.newpipe.fragments.list.channel;

public final class ChannelSwipePolicy {
    private ChannelSwipePolicy() {
    }

    public static boolean isChannelSwipeEnabled(
            final boolean useAsFrontPage,
            final boolean swipeMainTabsOnPinnedChannels) {
        return !useAsFrontPage || !swipeMainTabsOnPinnedChannels;
    }
}
