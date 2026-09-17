package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;

import org.junit.Test;


public class LockedOrientationFullscreenControllerTest {

    @Test
    public void orientationZonesUseHysteresisGapsAroundPortraitAndLandscape() {
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_PORTRAIT,
                LockedOrientationFullscreenController.orientationZone(0));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_PORTRAIT,
                LockedOrientationFullscreenController.orientationZone(25));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_PORTRAIT,
                LockedOrientationFullscreenController.orientationZone(335));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_LANDSCAPE,
                LockedOrientationFullscreenController.orientationZone(90));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_LANDSCAPE,
                LockedOrientationFullscreenController.orientationZone(115));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_LANDSCAPE,
                LockedOrientationFullscreenController.orientationZone(245));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_OTHER,
                LockedOrientationFullscreenController.orientationZone(45));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_OTHER,
                LockedOrientationFullscreenController.orientationZone(180));
        assertEquals(LockedOrientationFullscreenController.ORIENTATION_ZONE_OTHER,
                LockedOrientationFullscreenController.orientationZone(-1));
    }

    @Test
    public void lockedExpandedVideoCanEnterFullscreenAutomatically() {
        assertTrue(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, false));
    }

    @Test
    public void automaticEntryHonorsEligibilityAndProtectedPlayerState() {
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                false, true, true, true, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, false, true, true, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, false, true, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, true));
    }

    @Test
    public void onlyAutomaticallyEnteredFullscreenExitsWhenPhoneReturnsUpright() {
        assertTrue(LockedOrientationFullscreenController.shouldAutoExitFullscreen(
                true, LockedOrientationFullscreenController.ORIENTATION_ZONE_PORTRAIT));
        assertFalse(LockedOrientationFullscreenController.shouldAutoExitFullscreen(
                true, LockedOrientationFullscreenController.ORIENTATION_ZONE_LANDSCAPE));
        assertFalse(LockedOrientationFullscreenController.shouldAutoExitFullscreen(
                false, LockedOrientationFullscreenController.ORIENTATION_ZONE_PORTRAIT));
    }

    @Test
    public void explicitLandscapeRequestsProtectManualFullscreen() {
        assertTrue(LockedOrientationFullscreenController.isExplicitLandscapeOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertTrue(LockedOrientationFullscreenController.isExplicitLandscapeOrientation(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        assertTrue(LockedOrientationFullscreenController.isExplicitLandscapeOrientation(
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE));
        assertFalse(LockedOrientationFullscreenController.isExplicitLandscapeOrientation(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }

}
