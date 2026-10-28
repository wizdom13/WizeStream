package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class YoutubeClientVersionFallbackTest {
    @Test
    public void doesNotCallFallbackWhenPrimaryExtractionSucceeds() {
        final AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        assertTrue(YoutubeParsingHelper.tryClientVersionExtractors(
                () -> {
                },
                () -> fallbackCalled.set(true)));
        assertFalse(fallbackCalled.get());
    }

    @Test
    public void callsFallbackWhenPrimaryExtractionFails() {
        final AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        assertTrue(YoutubeParsingHelper.tryClientVersionExtractors(
                () -> {
                    throw new IllegalStateException("service worker changed");
                },
                () -> fallbackCalled.set(true)));
        assertTrue(fallbackCalled.get());
    }

    @Test
    public void allowsHardcodedFallbackWhenBothNetworkExtractionsFail() {
        assertFalse(YoutubeParsingHelper.tryClientVersionExtractors(
                () -> {
                    throw new IllegalStateException("service worker changed");
                },
                () -> {
                    throw new IllegalStateException("ytInitialData changed");
                }));
    }
}
