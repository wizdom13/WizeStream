package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerRotationModeTest {
    @Test
    public void legacyPreferencesKeepTheirExistingRotationBehavior() {
        assertEquals(PlayerRotationMode.SENSORS, PlayerRotationMode.resolve(null, true));
        assertEquals(PlayerRotationMode.SYSTEM, PlayerRotationMode.resolve(null, false));
        assertEquals(PlayerRotationMode.SYSTEM, PlayerRotationMode.resolve("invalid", false));
    }

    @Test
    public void explicitModesOverrideTheLegacySwitch() {
        assertEquals(PlayerRotationMode.FIXED, PlayerRotationMode.resolve("fixed", true));
        assertEquals(PlayerRotationMode.SYSTEM, PlayerRotationMode.resolve("system", true));
        assertEquals(PlayerRotationMode.SENSORS, PlayerRotationMode.resolve("sensors", false));
    }
}
