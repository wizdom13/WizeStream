package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerSheetTransitionCalculatorTest {
    @Test
    public void collapsedPeekHeightIncludesVisibleBottomNavigation() {
        assertEquals(132, PlayerSheetTransitionCalculator.adjustedPeekHeight(60, 72, true));
    }

    @Test
    public void hiddenPlayerDoesNotReserveBottomNavigationSpace() {
        assertEquals(0, PlayerSheetTransitionCalculator.adjustedPeekHeight(0, 72, true));
    }

    @Test
    public void playerPeekHeightIsUnchangedWithoutBottomNavigation() {
        assertEquals(60, PlayerSheetTransitionCalculator.adjustedPeekHeight(60, 72, false));
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
}
