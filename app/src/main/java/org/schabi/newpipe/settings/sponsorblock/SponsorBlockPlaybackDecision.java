package org.schabi.newpipe.settings.sponsorblock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;

import java.util.List;
import java.util.Set;

public final class SponsorBlockPlaybackDecision {
    public interface CategoryStateProvider {
        boolean isEnabled(@NonNull SponsorBlockCategory category);

        @NonNull
        SponsorBlockBehavior getBehavior(@NonNull SponsorBlockCategory category);
    }

    public interface CandidatePredicate<T> {
        boolean canRun(@NonNull T segment, @NonNull SponsorBlockBehavior behavior);
    }

    public interface SegmentProvider<T> {
        long getStartMillis(@NonNull T segment);

        long getEndMillis(@NonNull T segment);

        @Nullable
        SponsorBlockCategory getCategory(@NonNull T segment);

        @Nullable
        SponsorBlockAction getAction(@NonNull T segment);
    }

    private SponsorBlockPlaybackDecision() {
    }

    @Nullable
    public static <T> T findFirstActionableSegment(
            @NonNull final List<T> segments,
            final long currentPositionMillis,
            @NonNull final SegmentProvider<T> segmentProvider,
            @NonNull final CategoryStateProvider categoryStateProvider) {
        return findFirstRunnableSegment(segments, currentPositionMillis, segmentProvider,
                categoryStateProvider, (segment, behavior) -> true);
    }

    @Nullable
    public static <T> T findFirstRunnableSegment(
            @NonNull final List<T> segments,
            final long currentPositionMillis,
            @NonNull final SegmentProvider<T> segmentProvider,
            @NonNull final CategoryStateProvider categoryStateProvider,
            @NonNull final CandidatePredicate<T> candidatePredicate) {
        for (final T segment : segments) {
            final SponsorBlockCategory category = segmentProvider.getCategory(segment);
            final SponsorBlockBehavior behavior = category == null
                    ? SponsorBlockBehavior.DONT_SKIP
                    : categoryStateProvider.getBehavior(category);
            if (isPlaybackActionable(
                    segmentProvider.getStartMillis(segment),
                    segmentProvider.getEndMillis(segment),
                    category,
                    segmentProvider.getAction(segment),
                    currentPositionMillis,
                    categoryStateProvider)
                    && candidatePredicate.canRun(segment, behavior)) {
                return segment;
            }
        }
        return null;
    }

    public interface SegmentKeyProvider<T> {
        @NonNull
        String getSegmentKey(@NonNull T segment);
    }

    public static <T> boolean shouldKeepIgnoredSkipSegment(
            @Nullable final String ignoredSegmentKey,
            @NonNull final List<T> segments,
            final long currentPositionMillis,
            @NonNull final SegmentProvider<T> segmentProvider,
            @NonNull final SegmentKeyProvider<T> segmentKeyProvider,
            @NonNull final CategoryStateProvider categoryStateProvider) {
        if (ignoredSegmentKey == null) {
            return false;
        }

        for (final T segment : segments) {
            if (!ignoredSegmentKey.equals(segmentKeyProvider.getSegmentKey(segment))) {
                continue;
            }

            final SponsorBlockCategory category = segmentProvider.getCategory(segment);
            return isPlaybackActionable(
                    segmentProvider.getStartMillis(segment),
                    segmentProvider.getEndMillis(segment),
                    category,
                    segmentProvider.getAction(segment),
                    currentPositionMillis,
                    categoryStateProvider)
                    && categoryStateProvider.getBehavior(category) == SponsorBlockBehavior.SKIP;
        }
        return false;
    }

    @Nullable
    public static <T> String resolveIgnoredSegmentForProgress(
            @Nullable final String currentIgnoredSegmentKey,
            @NonNull final List<T> segments,
            final long currentPositionMillis,
            @NonNull final SegmentProvider<T> segmentProvider,
            @NonNull final SegmentKeyProvider<T> segmentKeyProvider,
            @NonNull final CategoryStateProvider categoryStateProvider) {
        return shouldKeepIgnoredSkipSegment(
                currentIgnoredSegmentKey,
                segments,
                currentPositionMillis,
                segmentProvider,
                segmentKeyProvider,
                categoryStateProvider) ? currentIgnoredSegmentKey : null;
    }

    @Nullable
    public static String resolveIgnoredSegmentAfterManualSeek(
            @Nullable final String seekTargetSegmentKey,
            final boolean graceRewindEnabled,
            @NonNull final Set<String> skippedSegmentKeys) {
        if (graceRewindEnabled) {
            return seekTargetSegmentKey;
        }

        if (seekTargetSegmentKey != null) {
            skippedSegmentKeys.remove(seekTargetSegmentKey);
        }
        return null;
    }

    public static boolean isPlaybackActionable(final long startMillis,
                                               final long endMillis,
                                               final SponsorBlockCategory category,
                                               final SponsorBlockAction action,
                                               final long currentPositionMillis,
                                               @NonNull final CategoryStateProvider provider) {
        return isActiveSegment(startMillis, endMillis, category, action, currentPositionMillis)
                && action == SponsorBlockAction.SKIP
                && category != SponsorBlockCategory.HIGHLIGHT
                && provider.isEnabled(category)
                && provider.getBehavior(category) != SponsorBlockBehavior.DONT_SKIP;
    }

    public static boolean isActiveSegment(final long startMillis,
                                          final long endMillis,
                                          final SponsorBlockCategory category,
                                          final SponsorBlockAction action,
                                          final long currentPositionMillis) {
        return startMillis >= 0
                && endMillis > startMillis
                && category != null
                && action != null
                && currentPositionMillis >= startMillis
                && currentPositionMillis < endMillis;
    }
}
