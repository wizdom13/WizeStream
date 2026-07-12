package org.schabi.newpipe.settings.sponsorblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SponsorBlockPlaybackDecisionTest {
    private static final TestSegmentProvider SEGMENT_PROVIDER = new TestSegmentProvider();

    @Test
    public void skipSegmentIsPlaybackActionable() {
        assertTrue(isActionable(SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP,
                SponsorBlockBehavior.SKIP, true));
    }

    @Test
    public void dontSkipSegmentIsNotPlaybackActionable() {
        assertFalse(isActionable(SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP,
                SponsorBlockBehavior.DONT_SKIP, true));
    }

    @Test
    public void manualSegmentIsPlaybackActionable() {
        assertTrue(isActionable(SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP,
                SponsorBlockBehavior.MANUAL, true));
    }

    @Test
    public void disabledSegmentIsNotPlaybackActionable() {
        assertFalse(isActionable(SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP,
                SponsorBlockBehavior.SKIP, false));
    }

    @Test
    public void highlightPoiIsNeverPlaybackActionable() {
        assertFalse(isActionable(SponsorBlockCategory.HIGHLIGHT, SponsorBlockAction.POI,
                SponsorBlockBehavior.SKIP, true));
    }

    @Test
    public void dontSkipThenSkipReturnsSkip() {
        final TestSegment dontSkip = segment(SponsorBlockCategory.INTRO);
        final TestSegment skip = segment(SponsorBlockCategory.SPONSOR);

        assertSame(skip, find(Arrays.asList(dontSkip, skip), category ->
                category == SponsorBlockCategory.INTRO
                        ? SponsorBlockBehavior.DONT_SKIP
                        : SponsorBlockBehavior.SKIP));
    }

    @Test
    public void dontSkipThenManualReturnsManual() {
        final TestSegment dontSkip = segment(SponsorBlockCategory.INTRO);
        final TestSegment manual = segment(SponsorBlockCategory.SPONSOR);

        assertSame(manual, find(Arrays.asList(dontSkip, manual), category ->
                category == SponsorBlockCategory.INTRO
                        ? SponsorBlockBehavior.DONT_SKIP
                        : SponsorBlockBehavior.MANUAL));
    }

    @Test
    public void disabledThenSkipReturnsSkip() {
        final TestSegment disabled = segment(SponsorBlockCategory.INTRO);
        final TestSegment skip = segment(SponsorBlockCategory.SPONSOR);

        assertSame(skip, find(Arrays.asList(disabled, skip), SponsorBlockBehavior.SKIP,
                category -> category != SponsorBlockCategory.INTRO));
    }

    @Test
    public void highlightPoiThenSkipReturnsSkip() {
        final TestSegment highlight = new TestSegment(SponsorBlockCategory.HIGHLIGHT,
                SponsorBlockAction.POI, 1_000, 2_000);
        final TestSegment skip = segment(SponsorBlockCategory.SPONSOR);

        assertSame(skip, find(Arrays.asList(highlight, skip), SponsorBlockBehavior.SKIP));
    }

    @Test
    public void invalidThenManualReturnsManual() {
        final TestSegment invalid = new TestSegment(SponsorBlockCategory.INTRO,
                SponsorBlockAction.SKIP, 2_000, 1_000);
        final TestSegment manual = segment(SponsorBlockCategory.SPONSOR);

        assertSame(manual, find(Arrays.asList(invalid, manual), SponsorBlockBehavior.MANUAL));
    }

    @Test
    public void onlyDontSkipReturnsNull() {
        assertNull(find(Arrays.asList(segment(SponsorBlockCategory.INTRO),
                segment(SponsorBlockCategory.SPONSOR)), SponsorBlockBehavior.DONT_SKIP));
    }

    @Test
    public void multipleActionableSegmentsPreserveListOrder() {
        final TestSegment first = segment(SponsorBlockCategory.INTRO);
        final TestSegment second = segment(SponsorBlockCategory.SPONSOR);

        assertSame(first, find(Arrays.asList(first, second), SponsorBlockBehavior.SKIP));
    }

    @Test
    public void graceRewindTargetUsesSameActionableSelection() {
        final TestSegment ignoredDontSkip = segment(SponsorBlockCategory.INTRO);
        final TestSegment rewindTarget = segment(SponsorBlockCategory.SPONSOR);

        assertSame(rewindTarget, find(Arrays.asList(ignoredDontSkip, rewindTarget), category ->
                category == SponsorBlockCategory.INTRO
                        ? SponsorBlockBehavior.DONT_SKIP
                        : SponsorBlockBehavior.SKIP));
    }


    @Test
    public void skippedSkipThenSkipReturnsSecondSkip() {
        final TestSegment skipped = segment(SponsorBlockCategory.INTRO);
        final TestSegment runnable = segment(SponsorBlockCategory.SPONSOR);

        assertSame(runnable, findRunnable(Arrays.asList(skipped, runnable),
                SponsorBlockBehavior.SKIP, skipped, null));
    }

    @Test
    public void skippedSkipThenManualReturnsManual() {
        final TestSegment skipped = segment(SponsorBlockCategory.INTRO);
        final TestSegment manual = segment(SponsorBlockCategory.SPONSOR);

        assertSame(manual, findRunnable(Arrays.asList(skipped, manual), category ->
                category == SponsorBlockCategory.SPONSOR
                        ? SponsorBlockBehavior.MANUAL
                        : SponsorBlockBehavior.SKIP, skipped, null));
    }

    @Test
    public void ignoredSkipThenManualReturnsManual() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);
        final TestSegment manual = segment(SponsorBlockCategory.SPONSOR);

        assertSame(manual, findRunnable(Arrays.asList(ignored, manual), category ->
                category == SponsorBlockCategory.SPONSOR
                        ? SponsorBlockBehavior.MANUAL
                        : SponsorBlockBehavior.SKIP, null, ignored));
    }

    @Test
    public void ignoredSkipThenSkipReturnsSecondSkip() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);
        final TestSegment runnable = segment(SponsorBlockCategory.SPONSOR);

        assertSame(runnable, findRunnable(Arrays.asList(ignored, runnable),
                SponsorBlockBehavior.SKIP, null, ignored));
    }

    @Test
    public void ignoredManualRemainsEligible() {
        final TestSegment manual = segment(SponsorBlockCategory.SPONSOR);

        assertSame(manual, findRunnable(Arrays.asList(manual),
                SponsorBlockBehavior.MANUAL, null, manual));
    }

    @Test
    public void allRuntimeSuppressedReturnsNull() {
        final TestSegment skipped = segment(SponsorBlockCategory.INTRO);
        final TestSegment ignored = segment(SponsorBlockCategory.SPONSOR);

        assertNull(findRunnable(Arrays.asList(skipped, ignored),
                SponsorBlockBehavior.SKIP, skipped, ignored));
    }

    @Test
    public void runtimeSelectionPreservesListOrder() {
        final TestSegment first = segment(SponsorBlockCategory.INTRO);
        final TestSegment second = segment(SponsorBlockCategory.SPONSOR);

        assertSame(first, findRunnable(Arrays.asList(first, second),
                SponsorBlockBehavior.SKIP, null, null));
    }


    @Test
    public void staticSelectionReturnsSkippedSegment() {
        final TestSegment skipped = segment(SponsorBlockCategory.SPONSOR);

        assertSame(skipped, find(Arrays.asList(skipped), SponsorBlockBehavior.SKIP));
    }

    @Test
    public void runtimeSelectionExcludesSkippedSegment() {
        final TestSegment skipped = segment(SponsorBlockCategory.SPONSOR);

        assertNull(findRunnable(Arrays.asList(skipped), SponsorBlockBehavior.SKIP, skipped, null));
    }

    @Test
    public void staticSelectionReturnsIgnoredSkipSegment() {
        final TestSegment ignored = segment(SponsorBlockCategory.SPONSOR);

        assertSame(ignored, find(Arrays.asList(ignored), SponsorBlockBehavior.SKIP));
    }

    @Test
    public void runtimeSelectionExcludesIgnoredSkipSegment() {
        final TestSegment ignored = segment(SponsorBlockCategory.SPONSOR);

        assertNull(findRunnable(Arrays.asList(ignored), SponsorBlockBehavior.SKIP, null, ignored));
    }

    @Test
    public void ignoredSkipRemainsIgnoredWhilePositionIsInsideSegment() {
        final TestSegment ignored = segment(SponsorBlockCategory.SPONSOR);

        assertTrue(keepIgnored(key(ignored), Arrays.asList(ignored), 1_500,
                SponsorBlockBehavior.SKIP));
    }

    @Test
    public void ignoredSkipClearsAfterPositionPassesSegmentEnd() {
        final TestSegment ignored = segment(SponsorBlockCategory.SPONSOR);

        assertFalse(keepIgnored(key(ignored), Arrays.asList(ignored), 2_000,
                SponsorBlockBehavior.SKIP));
    }

    @Test
    public void graceRewindDisabledCanRearmPreviouslySkippedSegment() {
        final TestSegment skipped = segment(SponsorBlockCategory.SPONSOR);

        assertSame(skipped, find(Arrays.asList(skipped), SponsorBlockBehavior.SKIP));
        assertNull(findRunnable(Arrays.asList(skipped), SponsorBlockBehavior.SKIP, skipped, null));
        assertSame(skipped, findRunnable(Arrays.asList(skipped), SponsorBlockBehavior.SKIP,
                null, null));
    }


    @Test
    public void graceEnabledAssignsSeekTargetAsIgnored() {
        final Set<String> skipped = new HashSet<>();

        assertSame("target", SponsorBlockPlaybackDecision.resolveIgnoredSegmentAfterManualSeek(
                "target", true, skipped));
        assertTrue(skipped.isEmpty());
    }

    @Test
    public void graceDisabledClearsExistingIgnoredKey() {
        assertNull(SponsorBlockPlaybackDecision.resolveIgnoredSegmentAfterManualSeek(
                null, false, new HashSet<>()));
    }

    @Test
    public void graceDisabledRemovesActionableTargetFromSkippedSet() {
        final Set<String> skipped = new HashSet<>();
        skipped.add("target");

        assertNull(SponsorBlockPlaybackDecision.resolveIgnoredSegmentAfterManualSeek(
                "target", false, skipped));
        assertFalse(skipped.contains("target"));
    }

    @Test
    public void graceDisabledWithoutSeekTargetStillClearsIgnoredKey() {
        final Set<String> skipped = new HashSet<>();
        skipped.add("other");

        assertNull(SponsorBlockPlaybackDecision.resolveIgnoredSegmentAfterManualSeek(
                null, false, skipped));
        assertTrue(skipped.contains("other"));
    }



    @Test
    public void ignoredSegmentRemainsActiveWhileInsideExactSegment() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);

        assertEquals(key(ignored), resolveIgnored(key(ignored), Arrays.asList(ignored), 1_500,
                SponsorBlockBehavior.SKIP));
    }

    @Test
    public void ignoredSegmentClearsAfterEndWithNoOtherActiveSegment() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(ignored), 2_000,
                SponsorBlockBehavior.SKIP));
    }

    @Test
    public void ignoredSegmentClearsAfterEndWhileManualOverlapRemainsActive() {
        final TestSegment ignored = new TestSegment(SponsorBlockCategory.INTRO,
                SponsorBlockAction.SKIP, 1_000, 2_000);
        final TestSegment manual = new TestSegment(SponsorBlockCategory.SPONSOR,
                SponsorBlockAction.SKIP, 1_500, 2_500);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(ignored, manual), 2_100,
                category -> category == SponsorBlockCategory.SPONSOR
                        ? SponsorBlockBehavior.MANUAL
                        : SponsorBlockBehavior.SKIP));
    }

    @Test
    public void ignoredSegmentClearsAfterEndWhileSkipOverlapRemainsActive() {
        final TestSegment ignored = new TestSegment(SponsorBlockCategory.INTRO,
                SponsorBlockAction.SKIP, 1_000, 2_000);
        final TestSegment skip = new TestSegment(SponsorBlockCategory.SPONSOR,
                SponsorBlockAction.SKIP, 1_500, 2_500);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(ignored, skip), 2_100,
                SponsorBlockBehavior.SKIP));
    }

    @Test
    public void runtimeSelectionReturnsOverlapAfterStaleIgnoredKeyClears() {
        final TestSegment ignored = new TestSegment(SponsorBlockCategory.INTRO,
                SponsorBlockAction.SKIP, 1_000, 2_000);
        final TestSegment runnable = new TestSegment(SponsorBlockCategory.SPONSOR,
                SponsorBlockAction.SKIP, 1_500, 2_500);
        final String updatedIgnored = resolveIgnored(key(ignored),
                Arrays.asList(ignored, runnable), 2_100, SponsorBlockBehavior.SKIP);

        assertNull(updatedIgnored);
        assertSame(runnable, findRunnable(Arrays.asList(ignored, runnable), 2_100,
                SponsorBlockBehavior.SKIP, null, null));
    }

    @Test
    public void ignoredSegmentClearsWhenCategoryBecomesDisabled() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(ignored), 1_500,
                SponsorBlockBehavior.SKIP, category -> false));
    }

    @Test
    public void ignoredSegmentClearsWhenBehaviorChangesToDontSkip() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(ignored), 1_500,
                SponsorBlockBehavior.DONT_SKIP));
    }

    @Test
    public void ignoredSegmentClearsWhenSegmentIsNoLongerPresent() {
        final TestSegment ignored = segment(SponsorBlockCategory.INTRO);
        final TestSegment other = segment(SponsorBlockCategory.SPONSOR);

        assertNull(resolveIgnored(key(ignored), Arrays.asList(other), 1_500,
                SponsorBlockBehavior.SKIP));
    }

    private boolean isActionable(final SponsorBlockCategory category,
                                 final SponsorBlockAction action,
                                 final SponsorBlockBehavior behavior,
                                 final boolean enabled) {
        return SponsorBlockPlaybackDecision.isPlaybackActionable(1_000, 2_000, category, action,
                1_500, new TestCategoryStateProvider(behavior, ignored -> enabled));
    }


    @Nullable
    private String resolveIgnored(@Nullable final String ignoredSegmentKey,
                                  @NonNull final List<TestSegment> segments,
                                  final long currentPositionMillis,
                                  @NonNull final SponsorBlockBehavior behavior) {
        return resolveIgnored(ignoredSegmentKey, segments, currentPositionMillis,
                category -> behavior, category -> true);
    }

    @Nullable
    private String resolveIgnored(@Nullable final String ignoredSegmentKey,
                                  @NonNull final List<TestSegment> segments,
                                  final long currentPositionMillis,
                                  @NonNull final SponsorBlockBehavior behavior,
                                  @NonNull final EnabledResolver enabledResolver) {
        return resolveIgnored(ignoredSegmentKey, segments, currentPositionMillis,
                category -> behavior, enabledResolver);
    }

    @Nullable
    private String resolveIgnored(@Nullable final String ignoredSegmentKey,
                                  @NonNull final List<TestSegment> segments,
                                  final long currentPositionMillis,
                                  @NonNull final BehaviorResolver behaviorResolver) {
        return resolveIgnored(ignoredSegmentKey, segments, currentPositionMillis,
                behaviorResolver, category -> true);
    }

    @Nullable
    private String resolveIgnored(@Nullable final String ignoredSegmentKey,
                                  @NonNull final List<TestSegment> segments,
                                  final long currentPositionMillis,
                                  @NonNull final BehaviorResolver behaviorResolver,
                                  @NonNull final EnabledResolver enabledResolver) {
        return SponsorBlockPlaybackDecision.resolveIgnoredSegmentForProgress(
                ignoredSegmentKey,
                segments,
                currentPositionMillis,
                SEGMENT_PROVIDER,
                this::key,
                new TestCategoryStateProvider(behaviorResolver, enabledResolver));
    }

    private boolean keepIgnored(@Nullable final String ignoredSegmentKey,
                                @NonNull final List<TestSegment> segments,
                                final long currentPositionMillis,
                                @NonNull final SponsorBlockBehavior behavior) {
        return SponsorBlockPlaybackDecision.shouldKeepIgnoredSkipSegment(
                ignoredSegmentKey,
                segments,
                currentPositionMillis,
                SEGMENT_PROVIDER,
                this::key,
                new TestCategoryStateProvider(behavior, category -> true));
    }

    @NonNull
    private String key(@NonNull final TestSegment segment) {
        return segment.category + ":" + segment.action + ":" + segment.startMillis
                + ":" + segment.endMillis;
    }

    @Nullable
    private TestSegment findRunnable(@NonNull final List<TestSegment> segments,
                                     @NonNull final SponsorBlockBehavior behavior,
                                     @Nullable final TestSegment skipped,
                                     @Nullable final TestSegment ignored) {
        return findRunnable(segments, category -> behavior, skipped, ignored);
    }

    @Nullable
    private TestSegment findRunnable(@NonNull final List<TestSegment> segments,
                                     @NonNull final BehaviorResolver behaviorResolver,
                                     @Nullable final TestSegment skipped,
                                     @Nullable final TestSegment ignored) {
        return SponsorBlockPlaybackDecision.findFirstRunnableSegment(segments, 1_500,
                SEGMENT_PROVIDER, new TestCategoryStateProvider(behaviorResolver,
                        category -> true), (segment, behavior) -> segment != skipped
                        && (behavior != SponsorBlockBehavior.SKIP || segment != ignored));
    }

    @Nullable
    private TestSegment findRunnable(@NonNull final List<TestSegment> segments,
                                     final long currentPositionMillis,
                                     @NonNull final SponsorBlockBehavior behavior,
                                     @Nullable final TestSegment skipped,
                                     @Nullable final TestSegment ignored) {
        return SponsorBlockPlaybackDecision.findFirstRunnableSegment(segments,
                currentPositionMillis, SEGMENT_PROVIDER,
                new TestCategoryStateProvider(behavior, category -> true),
                (segment, currentBehavior) -> segment != skipped
                        && (currentBehavior != SponsorBlockBehavior.SKIP || segment != ignored));
    }

    @Nullable
    private TestSegment find(@NonNull final List<TestSegment> segments,
                             @NonNull final SponsorBlockBehavior behavior) {
        return find(segments, behavior, category -> true);
    }

    @Nullable
    private TestSegment find(@NonNull final List<TestSegment> segments,
                             @NonNull final BehaviorResolver behaviorResolver) {
        return SponsorBlockPlaybackDecision.findFirstActionableSegment(segments, 1_500,
                SEGMENT_PROVIDER, new TestCategoryStateProvider(behaviorResolver,
                        category -> true));
    }

    @Nullable
    private TestSegment find(@NonNull final List<TestSegment> segments,
                             @NonNull final SponsorBlockBehavior behavior,
                             @NonNull final EnabledResolver enabledResolver) {
        return SponsorBlockPlaybackDecision.findFirstActionableSegment(segments, 1_500,
                SEGMENT_PROVIDER, new TestCategoryStateProvider(behavior, enabledResolver));
    }

    private TestSegment segment(@NonNull final SponsorBlockCategory category) {
        return new TestSegment(category, SponsorBlockAction.SKIP, 1_000, 2_000);
    }

    private interface BehaviorResolver {
        @NonNull
        SponsorBlockBehavior resolve(@NonNull SponsorBlockCategory category);
    }

    private interface EnabledResolver {
        boolean isEnabled(@NonNull SponsorBlockCategory category);
    }

    private static final class TestCategoryStateProvider
            implements SponsorBlockPlaybackDecision.CategoryStateProvider {
        private final BehaviorResolver behaviorResolver;
        private final EnabledResolver enabledResolver;

        private TestCategoryStateProvider(@NonNull final SponsorBlockBehavior behavior,
                                          @NonNull final EnabledResolver enabledResolver) {
            this(category -> behavior, enabledResolver);
        }

        private TestCategoryStateProvider(@NonNull final BehaviorResolver behaviorResolver,
                                          @NonNull final EnabledResolver enabledResolver) {
            this.behaviorResolver = behaviorResolver;
            this.enabledResolver = enabledResolver;
        }

        @Override
        public boolean isEnabled(@NonNull final SponsorBlockCategory category) {
            return enabledResolver.isEnabled(category);
        }

        @NonNull
        @Override
        public SponsorBlockBehavior getBehavior(@NonNull final SponsorBlockCategory category) {
            return behaviorResolver.resolve(category);
        }
    }

    private static final class TestSegmentProvider
            implements SponsorBlockPlaybackDecision.SegmentProvider<TestSegment> {
        @Override
        public long getStartMillis(@NonNull final TestSegment segment) {
            return segment.startMillis;
        }

        @Override
        public long getEndMillis(@NonNull final TestSegment segment) {
            return segment.endMillis;
        }

        @Nullable
        @Override
        public SponsorBlockCategory getCategory(@NonNull final TestSegment segment) {
            return segment.category;
        }

        @Nullable
        @Override
        public SponsorBlockAction getAction(@NonNull final TestSegment segment) {
            return segment.action;
        }
    }

    private static final class TestSegment {
        private final SponsorBlockCategory category;
        private final SponsorBlockAction action;
        private final long startMillis;
        private final long endMillis;

        private TestSegment(final SponsorBlockCategory category,
                            final SponsorBlockAction action,
                            final long startMillis,
                            final long endMillis) {
            this.category = category;
            this.action = action;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }
}
