package us.shandian.giga.postprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Mp3OutputOptionsTest {
    @Test
    void parsesSupportedBitratesAndFallsBackForInvalidValues() {
        assertEquals(128, Mp3OutputOptions.parseBitrate("128"));
        assertEquals(320, Mp3OutputOptions.parseBitrate("320"));
        assertEquals(Mp3OutputOptions.DEFAULT_BITRATE_KBPS,
                Mp3OutputOptions.parseBitrate("500"));
        assertEquals(Mp3OutputOptions.DEFAULT_BITRATE_KBPS,
                Mp3OutputOptions.parseBitrate("invalid"));
    }

    @Test
    void estimatesConversionStorageForSourceAndOutput() {
        assertEquals(14_400_000L,
                Mp3OutputOptions.estimateOutputBytes(600, 192));
        assertEquals(24_400_000L,
                Mp3OutputOptions.estimateRequiredBytes(10_000_000L, 600, 192));
    }

}
