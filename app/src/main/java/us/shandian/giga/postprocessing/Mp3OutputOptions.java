package us.shandian.giga.postprocessing;

import androidx.annotation.NonNull;

import java.util.Set;

public final class Mp3OutputOptions {
    public static final int DEFAULT_BITRATE_KBPS = 192;
    public static final Set<Integer> SUPPORTED_BITRATES = Set.of(128, 192, 256, 320);

    private Mp3OutputOptions() {
    }

    public static int parseBitrate(@NonNull final String value) {
        try {
            final int bitrate = Integer.parseInt(value);
            return SUPPORTED_BITRATES.contains(bitrate) ? bitrate : DEFAULT_BITRATE_KBPS;
        } catch (final NumberFormatException ignored) {
            return DEFAULT_BITRATE_KBPS;
        }
    }

    public static long estimateOutputBytes(final long durationSeconds, final int bitrateKbps) {
        if (durationSeconds <= 0 || !SUPPORTED_BITRATES.contains(bitrateKbps)) {
            return 0;
        }
        return durationSeconds * bitrateKbps * 1_000L / 8L;
    }

    public static long estimateRequiredBytes(final long sourceBytes,
                                             final long durationSeconds,
                                             final int bitrateKbps) {
        final long outputBytes = estimateOutputBytes(durationSeconds, bitrateKbps);
        if (sourceBytes <= 0 || outputBytes <= 0) {
            return Math.max(sourceBytes, outputBytes);
        }
        try {
            return Math.addExact(sourceBytes, outputBytes);
        } catch (final ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
