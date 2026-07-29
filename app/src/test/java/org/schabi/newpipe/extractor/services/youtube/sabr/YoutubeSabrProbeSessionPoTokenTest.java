package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YoutubeSabrProbeSessionPoTokenTest {
    private static final Localization LOCALIZATION = new Localization("en", "US");
    private static final ContentCountry CONTENT_COUNTRY = new ContentCountry("US");

    @AfterEach
    void tearDown() {
        NewPipe.setYoutubeSessionPoTokenProvider(null);
    }

    @Test
    void rejectsExplicitPoTokenWithoutVisitorData() {
        final AtomicInteger providerCalls = installAutomaticPair();

        assertThrows(IllegalArgumentException.class,
                () -> YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        LOCALIZATION, CONTENT_COUNTRY, "explicit-token", null));

        assertEquals(0, providerCalls.get());
    }

    @Test
    void rejectsExplicitVisitorDataWithoutPoToken() {
        final AtomicInteger providerCalls = installAutomaticPair();

        assertThrows(IllegalArgumentException.class,
                () -> YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        LOCALIZATION, CONTENT_COUNTRY, null, "explicit-visitor"));

        assertEquals(0, providerCalls.get());
    }

    @Test
    void missingExplicitPairUsesCompleteAutomaticPair() {
        final AtomicInteger providerCalls = installAutomaticPair();

        final YoutubeSabrProbe.PlayerIdentityPair resolved =
                YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        LOCALIZATION, CONTENT_COUNTRY, null, null);

        assertEquals("automatic-token", resolved.playerPoToken);
        assertEquals("automatic-visitor", resolved.visitorData);
        assertEquals(1, providerCalls.get());
    }

    @Test
    void completeExplicitPairIsUsedWithoutProvider() {
        final AtomicInteger providerCalls = installAutomaticPair();

        final YoutubeSabrProbe.PlayerIdentityPair resolved =
                YoutubeSabrProbe.resolvePlayerIdentity(YoutubeSabrClientProfile.MWEB,
                        LOCALIZATION, CONTENT_COUNTRY, "explicit-token", "explicit-visitor");

        assertEquals("explicit-token", resolved.playerPoToken);
        assertEquals("explicit-visitor", resolved.visitorData);
        assertEquals(0, providerCalls.get());
    }

    private static AtomicInteger installAutomaticPair() {
        final AtomicInteger calls = new AtomicInteger();
        NewPipe.setYoutubeSessionPoTokenProvider((clientName, localization, contentCountry,
                                                  loggedIn) -> {
            calls.incrementAndGet();
            return new YoutubeSessionPoToken("automatic-visitor", "automatic-token");
        });
        return calls;
    }
}
