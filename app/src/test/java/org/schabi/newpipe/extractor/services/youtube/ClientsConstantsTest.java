package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClientsConstantsTest {
    @Test
    public void visionOsIdentityMatchesCurrentOfficialClient() {
        assertEquals("1.04", ClientsConstants.VISIONOS_CLIENT_VERSION);
        assertEquals("RealityDevice17,1", ClientsConstants.VISIONOS_DEVICE_MODEL);
        assertEquals("26.6.0.23O770", ClientsConstants.VISIONOS_VERSION);
        assertEquals("26_6_0", ClientsConstants.VISIONOS_USER_AGENT_VERSION);
    }
}
