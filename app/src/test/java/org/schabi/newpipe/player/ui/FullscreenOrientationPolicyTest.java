package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.junit.Test;
import org.schabi.newpipe.player.helper.PlayerRotationMode;

public class FullscreenOrientationPolicyTest {
    @Test
    public void stalePortraitFullscreenExitIsAppliedImmediately() {
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, false));
        assertTrue(FullscreenOrientationPolicy.isTargetOrientation(
                Configuration.ORIENTATION_PORTRAIT,
                Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void pendingExitOverridesVerticalVideoPortraitPreservation() {
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        true,
                        true,
                        Configuration.ORIENTATION_PORTRAIT,
                        FullscreenOrientationPolicy.EXIT_FULLSCREEN));
    }

    @Test
    public void pendingVerticalFullscreenEntryCanCompleteInPortrait() {
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        false,
                        true,
                        true,
                        Configuration.ORIENTATION_PORTRAIT,
                        FullscreenOrientationPolicy.ENTER_FULLSCREEN));
    }

    @Test
    public void automaticPortraitStillPreservesVerticalFullscreen() {
        assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        true,
                        true,
                        Configuration.ORIENTATION_UNDEFINED,
                        FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
    }

    @Test
    public void automaticHorizontalPhoneTracksConfiguration() {
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_LANDSCAPE,
                        false,
                        false,
                        true,
                        Configuration.ORIENTATION_UNDEFINED,
                        FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        false,
                        true,
                        Configuration.ORIENTATION_UNDEFINED,
                        FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
    }

    @Test
    public void pendingTransitionWaitsForItsRequestedOrientation() {
        assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        false,
                        true,
                        Configuration.ORIENTATION_LANDSCAPE,
                        FullscreenOrientationPolicy.ENTER_FULLSCREEN));
    }

    @Test
    public void fixedAndSensorLandscapeRequestsRemainDistinct() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.FIXED));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.SYSTEM));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.SENSORS));
    }

    @Test
    public void expandedPhoneVideoProtectsPhoneLayoutDuringLandscapeFullscreen() {
        assertTrue(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false, true, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false, true, true));
    }

    @Test
    public void staleWidePortraitLayoutRequiresRecreation() {
        assertTrue(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                true, Configuration.ORIENTATION_PORTRAIT, 900));
        assertFalse(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                false, Configuration.ORIENTATION_PORTRAIT, 900));
    }
}
