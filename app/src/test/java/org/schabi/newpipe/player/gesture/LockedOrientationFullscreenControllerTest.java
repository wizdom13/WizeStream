package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LockedOrientationFullscreenControllerTest {
    private final Path projectDirectory = Files.exists(Path.of("src/main"))
            ? Path.of("src/main")
            : Path.of("app/src/main");

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
                true, true, true, true, false, false, false, false));
    }

    @Test
    public void automaticEntryDoesNotTakeOverManualOrUnsupportedSessions() {
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                false, true, true, true, false, false, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, false, true, true, false, false, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, false, true, false, false, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, false, false, false, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, true, false, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, false, true, false, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, false, false, true, false));
        assertFalse(LockedOrientationFullscreenController.shouldAutoEnterFullscreen(
                true, true, true, true, false, false, false, true));
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

    @Test
    public void rotateToFullscreenIsEnabledByDefaultAndWiredToPlayerSheet() throws Exception {
        final String settings = Files.readString(projectDirectory.resolve(
                "res/xml/video_audio_settings.xml"));
        final String behavior = Files.readString(projectDirectory.resolve(
                "java/org/schabi/newpipe/player/gesture/CustomBottomSheetBehavior.java"));

        final int preference = settings.indexOf("android:key=\"@string/rotate_to_fullscreen_key\"");
        final int defaultValue = settings.lastIndexOf("android:defaultValue=\"true\"", preference);
        assertTrue(preference >= 0 && defaultValue >= 0 && preference - defaultValue < 100);
        assertTrue(behavior.contains("lockedOrientationFullscreenController.attach(child, getState())"));
        assertTrue(behavior.contains(
                "lockedOrientationFullscreenController.onPlayerSheetStateChanged(newState)"));
    }
}
