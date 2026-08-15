package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public class HomeDestinationResolverTest {
    @Test
    public void fixedTabsResolveToFixedKeys() {
        assertEquals(HomeDestinationKey.SUBSCRIPTIONS,
                resolve(Tab.Type.SUBSCRIPTIONS.getTab()));
        assertEquals(HomeDestinationKey.FEED, resolve(Tab.Type.FEED.getTab()));
        assertEquals(HomeDestinationKey.BOOKMARKS, resolve(Tab.Type.BOOKMARKS.getTab()));
        assertEquals(HomeDestinationKey.DOWNLOADS, resolve(Tab.Type.DOWNLOADS.getTab()));
        assertEquals(HomeDestinationKey.HISTORY, resolve(Tab.Type.HISTORY.getTab()));
        assertEquals(HomeDestinationKey.LOCAL_MEDIA, resolve(Tab.Type.LOCAL_MEDIA.getTab()));
    }

    @Test
    public void explicitKioskTabResolvesWithServiceIdAndKioskId() {
        assertEquals(HomeDestinationKey.kiosk(3, "live"),
                resolve(new Tab.KioskTab(3, "live")));
        assertNotEquals(resolve(new Tab.KioskTab(3, "live")),
                resolve(new Tab.KioskTab(4, "live")));
    }

    @Test
    public void defaultKioskTabUsesInjectedResolver() {
        assertEquals(HomeDestinationKey.kiosk(7, "default"),
                HomeDestinationResolver.fromTab(Tab.Type.DEFAULT_KIOSK.getTab(),
                        7, serviceId -> "default"));
    }

    @Test
    public void fromTabsCollectsSupportedKeysAndIgnoresNonDrawerTabs() {
        final Set<HomeDestinationKey> keys = HomeDestinationResolver.fromTabs(List.of(
                Tab.Type.SUBSCRIPTIONS.getTab(),
                Tab.Type.FEED.getTab(),
                Tab.Type.BOOKMARKS.getTab(),
                Tab.Type.DOWNLOADS.getTab(),
                Tab.Type.HISTORY.getTab(),
                Tab.Type.LOCAL_MEDIA.getTab(),
                new Tab.KioskTab(0, "live"),
                new Tab.ChannelTab(0, "https://example.com", "Channel"),
                new Tab.PlaylistTab(1L, "Playlist"),
                new Tab.FeedGroupTab(1L, "Group", 1)),
                0, serviceId -> "default");

        assertTrue(keys.contains(HomeDestinationKey.SUBSCRIPTIONS));
        assertTrue(keys.contains(HomeDestinationKey.FEED));
        assertTrue(keys.contains(HomeDestinationKey.BOOKMARKS));
        assertTrue(keys.contains(HomeDestinationKey.DOWNLOADS));
        assertTrue(keys.contains(HomeDestinationKey.HISTORY));
        assertTrue(keys.contains(HomeDestinationKey.LOCAL_MEDIA));
        assertTrue(keys.contains(HomeDestinationKey.kiosk(0, "live")));
        assertFalse(keys.contains(HomeDestinationKey.kiosk(0, "default")));
        assertEquals(7, keys.size());
    }

    private HomeDestinationKey resolve(final Tab tab) {
        return HomeDestinationResolver.fromTab(tab, 0, serviceId -> "default");
    }
}
