package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class PlayerSheetTransitionCalculatorTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

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
    public void navigationRailMarginLeavesTheExpandedPlayerEdgeToEdge() {
        assertEquals(80,
                PlayerSheetTransitionCalculator.navigationRailPlayerMargin(80, 0.0f));
        assertEquals(40,
                PlayerSheetTransitionCalculator.navigationRailPlayerMargin(80, 0.5f));
        assertEquals(0,
                PlayerSheetTransitionCalculator.navigationRailPlayerMargin(80, 1.0f));
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
        assertTrue(PlayerSheetTransitionCalculator.isActiveTransitionState(
                BottomSheetBehavior.STATE_DRAGGING));
        assertTrue(PlayerSheetTransitionCalculator.isActiveTransitionState(
                BottomSheetBehavior.STATE_SETTLING));
        assertFalse(PlayerSheetTransitionCalculator.isActiveTransitionState(
                BottomSheetBehavior.STATE_COLLAPSED));

        assertEquals(0.35f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_DRAGGING, 0.35f), 0.0f);
        assertEquals(0.75f,
                PlayerSheetTransitionCalculator.expandedFractionForState(
                        BottomSheetBehavior.STATE_SETTLING, 0.75f), 0.0f);
    }

    @Test
    public void playerAndMiniPlayerChromeUseComplementaryEasedProgress() {
        assertEquals(0.0f,
                PlayerSheetTransitionCalculator.playerChromeAlpha(0.0f), 0.0f);
        assertEquals(0.15625f,
                PlayerSheetTransitionCalculator.playerChromeAlpha(0.25f), 0.00001f);
        assertEquals(0.5f,
                PlayerSheetTransitionCalculator.playerChromeAlpha(0.5f), 0.0f);
        assertEquals(0.84375f,
                PlayerSheetTransitionCalculator.playerChromeAlpha(0.75f), 0.00001f);
        assertEquals(1.0f,
                PlayerSheetTransitionCalculator.playerChromeAlpha(1.0f), 0.0f);

        assertEquals(1.0f,
                PlayerSheetTransitionCalculator.miniPlayerChromeAlpha(0.0f), 0.0f);
        assertEquals(0.5f,
                PlayerSheetTransitionCalculator.miniPlayerChromeAlpha(0.5f), 0.0f);
        assertEquals(0.0f,
                PlayerSheetTransitionCalculator.miniPlayerChromeAlpha(1.0f), 0.0f);
    }

    @Test
    public void playerSheetTransitionKeepsTheCurrentFrameVisibleWhileMoving() throws Exception {
        final String behavior = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/player/gesture/CustomBottomSheetBehavior.java"));

        assertTrue(behavior.contains("suppressSurfaceBlackout(bottomSheet);"));
        assertTrue(behavior.contains("surfaceForeground.setVisibility(View.INVISIBLE)"));
        assertTrue(behavior.contains("loadingPanel.setBackgroundColor(Color.TRANSPARENT)"));
        assertTrue(behavior.contains("playerChromeAlpha(expandedFraction)"));
        assertTrue(behavior.contains("miniPlayerChromeAlpha(expandedFraction)"));
    }
}
