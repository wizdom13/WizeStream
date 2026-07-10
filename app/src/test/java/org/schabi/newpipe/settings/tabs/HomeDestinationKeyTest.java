package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeDestinationKeyTest {
    @Test
    public void kioskEqualityIncludesServiceId() {
        assertEquals(HomeDestinationKey.kiosk(0, "live"), HomeDestinationKey.kiosk(0, "live"));
        assertEquals(HomeDestinationKey.kiosk(0, "live").hashCode(),
                HomeDestinationKey.kiosk(0, "live").hashCode());
        assertNotEquals(HomeDestinationKey.kiosk(0, "live"), HomeDestinationKey.kiosk(1, "live"));
    }

    @Test
    public void fixedKeysAreDistinct() {
        assertNotEquals(HomeDestinationKey.FEED, HomeDestinationKey.SUBSCRIPTIONS);
        assertNotEquals(HomeDestinationKey.DOWNLOADS, HomeDestinationKey.HISTORY);
    }

    @Test
    public void drawerPolicyFiltersConfiguredDestinations() {
        final Set<HomeDestinationKey> configured = Set.of(HomeDestinationKey.DOWNLOADS,
                HomeDestinationKey.kiosk(0, "Trending"));
        assertFalse(HomeDrawerPolicy.shouldShow(configured, HomeDestinationKey.DOWNLOADS));
        assertTrue(HomeDrawerPolicy.shouldShow(configured, HomeDestinationKey.HISTORY));
        assertTrue(HomeDrawerPolicy.shouldShow(configured,
                HomeDestinationKey.kiosk(1, "Trending")));
    }

    @Test
    public void kioskMenuIdsMapToUnfilteredTargets() {
        final Map<Integer, HomeDrawerPolicy.KioskTarget> targets =
                HomeDrawerPolicy.assignKioskMenuIds(
                        Set.of(HomeDestinationKey.kiosk(0, "filtered")), 0,
                        List.of("filtered", "kept"), 100);
        assertEquals(1, targets.size());
        assertEquals("kept", targets.get(100).getKioskId());
        assertEquals(0, targets.get(100).getServiceId());
        assertFalse(targets.values().stream()
                .anyMatch(target -> "filtered".equals(target.getKioskId())));
    }
}
