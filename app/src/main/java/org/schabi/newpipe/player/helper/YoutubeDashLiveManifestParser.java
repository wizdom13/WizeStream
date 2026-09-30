package org.schabi.newpipe.player.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.common.C;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Location;
import androidx.media3.exoplayer.dash.manifest.Period;
import androidx.media3.exoplayer.dash.manifest.ProgramInformation;
import androidx.media3.exoplayer.dash.manifest.ServiceDescriptionElement;
import androidx.media3.exoplayer.dash.manifest.UtcTimingElement;

import java.util.List;

/**
 * A {@link DashManifestParser} fixing YouTube DASH manifests to allow starting playback from the
 * newest period available instead of the earliest one in some cases.
 *
 * <p>
 * It changes the {@code availabilityStartTime} passed to a custom value doing the workaround.
 * A better approach to fix the issue should be investigated and used in the future.
 * </p>
 */
public class YoutubeDashLiveManifestParser extends DashManifestParser {

    // Result of Util.parseXsDateTime("1970-01-01T00:00:00Z")
    private static final long AVAILABILITY_START_TIME_TO_USE = 0;

    // There is no computation made with the availabilityStartTime value in the
    // parseMediaPresentationDescription method itself, so we can just override methods called in
    // this method using the workaround value
    // Overriding parsePeriod does not seem to be needed

    @SuppressWarnings("checkstyle:ParameterNumber")
    @NonNull
    @Override
    protected DashManifest buildMediaPresentationDescription(
            final long availabilityStartTime,
            final long durationMs,
            final long minBufferTimeMs,
            final boolean dynamic,
            final long minUpdateTimeMs,
            final long timeShiftBufferDepthMs,
            final long suggestedPresentationDelayMs,
            final long publishTimeMs,
            @Nullable final ProgramInformation programInformation,
            @Nullable final UtcTimingElement utcTiming,
            @Nullable final ServiceDescriptionElement serviceDescription,
            @NonNull final List<Period> periods,
            @NonNull final List<Location> locations) {
        return super.buildMediaPresentationDescription(
                AVAILABILITY_START_TIME_TO_USE,
                // A dynamic YouTube MPD may describe the current DVR snapshot with a finite
                // duration. Treating it as the end of the broadcast stops playback at that edge
                // even though later manifest refreshes contain more segments. Static archives
                // keep their declared duration.
                dynamic ? C.TIME_UNSET : durationMs,
                minBufferTimeMs,
                dynamic,
                minUpdateTimeMs,
                timeShiftBufferDepthMs,
                suggestedPresentationDelayMs,
                publishTimeMs,
                programInformation,
                utcTiming,
                serviceDescription,
                periods,
                locations);
    }
}
