package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainPlayerGestureListenerTest {
    private static final float FLOAT_TOLERANCE = 0.001f;

    @Test
    public void embeddedDownwardSwipeDelegatesToBottomSheet() {
        assertTrue(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 5f, 80f));
    }

    @Test
    public void disabledSwipeDownSettingDoesNotDelegate() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, false, 5f, 80f));
    }

    @Test
    public void fullscreenDownwardSwipeRemainsPlayerGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                true, true, 5f, 80f));
    }

    @Test
    public void embeddedUpwardSwipeRemainsFullscreenGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 5f, -80f));
    }

    @Test
    public void horizontalSwipeRemainsPlayerGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 80f, 50f));
    }

    @Test
    public void movementBelowThresholdDoesNotDelegate() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 2f, 40f));
    }

    @Test
    public void verticalTwoFingerMovementLocksSpeedGesture() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.SPEED,
                MainPlayerGestureListener.classifyTwoFingerGesture(60f, 5f, 8f, 12f));
    }

    @Test
    public void spanDominantMovementIsTreatedAsPinch() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.IGNORED,
                MainPlayerGestureListener.classifyTwoFingerGesture(20f, 2f, 50f, 12f));
    }

    @Test
    public void twoFingerGestureWaitsForClearIntent() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.PENDING,
                MainPlayerGestureListener.classifyTwoFingerGesture(8f, 4f, 3f, 12f));
    }

    @Test
    public void upwardTwoFingerMovementIncreasesSpeedInFineSteps() {
        assertEquals(1.10f,
                MainPlayerGestureListener.calculatePlaybackSpeed(1.0f, 48f, 24f),
                FLOAT_TOLERANCE);
    }

    @Test
    public void downwardTwoFingerMovementCanLandExactlyOnNormalSpeed() {
        assertEquals(1.0f,
                MainPlayerGestureListener.calculatePlaybackSpeed(1.13f, -72f, 24f),
                FLOAT_TOLERANCE);
    }

    @Test
    public void twoFingerSpeedIsClampedToPlayerLimits() {
        assertEquals(3.0f,
                MainPlayerGestureListener.calculatePlaybackSpeed(2.95f, 240f, 24f),
                FLOAT_TOLERANCE);
        assertEquals(0.10f,
                MainPlayerGestureListener.calculatePlaybackSpeed(0.15f, -240f, 24f),
                FLOAT_TOLERANCE);
    }
}
