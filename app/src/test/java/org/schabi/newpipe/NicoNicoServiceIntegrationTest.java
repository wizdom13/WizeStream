package org.schabi.newpipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.util.ServiceHelper;

public class NicoNicoServiceIntegrationTest {
    @Test
    public void nicoNicoIsVisible() {
        assertTrue(ServiceHelper.isServiceVisible(ServiceList.NicoNico));
    }

    @Test
    public void nicoNicoUsesItsBrandedDrawerIcon() {
        assertEquals(R.drawable.ic_niconico,
                ServiceHelper.getIcon(ServiceList.NicoNico.getServiceId()));
    }
}
