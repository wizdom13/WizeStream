package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.SQUARE;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN;

import android.content.res.Configuration;

import org.junit.Test;

public class MainPlayerUiFullscreenTest {
    @Test
    public void landscapeVideoUsesOrientationAwareAction() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(LANDSCAPE, false, false));
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(LANDSCAPE, true, false));
    }

    @Test
    public void portraitVideoInPortraitTogglesFullscreenDirectly() {
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(PORTRAIT, false, true));
    }

    @Test
    public void portraitVideoInLockedLandscapeUsesOrientationAwareAction() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(PORTRAIT, true, true));
    }

    @Test
    public void portraitVideoInUnlockedLandscapeUsesPortraitOrientationAction() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(PORTRAIT, true, false));
    }

    @Test
    public void unknownAndSquareContentNeverForceOrientationForFullscreenButton() {
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(UNKNOWN, false, true));
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(UNKNOWN, true, true));
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(SQUARE, false, true));
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(SQUARE, true, true));
    }

    @Test
    public void portraitResumeNeverAutoEntersFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_PORTRAIT,
                false,
                false,
                false,
                LANDSCAPE));
    }

    @Test
    public void undefinedOrientationNeverAutoEntersFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_UNDEFINED,
                false,
                false,
                false,
                LANDSCAPE));
    }

    @Test
    public void landscapePhoneVideoCanAutoEnterFullscreen() {
        assertTrue(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                LANDSCAPE));
    }

    @Test
    public void portraitSquareAndUnknownVideosDoNotAutoEnterFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                PORTRAIT));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                SQUARE));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false,
                UNKNOWN));
    }

    @Test
    public void fullscreenAudioAndTabletStatesDoNotAutoEnterAgain() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                true,
                false,
                false,
                LANDSCAPE));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                true,
                false,
                LANDSCAPE));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                true,
                LANDSCAPE));
    }

    @Test
    public void combinedAudioTabletAndFullscreenFlagsStayOutOfAutoFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                true,
                true,
                true,
                LANDSCAPE));
    }

    @Test
    public void explicitExitTargetDoesNotDependOnContentOrientation() {
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, LANDSCAPE));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, PORTRAIT));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, SQUARE));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, UNKNOWN));
    }

    @Test
    public void explicitLandscapeFullscreenStillTargetsLandscape() {
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, LANDSCAPE));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, PORTRAIT));
    }
}
