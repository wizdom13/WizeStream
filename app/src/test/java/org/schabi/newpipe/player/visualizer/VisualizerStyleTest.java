package org.schabi.newpipe.player.visualizer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VisualizerStyleTest {
    @Test
    public void exposesFifteenSelectableStyles() {
        assertEquals(15, VisualizerStyle.values().length);
    }

    @Test
    public void everyStyleRoundTripsThroughItsPreferenceValue() {
        for (final VisualizerStyle style : VisualizerStyle.values()) {
            assertEquals(style,
                    VisualizerStyle.fromPreferenceValue(style.getPreferenceValue()));
        }
    }

    @Test
    public void unknownPreferenceFallsBackToCenteredBars() {
        assertEquals(VisualizerStyle.CENTERED_BARS,
                VisualizerStyle.fromPreferenceValue("removed_style"));
        assertEquals(VisualizerStyle.CENTERED_BARS,
                VisualizerStyle.fromPreferenceValue(null));
    }
}
