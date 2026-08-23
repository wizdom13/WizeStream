package us.shandian.giga.postprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostprocessingMp3RestoreTest {
    @Test
    void restoresMp3PostprocessorByItsPersistedAlgorithmName() {
        final Postprocessing restored = Postprocessing.getAlgorithm(
                Postprocessing.ALGORITHM_MP3_FROM_AUDIO, new String[] {"256"}, null);

        assertTrue(restored.isMp3Conversion());
    }
}
