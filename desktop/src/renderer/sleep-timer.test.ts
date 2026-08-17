import { describe, expect, it } from 'vitest';
import {
  sleepTimerFadeMultiplier, sleepTimerRemainingMillis, sleepTimerStatus, type SleepTimerState,
} from './sleep-timer';

describe('desktop sleep timer', () => {
  it('counts down duration timers against their deadline', () => {
    const timer: SleepTimerState = { mode: 'duration', deadline: 70_000, fadeOut: false };
    expect(sleepTimerRemainingMillis(timer, 10_000, -1)).toBe(60_000);
    expect(sleepTimerStatus(timer, 60_000)).toBe('1:00 remaining');
  });

  it('uses playback time for end-of-current mode', () => {
    const timer: SleepTimerState = { mode: 'end_current', fadeOut: false };
    expect(sleepTimerRemainingMillis(timer, 0, 91)).toBe(91_000);
    expect(sleepTimerStatus(timer, 91_000)).toBe('Ends after the current video · 1:31 remaining');
  });

  it('fades linearly during the final thirty seconds', () => {
    const timer: SleepTimerState = { mode: 'duration', deadline: 20_000, fadeOut: true };
    expect(sleepTimerFadeMultiplier(timer, 30_000)).toBe(1);
    expect(sleepTimerFadeMultiplier(timer, 15_000)).toBe(0.5);
    expect(sleepTimerFadeMultiplier(timer, 15_000, 15_000)).toBe(1);
    expect(sleepTimerFadeMultiplier(timer, 0)).toBe(0);
  });
});
