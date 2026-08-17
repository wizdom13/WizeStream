import { describe, expect, it } from 'vitest';
import {
  defaultPlaybackParameters, formatPlaybackPitch, formatPlaybackSpeed, pitchFromSemitones,
  playbackParameterFromSlider, playbackParameterToSlider, playbackWithParameter,
  playbackWithStep, playbackWithUnhook, semitonesFromPitch,
} from './playback-parameters';

describe('Android-compatible playback parameters', () => {
  it('uses the Android quadratic slider around normal speed', () => {
    expect(playbackParameterFromSlider(0)).toBe(0.1);
    expect(playbackParameterFromSlider(5_000)).toBe(1);
    expect(playbackParameterFromSlider(10_000)).toBe(3);
    expect(playbackParameterToSlider(1)).toBe(5_000);
  });

  it('hooks tempo and pitch unless they are unhooked', () => {
    const hooked = playbackWithUnhook({ ...defaultPlaybackParameters, speed: 2, pitch: 0.8 }, false);
    expect(hooked).toMatchObject({ speed: 0.8, pitch: 0.8, unhook: false });
    expect(playbackWithParameter(hooked, 'speed', 1.5)).toMatchObject({ speed: 1.5, pitch: 1.5 });
    expect(playbackWithParameter({ ...hooked, unhook: true }, 'speed', 2)).toMatchObject({ speed: 2, pitch: 0.8 });
  });

  it('applies the selected step and clamps the supported range', () => {
    const settings = { ...defaultPlaybackParameters, speed: 2.9, adjustmentStep: 0.25 as const };
    expect(playbackWithStep(settings, 'speed', 1).speed).toBe(3);
    expect(playbackWithParameter(settings, 'pitch', 0).pitch).toBe(0.1);
  });

  it('supports the Android semitone pitch mode', () => {
    expect(pitchFromSemitones(12)).toBe(2);
    expect(semitonesFromPitch(0.5)).toBe(-12);
    expect(formatPlaybackSpeed(1.25)).toBe('1.25×');
    expect(formatPlaybackPitch(1.25)).toBe('125%');
  });
});
