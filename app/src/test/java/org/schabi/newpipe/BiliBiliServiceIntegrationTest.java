package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.util.ServiceHelper;

public class BiliBiliServiceIntegrationTest {
    @Test
    public void biliBiliIsVisibleWhileNicoNicoRemainsHidden() {
        assertTrue(ServiceHelper.isServiceVisible(ServiceList.BiliBili));
        assertFalse(ServiceHelper.isServiceVisible(ServiceList.NicoNico));
    }

    @Test
    public void biliBiliUsesItsBrandedDrawerIcon() {
        assertEquals(R.drawable.ic_bilibili,
                ServiceHelper.getIcon(ServiceList.BiliBili.getServiceId()));
    }
}
