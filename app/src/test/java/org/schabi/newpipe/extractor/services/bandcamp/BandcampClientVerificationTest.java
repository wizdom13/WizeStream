package org.schabi.newpipe.extractor.services.bandcamp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.bandcamp.extractors.BandcampExtractorHelper;

import java.util.List;
import java.util.Map;

public class BandcampClientVerificationTest {
    @Test
    public void contentHeadersContainAValidAnonymousClientCookie() {
        final Map<String, List<String>> headers =
                BandcampExtractorHelper.getClientVerificationHeaders();

        assertEquals(1, headers.size());
        assertEquals(1, headers.get("Cookie").size());
        assertTrue(headers.get("Cookie").get(0)
                .matches("cart_client_id:\\\"[0-9a-f]{64}\\\""));
    }
}
