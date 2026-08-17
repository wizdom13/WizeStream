import { describe, expect, it } from 'vitest';
import { defaultDesktopSettings } from '../shared/contracts';
import {
  equalizerHeadroomDecibels, equalizerHeadroomMultiplier, equalizerWithBandGain,
  equalizerWithPreset,
} from './equalizer';

describe('desktop equalizer', () => {
  it('uses the same Rock curve and half-decibel steps as Android', () => {
    const rock = equalizerWithPreset(defaultDesktopSettings.equalizer, 'rock');
    expect(rock.gains).toEqual([6, 4, 2, 0, -2, 2, 4, 6, 6, 4]);
    expect(equalizerHeadroomDecibels({ ...rock, enabled: true })).toBe(3);
  });

  it('turns a band edit into a bounded custom curve', () => {
    const edited = equalizerWithBandGain(defaultDesktopSettings.equalizer, 2, 40);
    expect(edited.preset).toBe('custom');
    expect(edited.gains[2]).toBe(24);
  });

  it('reduces effective volume to preserve clipping headroom', () => {
    const enabled = { ...equalizerWithPreset(defaultDesktopSettings.equalizer, 'bass_boost'), enabled: true };
    expect(equalizerHeadroomMultiplier(enabled)).toBeCloseTo(10 ** (-5 / 20));
    expect(equalizerHeadroomMultiplier({ ...enabled, enabled: false })).toBe(1);
  });
});
