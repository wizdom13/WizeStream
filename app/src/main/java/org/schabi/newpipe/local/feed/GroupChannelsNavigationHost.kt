package org.schabi.newpipe.local.feed

enum class GroupChannelsNavigationHost {
    ACTIVITY;

    companion object {
        @JvmStatic
        fun forOpenedFeedGroup(): GroupChannelsNavigationHost = ACTIVITY
    }
}
