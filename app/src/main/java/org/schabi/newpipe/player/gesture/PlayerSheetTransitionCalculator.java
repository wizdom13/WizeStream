package org.schabi.newpipe.player.gesture;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING;

import androidx.annotation.Nullable;

final class PlayerSheetTransitionCalculator {
    private PlayerSheetTransitionCalculator() {
    }

    static int adjustedPeekHeight(final int playerPeekHeight,
                                  final int bottomNavigationHeight,
                                  final boolean bottomNavigationVisible,
                                  final int bottomSystemBarInset) {
        if (playerPeekHeight <= 0) {
            return 0;
        }
        final int safeBottomInset = Math.max(bottomSystemBarInset, 0);
        if (!bottomNavigationVisible) {
            return playerPeekHeight + safeBottomInset;
        }
        return playerPeekHeight + bottomNavigationHeight + safeBottomInset;
    }

    static float clampExpandedFraction(final float expandedFraction) {
        return Math.max(0.0f, Math.min(1.0f, expandedFraction));
    }

    static float expandedFractionForState(final int state,
                                          @Nullable final Float slideOffset) {
        if (state == STATE_COLLAPSED || state == STATE_HIDDEN) {
            return 0.0f;
        }
        if (state == STATE_EXPANDED || state == STATE_HALF_EXPANDED) {
            return 1.0f;
        }
        if ((state == STATE_DRAGGING || state == STATE_SETTLING) && slideOffset != null) {
            return clampExpandedFraction(slideOffset);
        }
        return 0.0f;
    }

    static float bottomNavigationTranslation(final int bottomNavigationHeight,
                                             final float expandedFraction) {
        return bottomNavigationHeight * clampExpandedFraction(expandedFraction);
    }

    static int navigationRailPlayerMargin(final int navigationRailWidth,
                                          final float expandedFraction) {
        final int safeWidth = Math.max(navigationRailWidth, 0);
        return Math.round(safeWidth * (1.0f - clampExpandedFraction(expandedFraction)));
    }
}
