package org.schabi.newpipe.settings.sponsorblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;

public class SponsorBlockBehaviorTest {
    @Test
    public void behaviorValuesAreStable() {
        assertEquals("skip", SponsorBlockBehavior.SKIP.value);
        assertEquals("manual", SponsorBlockBehavior.MANUAL.value);
        assertEquals("dont_skip", SponsorBlockBehavior.DONT_SKIP.value);
    }

    @Test
    public void behaviorFallbackIsSkip() {
        assertEquals(SponsorBlockBehavior.SKIP, SponsorBlockBehavior.fromValue("unknown"));
        assertEquals(SponsorBlockBehavior.SKIP, SponsorBlockBehavior.fromValue(null));
    }

    @Test
    public void categoryDefaultsArePreserved() {
        assertTrue(SponsorBlockCategoryConfig.SPONSOR.defaultEnabled);
        for (final SponsorBlockCategoryConfig category : SponsorBlockCategoryConfig.ALL) {
            if (category != SponsorBlockCategoryConfig.SPONSOR) {
                assertFalse(category.defaultEnabled);
            }
        }
    }

    @Test
    public void highlightIsMarkerOnly() {
        assertEquals(SponsorBlockCategory.HIGHLIGHT,
                SponsorBlockCategoryConfig.HIGHLIGHT.apiCategory);
        assertEquals(SponsorBlockBehavior.DONT_SKIP,
                SponsorBlockCategoryConfig.HIGHLIGHT.defaultBehavior);
        assertTrue(SponsorBlockCategoryConfig.HIGHLIGHT.isMarkerOnly());
    }
}
