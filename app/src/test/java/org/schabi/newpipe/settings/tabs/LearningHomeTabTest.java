package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class LearningHomeTabTest {
    @Test
    public void learningCanBeTheFirstSavedTabAndResolvesToItsDrawerDestination() throws Exception {
        final List<Tab> configured = List.of(Tab.Type.LEARNING.getTab(),
                Tab.Type.BOOKMARKS.getTab());
        final List<Tab> restored = TabsJsonHelper.getTabsFromJson(
                TabsJsonHelper.getJsonToSave(configured));
        assertEquals(configured, restored);
        assertTrue(restored.get(0) instanceof Tab.LearningTab);
        assertEquals(HomeDestinationKey.LEARNING, HomeDestinationResolver.fromTab(
                restored.get(0), 0, service -> "default"));
    }

    @Test
    public void disablingLearningHidesItWithoutDestroyingItsSavedPosition() {
        final List<Tab> configured = List.of(Tab.Type.LEARNING.getTab(),
                Tab.Type.BOOKMARKS.getTab());
        assertEquals(List.of(Tab.Type.BOOKMARKS.getTab()),
                TabsManager.visibleTabs(configured, false));
        assertEquals(2, configured.size());
        assertEquals(configured, TabsManager.visibleTabs(configured, true));
    }

    @Test
    public void aLearningOnlyLayoutStillHasAHomeWhenLearningIsDisabled() {
        assertEquals(TabsJsonHelper.getDefaultTabs(),
                TabsManager.visibleTabs(List.of(Tab.Type.LEARNING.getTab()), false));
    }
}
