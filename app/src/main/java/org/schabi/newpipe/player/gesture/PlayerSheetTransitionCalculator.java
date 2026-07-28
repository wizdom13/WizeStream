package org.schabi.newpipe.player.gesture;

final class PlayerSheetTransitionCalculator {
    private PlayerSheetTransitionCalculator() {
    }

    static int adjustedPeekHeight(final int playerPeekHeight,
                                  final int bottomNavigationHeight,
                                  final boolean bottomNavigationVisible) {
        if (!bottomNavigationVisible || playerPeekHeight <= 0) {
            return playerPeekHeight;
        }
        return playerPeekHeight + bottomNavigationHeight;
    }

    static float clampExpandedFraction(final float expandedFraction) {
        return Math.max(0.0f, Math.min(1.0f, expandedFraction));
    }

    static float bottomNavigationTranslation(final int bottomNavigationHeight,
                                             final float expandedFraction) {
        return bottomNavigationHeight * clampExpandedFraction(expandedFraction);
    }
}
