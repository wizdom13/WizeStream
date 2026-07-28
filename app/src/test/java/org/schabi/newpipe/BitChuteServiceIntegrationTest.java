package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.util.ServiceHelper;

public class BitChuteServiceIntegrationTest {
    @Test
    public void bitChuteIsVisible() {
        assertTrue(ServiceHelper.isServiceVisible(ServiceList.BitChute));
    }

    @Test
    public void bitChuteUsesItsBrandedDrawerIcon() {
        assertEquals(R.drawable.ic_bitchute,
                ServiceHelper.getIcon(ServiceList.BitChute.getServiceId()));
    }
}
