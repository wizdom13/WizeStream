package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.Collections;
import java.util.Set;

public class ChannelTabHelperTest {
    @Test
    public void podcastsAreRecognizedAsAVisibleStreamTab() {
        final Context context = mock(Context.class);
        final SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getString(R.string.show_channel_tabs_key)).thenReturn("channel_tabs");
        when(context.getString(R.string.show_channel_tabs_podcasts))
                .thenReturn("show_channel_tabs_podcasts");
        when(preferences.getStringSet("channel_tabs", null))
                .thenReturn(Set.of("show_channel_tabs_podcasts"));

        assertTrue(ChannelTabHelper.isStreamsTab(ChannelTabs.PODCASTS));
        assertTrue(ChannelTabHelper.showChannelTab(context, preferences, ChannelTabs.PODCASTS));
        assertEquals(R.string.channel_tab_podcasts,
                ChannelTabHelper.getTranslationKey(ChannelTabs.PODCASTS));
    }

    @Test
    public void podcastsCanBeSelectedForFeedFetching() {
        final Context context = mock(Context.class);
        final SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getString(R.string.feed_fetch_channel_tabs_key))
                .thenReturn("feed_fetch_channel_tabs");
        when(context.getString(R.string.fetch_channel_tabs_podcasts))
                .thenReturn("fetch_channel_tabs_podcasts");
        when(preferences.getStringSet("feed_fetch_channel_tabs", null))
                .thenReturn(Set.of("fetch_channel_tabs_podcasts"));

        assertTrue(ChannelTabHelper.fetchFeedChannelTab(
                context, preferences, handler(ChannelTabs.PODCASTS)));
    }

    @Test
    public void postsAreVisibleButExcludedFromFeedFetching() {
        final Context context = mock(Context.class);
        final SharedPreferences preferences = mock(SharedPreferences.class);
        when(context.getString(R.string.show_channel_tabs_key)).thenReturn("channel_tabs");
        when(context.getString(R.string.show_channel_tabs_posts))
                .thenReturn("show_channel_tabs_posts");
        when(preferences.getStringSet("channel_tabs", null))
                .thenReturn(Set.of("show_channel_tabs_posts"));

        assertFalse(ChannelTabHelper.isStreamsTab(ChannelTabs.POSTS));
        assertTrue(ChannelTabHelper.showChannelTab(context, preferences, ChannelTabs.POSTS));
        assertEquals(R.string.channel_tab_posts,
                ChannelTabHelper.getTranslationKey(ChannelTabs.POSTS));
        assertFalse(ChannelTabHelper.fetchFeedChannelTab(
                context, preferences, handler(ChannelTabs.POSTS)));
    }

    private static ListLinkHandler handler(final String tab) {
        return new ListLinkHandler(
                "https://www.youtube.com/channel/UC123/" + tab,
                "https://www.youtube.com/channel/UC123/" + tab,
                "channel/UC123",
                Collections.singletonList(
                        new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab)),
                null);
    }
}
