package org.schabi.newpipe.settings.tabs;

import org.junit.Test;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.KioskTranslator;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TabTest {
    @Test
    public void checkIdDuplication() {
        final Set<Integer> usedIds = new HashSet<>();

        for (final Tab.Type type : Tab.Type.values()) {
            final boolean added = usedIds.add(type.getTabId());
            assertTrue("Id was already used: " + type.getTabId(), added);
        }
    }

    @Test
    public void bitChuteTrendingKioskHasAnIcon() {
        assertEquals(R.drawable.ic_whatshot,
                KioskTranslator.getKioskIcon("Trending This Month"));
    }
}
