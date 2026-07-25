package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;

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
}
