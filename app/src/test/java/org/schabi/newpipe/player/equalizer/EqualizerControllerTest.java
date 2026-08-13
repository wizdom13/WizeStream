package org.schabi.newpipe.player.equalizer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

public class EqualizerControllerTest {
    @Test
    public void enablingCreatesOneSessionEngineAndPersistsState() {
        final FakeStore store = new FakeStore(EqualizerState.flat());
        final FakeFactory factory = new FakeFactory(true);
        final EqualizerController controller = new EqualizerController(store, factory);
        controller.attachAudioSession(41);

        assertEquals(0, factory.createCount);

        final EqualizerState enabled =
                EqualizerState.flat().withEnabled(true).withPreset(EqualizerPreset.ROCK);
        controller.updateState(enabled);

        assertEquals(1, factory.createCount);
        assertEquals(41, factory.lastSessionId);
        assertTrue(factory.lastEngine.enabled);
        assertArrayEquals(enabled.getGains(), factory.lastEngine.gains);
        assertSame(enabled, store.saved);
        assertTrue(controller.isOperational());
        assertEquals(enabled.getHeadroomMultiplier(),
                controller.getHeadroomMultiplier(), 0.0f);
    }

    @Test
    public void audioSessionChangeReleasesOldEngineAndReappliesCurve() {
        final FakeStore store = new FakeStore(
                EqualizerState.flat().withEnabled(true).withPreset(EqualizerPreset.VOCAL));
        final FakeFactory factory = new FakeFactory(true);
        final EqualizerController controller = new EqualizerController(store, factory);

        controller.attachAudioSession(10);
        final FakeEngine first = factory.lastEngine;
        controller.attachAudioSession(11);

        assertTrue(first.released);
        assertFalse(first.enabled);
        assertEquals(2, factory.createCount);
        assertEquals(11, factory.lastSessionId);
        assertArrayEquals(store.initial.getGains(), factory.lastEngine.gains);
    }

    @Test
    public void previewIsLiveButOnlyCommitPersists() {
        final FakeStore store = new FakeStore(EqualizerState.flat());
        final FakeFactory factory = new FakeFactory(true);
        final EqualizerController controller = new EqualizerController(store, factory);
        controller.attachAudioSession(7);

        final EqualizerState preview =
                EqualizerState.flat().withEnabled(true).withBandGain(0, 8);
        controller.previewState(preview);

        assertEquals(0, store.saveCount);
        assertArrayEquals(preview.getGains(), factory.lastEngine.gains);

        controller.updateState(preview);
        assertEquals(1, store.saveCount);
        assertSame(preview, store.saved);
    }

    @Test
    public void unavailableBackendNeverAppliesHeadroom() {
        final FakeStore store = new FakeStore(
                EqualizerState.flat().withEnabled(true).withPreset(EqualizerPreset.ROCK));
        final FakeFactory factory = new FakeFactory(false);
        final EqualizerController controller = new EqualizerController(store, factory);

        controller.attachAudioSession(9);

        assertFalse(controller.isOperational());
        assertEquals(0, factory.createCount);
        assertEquals(1.0f, controller.getHeadroomMultiplier(), 0.0f);
    }

    private static final class FakeStore implements EqualizerStateStore {
        @NonNull
        private final EqualizerState initial;
        private EqualizerState saved;
        private int saveCount;

        FakeStore(@NonNull final EqualizerState initial) {
            this.initial = initial;
        }

        @NonNull
        @Override
        public EqualizerState load() {
            return initial;
        }

        @Override
        public void save(@NonNull final EqualizerState state) {
            saved = state;
            saveCount++;
        }
    }

    private static final class FakeFactory implements EqualizerEngineFactory {
        private final boolean available;
        private int createCount;
        private int lastSessionId;
        private FakeEngine lastEngine;

        FakeFactory(final boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @NonNull
        @Override
        public EqualizerEngine create(final int audioSessionId) {
            createCount++;
            lastSessionId = audioSessionId;
            lastEngine = new FakeEngine();
            return lastEngine;
        }
    }

    private static final class FakeEngine implements EqualizerEngine {
        private int[] gains;
        private boolean enabled;
        private boolean released;

        @Override
        public void apply(@NonNull final int[] canonicalGainSteps) {
            gains = java.util.Arrays.copyOf(
                    canonicalGainSteps, canonicalGainSteps.length);
        }

        @Override
        public void setEnabled(final boolean newEnabled) {
            enabled = newEnabled;
        }

        @Override
        public void release() {
            released = true;
        }
    }
}
