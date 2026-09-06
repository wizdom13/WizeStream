package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CustomCollapsingToolbarLayoutTest {
    @Test
    public void activeVideoPlayerRaisesThePlayerLayer() {
        assertEquals(3.0f,
                CustomCollapsingToolbarLayout.playerLayerTranslationZ(true, 3.0f),
                0.0f);
    }

    @Test
    public void detachedPlayerRestoresTheNormalLayerOrder() {
        assertEquals(0.0f,
                CustomCollapsingToolbarLayout.playerLayerTranslationZ(false, 3.0f),
                0.0f);
    }

    @Test
    public void invalidDensityNeverCreatesANegativeLayerOffset() {
        assertEquals(0.0f,
                CustomCollapsingToolbarLayout.playerLayerTranslationZ(true, -1.0f),
                0.0f);
    }
}
