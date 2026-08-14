import { describe, expect, it } from 'vitest';
import { subscriberCountLabel } from './subscriber-count';

describe('subscriber count labels', () => {
  it('uses a compact channel-card label', () => {
    expect(subscriberCountLabel(144_000)).toBe('144k subscribers');
    expect(subscriberCountLabel(1_250_000)).toBe('1.3m subscribers');
  });

  it('uses the singular label for one subscriber', () => {
    expect(subscriberCountLabel(1)).toBe('1 subscriber');
  });

  it('omits unavailable counts', () => {
    expect(subscriberCountLabel(null)).toBeUndefined();
    expect(subscriberCountLabel(-1)).toBeUndefined();
  });
});
