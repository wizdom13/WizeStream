import type { PlaybackParameterSettings } from '../shared/contracts';

export const playbackParameterMinimum = 0.1;
export const playbackParameterMaximum = 3;
export const playbackParameterSliderMaximum = 10_000;
export const playbackAdjustmentSteps = [0.01, 0.05, 0.1, 0.25, 1] as const;

export const defaultPlaybackParameters: PlaybackParameterSettings = {
  speed: 1,
  pitch: 1,
  skipSilence: false,
  unhook: true,
  adjustmentStep: 0.25,
  pitchMode: 'percent',
};

function roundParameter(value: number) {
  return Math.round(Math.max(playbackParameterMinimum, Math.min(playbackParameterMaximum, value)) * 100) / 100;
}

export function playbackParameterFromSlider(progress: number) {
  const center = playbackParameterSliderMaximum / 2;
  const offset = Math.max(0, Math.min(playbackParameterSliderMaximum, progress)) - center;
  const square = (offset / center) ** 2;
  const gap = offset >= 0 ? playbackParameterMaximum - 1 : playbackParameterMinimum - 1;
  return roundParameter(1 + square * gap);
}

export function playbackParameterToSlider(value: number) {
  const center = playbackParameterSliderMaximum / 2;
  const normalized = roundParameter(value);
  const difference = normalized - 1;
  const gap = difference >= 0 ? playbackParameterMaximum - 1 : 1 - playbackParameterMinimum;
  const direction = difference >= 0 ? 1 : -1;
  return Math.round(center + direction * Math.sqrt(Math.abs(difference) / gap) * center);
}

export function playbackWithParameter(
  settings: PlaybackParameterSettings,
  parameter: 'speed' | 'pitch',
  value: number,
): PlaybackParameterSettings {
  const next = roundParameter(value);
  return settings.unhook ? { ...settings, [parameter]: next } : { ...settings, speed: next, pitch: next };
}

export function playbackWithUnhook(settings: PlaybackParameterSettings, unhook: boolean) {
  if (unhook) return { ...settings, unhook };
  const hooked = Math.min(settings.speed, settings.pitch);
  return { ...settings, unhook, speed: hooked, pitch: hooked };
}

export function playbackWithStep(
  settings: PlaybackParameterSettings,
  parameter: 'speed' | 'pitch',
  direction: -1 | 1,
) {
  return playbackWithParameter(settings, parameter, settings[parameter] + settings.adjustmentStep * direction);
}

export function semitonesFromPitch(pitch: number) {
  return Math.round(12 * Math.log2(pitch));
}

export function pitchFromSemitones(semitones: number) {
  return roundParameter(2 ** (Math.max(-12, Math.min(12, semitones)) / 12));
}

export function formatPlaybackSpeed(speed: number) {
  return `${roundParameter(speed).toFixed(2).replace(/0+$/, '').replace(/\.$/, '')}×`;
}

export function formatPlaybackPitch(pitch: number) {
  return `${Math.round(roundParameter(pitch) * 100)}%`;
}

export function formatPlaybackStep(step: number) {
  return `${Math.round(step * 100)}%`;
}
