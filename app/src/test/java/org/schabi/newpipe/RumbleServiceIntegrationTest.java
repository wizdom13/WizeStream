package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.util.ServiceHelper;

public class RumbleServiceIntegrationTest {
    @Test
    public void rumbleIsVisible() {
        assertTrue(ServiceHelper.isServiceVisible(ServiceList.Rumble));
    }

    @Test
    public void rumbleUsesItsBrandedDrawerIcon() {
        assertEquals(R.drawable.ic_rumble,
                ServiceHelper.getIcon(ServiceList.Rumble.getServiceId()));
    }
}
