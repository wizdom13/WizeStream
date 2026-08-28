package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.Test;

public class PlayerSheetTransitionCalculatorTest {
    @Test
    public void collapsedPeekHeightIncludesVisibleBottomNavigationAndSystemBar() {
        assertEquals(156,
                PlayerSheetTransitionCalculator.adjustedPeekHeight(60, 72, true, 24));
    }

    @Test
    public void hiddenPlayerDoesNotReserveBottomNavigationSpace() {
        assertEquals(0,
                PlayerSheetTransitionCalculator.adjustedPeekHeight(0, 72, true, 24));
    }

    @Test
    public void collapsedPeekHeightIncludesSystemBarWithoutBottomNavigation() {
        assertEquals(84,
                PlayerSheetTransitionCalculator.adjustedPeekHeight(60, 72, false, 24));
    }

    @Test
    public void negativeSystemBarInsetsAreIgnored() {
        assertEquals(132,
                PlayerSheetTransitionCalculator.adjustedPeekHeight(60, 72, true, -24));
    }

    @Test
    public void navigationSlidesOnlyWithinItsOwnHeight() {
        assertEquals(0.0f,
                PlayerSheetTransitionCalculator.bottomNavigationTranslation(72, -1.0f), 0.0f);
        assertEquals(36.0f,
                PlayerSheetTransitionCalculator.bottomNavigationTranslation(72, 0.5f), 0.0f);
        assertEquals(72.0f,
                PlayerSheetTransitionCalculator.bottomNavigationTranslation(72, 2.0f), 0.0f);
    }

    @Test
    public void stableCollapsedAndHiddenStatesIgnoreLateSlideOffsets() {
        assertEquals(0.0f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_COLLAPSED, 0.65f), 0.0f);
        assertEquals(0.0f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_HIDDEN, 0.65f), 0.0f);
    }

    @Test
    public void stableExpandedStatesIgnoreLateSlideOffsets() {
        assertEquals(1.0f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_EXPANDED, 0.2f), 0.0f);
        assertEquals(1.0f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_HALF_EXPANDED, 0.2f), 0.0f);
    }

    @Test
    public void activePlayerTransitionsFollowTheCurrentSlideOffset() {
        assertEquals(0.35f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_DRAGGING, 0.35f), 0.0f);
        assertEquals(0.75f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_SETTLING, 0.75f), 0.0f);
    }
}
