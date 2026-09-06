package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;

import org.junit.Test;

public class MainPlayerUiFullscreenTest {
    @Test
    public void horizontalVideoUsesOrientationAwareAction() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(false, false, false));
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(false, true, false));
    }

    @Test
    public void verticalVideoInPortraitTogglesFullscreenDirectly() {
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(true, false, true));
    }

    @Test
    public void verticalVideoInLockedLandscapeUsesOrientationAwareAction() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(true, true, true));
    }

    @Test
    public void verticalVideoInUnlockedLandscapeTogglesFullscreenDirectly() {
        assertFalse(MainPlayerUi.shouldUseScreenRotationAction(true, true, false));
    }

    @Test
    public void portraitResumeNeverAutoEntersFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_PORTRAIT,
                false,
                false,
                false));
    }

    @Test
    public void undefinedOrientationNeverAutoEntersFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_UNDEFINED,
                false,
                false,
                false));
    }

    @Test
    public void landscapePhoneVideoCanAutoEnterFullscreen() {
        assertTrue(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                false));
    }

    @Test
    public void fullscreenAudioAndTabletStatesDoNotAutoEnterAgain() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                true,
                false,
                false));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                true,
                false));
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                false,
                false,
                true));
    }

    @Test
    public void combinedAudioTabletAndFullscreenFlagsStayOutOfAutoFullscreen() {
        assertFalse(MainPlayerUi.shouldEnterFullscreenForConfiguration(
                Configuration.ORIENTATION_LANDSCAPE,
                true,
                true,
                true));
    }

    @Test
    public void orientationActionDoesNotDependOnFullscreenStateForHorizontalVideo() {
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(false, false, true));
        assertTrue(MainPlayerUi.shouldUseScreenRotationAction(false, true, true));
    }
}
