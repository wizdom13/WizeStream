package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainPlayerGestureListenerTest {
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
}
