import type {
  SponsorBlockCategoryId, SponsorBlockSegment, SponsorBlockSettings,
} from '../shared/contracts';

export interface SponsorBlockCategoryDescriptor {
  id: SponsorBlockCategoryId;
  title: string;
  summary: string;
  color: string;
  markerOnly?: boolean;
}

export const sponsorBlockCategories: SponsorBlockCategoryDescriptor[] = [
  { id: 'sponsor', title: 'Sponsor', summary: 'Paid promotions and sponsor messages', color: '#00D400' },
  { id: 'intro', title: 'Intro', summary: 'Opening sequences and repeated intro sections', color: '#00FFFF' },
  { id: 'outro', title: 'Outro', summary: 'End cards and closing sections', color: '#0202ED' },
  { id: 'interaction', title: 'Interaction reminder', summary: 'Reminders to like, subscribe, or comment', color: '#CC00FF' },
  { id: 'self_promo', title: 'Self-promotion', summary: 'Creator promotion of their own content', color: '#FFFF00' },
  { id: 'non_music', title: 'Non-music section', summary: 'Non-music or off-topic parts in music videos', color: '#FF9900' },
  { id: 'preview', title: 'Preview/recap', summary: 'Previews, recaps, and teasers', color: '#008FD6' },
  { id: 'filler', title: 'Filler', summary: 'Filler tangents and repeated material', color: '#7300FF' },
  { id: 'highlight', title: 'Highlight', summary: 'Point-of-interest markers; not skipped automatically', color: '#FF1684', markerOnly: true },
];

export function validSponsorBlockSegments(
  segments: SponsorBlockSegment[],
  settings: SponsorBlockSettings,
): SponsorBlockSegment[] {
  if (!settings.enabled) return [];
  return segments.filter((segment) => Number.isFinite(segment.startTime)
    && Number.isFinite(segment.endTime)
    && segment.startTime >= 0
    && segment.endTime > segment.startTime
    && settings.categories[segment.category]?.enabled === true);
}

export function activeSponsorBlockSegment(
  segments: SponsorBlockSegment[],
  positionSeconds: number,
  settings: SponsorBlockSettings,
  skipped: ReadonlySet<string> = new Set(),
  ignoredKey?: string,
): SponsorBlockSegment | undefined {
  const positionMillis = positionSeconds * 1_000;
  return validSponsorBlockSegments(segments, settings).find((segment) => {
    const preference = settings.categories[segment.category];
    const key = sponsorBlockSegmentKey(segment);
    return segment.action === 'skip'
      && segment.category !== 'highlight'
      && preference.behavior !== 'dont_skip'
      && positionMillis >= segment.startTime
      && positionMillis < segment.endTime
      && !skipped.has(key)
      && !(preference.behavior === 'skip' && ignoredKey === key);
  });
}

export function sponsorBlockSegmentKey(segment: SponsorBlockSegment): string {
  return segment.uuid || `${segment.category}:${segment.action}:${segment.startTime}:${segment.endTime}`;
}

export function sponsorBlockCategoryTitle(category: SponsorBlockCategoryId): string {
  return sponsorBlockCategories.find((item) => item.id === category)?.title ?? 'segment';
}

export function sponsorBlockCategoryColor(category: SponsorBlockCategoryId): string {
  return sponsorBlockCategories.find((item) => item.id === category)?.color ?? '#00D400';
}
