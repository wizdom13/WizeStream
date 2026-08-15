import { describe, expect, it } from 'vitest';
import { defaultSponsorBlockSettings, type SponsorBlockSegment } from '../shared/contracts';
import {
  activeSponsorBlockSegment, sponsorBlockSegmentKey, validSponsorBlockSegments,
} from './sponsor-block';

const sponsor: SponsorBlockSegment = {
  uuid: 'sponsor-1', startTime: 10_000, endTime: 20_000, category: 'sponsor', action: 'skip',
};

describe('Desktop SponsorBlock playback decisions', () => {
  it('is disabled by default and enables only the Android-default sponsor category', () => {
    expect(defaultSponsorBlockSettings.enabled).toBe(false);
    expect(defaultSponsorBlockSettings.categories.sponsor.enabled).toBe(true);
    expect(defaultSponsorBlockSettings.categories.intro.enabled).toBe(false);
    expect(validSponsorBlockSegments([sponsor], defaultSponsorBlockSettings)).toEqual([]);
  });

  it('selects an active automatic segment using millisecond extractor times', () => {
    const settings = { ...defaultSponsorBlockSettings, enabled: true };
    expect(activeSponsorBlockSegment([sponsor], 15, settings)).toEqual(sponsor);
    expect(activeSponsorBlockSegment([sponsor], 20, settings)).toBeUndefined();
  });

  it('honors manual, ignored, skipped, disabled and marker-only segments', () => {
    const manual = { ...defaultSponsorBlockSettings, enabled: true, categories: {
      ...defaultSponsorBlockSettings.categories,
      sponsor: { enabled: true, behavior: 'manual' as const },
    } };
    expect(activeSponsorBlockSegment([sponsor], 15, manual)).toEqual(sponsor);
    expect(activeSponsorBlockSegment([sponsor], 15, manual, new Set(['sponsor-1']))).toBeUndefined();
    expect(activeSponsorBlockSegment([sponsor], 15, {
      ...manual, categories: { ...manual.categories, sponsor: { enabled: false, behavior: 'manual' } },
    })).toBeUndefined();
    expect(activeSponsorBlockSegment([{ ...sponsor, category: 'highlight', action: 'poi' }], 15, {
      ...manual, categories: { ...manual.categories, highlight: { enabled: true, behavior: 'dont_skip' } },
    })).toBeUndefined();
    expect(activeSponsorBlockSegment([sponsor], 15, {
      ...manual, categories: { ...manual.categories, sponsor: { enabled: true, behavior: 'skip' } },
    }, new Set(), sponsorBlockSegmentKey(sponsor))).toBeUndefined();
  });
});
