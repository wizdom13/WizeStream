package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class TabsJsonHelperDownloadsTest {
    @Test
    public void downloadsTabSerializesAndDeserializes() throws Exception {
        final String json = TabsJsonHelper.getJsonToSave(List.of(Tab.Type.DOWNLOADS.getTab()));
        final List<Tab> tabs = TabsJsonHelper.getTabsFromJson(json);
        assertEquals(1, tabs.size());
        assertTrue(tabs.get(0) instanceof Tab.DownloadsTab);
    }

    @Test
    public void oldJsonStillDeserializes() throws Exception {
        final List<Tab> tabs = TabsJsonHelper.getTabsFromJson(
                "{\"tabs\":[{\"tab_id\":7},{\"tab_id\":2},{\"tab_id\":1},{\"tab_id\":3}]}");
        assertEquals(4, tabs.size());
        assertTrue(tabs.get(0) instanceof Tab.DefaultKioskTab);
    }
}
