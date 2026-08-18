import { describe, expect, it } from 'vitest';
import { previewFrameAt } from './stream-preview';

describe('previewFrameAt', () => {
  const frameset = {
    urls: ['one.jpg', 'two.jpg'], frameWidth: 160, frameHeight: 90,
    totalCount: 12, durationPerFrame: 5_000, framesPerPageX: 3, framesPerPageY: 2,
  };

  it('selects the correct storyboard page, row and column', () => {
    expect(previewFrameAt([frameset], 20)).toMatchObject({ url: 'one.jpg', left: 160, top: 90 });
    expect(previewFrameAt([frameset], 35)).toMatchObject({ url: 'two.jpg', left: 160, top: 0 });
  });

  it('returns no frame for unavailable preview metadata', () => {
    expect(previewFrameAt([], 10)).toBeUndefined();
  });
});
