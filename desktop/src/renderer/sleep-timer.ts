export type SleepTimerMode = 'none' | 'duration' | 'end_current';

export interface SleepTimerState {
  mode: SleepTimerMode;
  deadline?: number;
  fadeOut: boolean;
}

export const inactiveSleepTimer: SleepTimerState = { mode: 'none', fadeOut: false };
export const sleepTimerFadeDurationMillis = 30_000;

export function sleepTimerRemainingMillis(timer: SleepTimerState, now: number, playbackRemainingSeconds: number) {
  if (timer.mode === 'duration') return Math.max(0, (timer.deadline ?? now) - now);
  if (timer.mode === 'end_current' && Number.isFinite(playbackRemainingSeconds) && playbackRemainingSeconds >= 0) {
    return Math.max(0, playbackRemainingSeconds * 1_000);
  }
  return -1;
}

export function sleepTimerFadeMultiplier(
  timer: SleepTimerState,
  remainingMillis: number,
  activeFadeWindowMillis = sleepTimerFadeDurationMillis,
) {
  if (timer.mode === 'none' || !timer.fadeOut || remainingMillis < 0
    || remainingMillis >= sleepTimerFadeDurationMillis) return 1;
  return Math.max(0, Math.min(1, remainingMillis / Math.max(1, activeFadeWindowMillis)));
}

export function sleepTimerStatus(timer: SleepTimerState, remainingMillis: number) {
  if (timer.mode === 'none') return 'Sleep timer';
  if (timer.mode === 'end_current' && remainingMillis < 0) return 'Ends after the current video';
  if (remainingMillis < 0) return 'Sleep timer';
  const seconds = Math.ceil(remainingMillis / 1_000);
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const remainder = seconds % 60;
  const countdown = hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
    : `${minutes}:${String(remainder).padStart(2, '0')}`;
  return timer.mode === 'end_current' ? `Ends after the current video · ${countdown} remaining` : `${countdown} remaining`;
}
