import type { EqualizerPresetId, EqualizerSettings } from '../shared/contracts';

export const equalizerFrequencies = [32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000] as const;
export const equalizerMinimumGainStep = -24;
export const equalizerMaximumGainStep = 24;

export const equalizerPresets: ReadonlyArray<{
  id: Exclude<EqualizerPresetId, 'custom'>;
  label: string;
  gains: readonly number[];
}> = [
  { id: 'flat', label: 'Flat', gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
  { id: 'bass_boost', label: 'Bass boost', gains: [10, 8, 6, 2, 0, -2, -2, 0, 2, 4] },
  { id: 'vocal', label: 'Vocal', gains: [-4, -2, 0, 2, 4, 6, 6, 4, 2, 0] },
  { id: 'acoustic', label: 'Acoustic', gains: [4, 2, 0, -2, 2, 4, 4, 2, 2, 4] },
  { id: 'rock', label: 'Rock', gains: [6, 4, 2, 0, -2, 2, 4, 6, 6, 4] },
];

export function equalizerPresetLabel(preset: EqualizerPresetId) {
  return preset === 'custom'
    ? 'Custom'
    : equalizerPresets.find((item) => item.id === preset)?.label ?? 'Flat';
}

export function equalizerWithPreset(settings: EqualizerSettings, preset: EqualizerPresetId): EqualizerSettings {
  if (preset === 'custom') return { ...settings, preset };
  const selected = equalizerPresets.find((item) => item.id === preset) ?? equalizerPresets[0];
  return { ...settings, preset: selected.id, gains: [...selected.gains] };
}

export function equalizerWithBandGain(settings: EqualizerSettings, band: number, gainStep: number): EqualizerSettings {
  const gains = [...settings.gains];
  gains[band] = Math.max(equalizerMinimumGainStep, Math.min(equalizerMaximumGainStep, Math.round(gainStep)));
  return { ...settings, preset: 'custom', gains };
}

export function equalizerHeadroomDecibels(settings: EqualizerSettings) {
  if (!settings.enabled) return 0;
  return Math.max(0, ...settings.gains) / 2;
}

export function equalizerHeadroomMultiplier(settings: EqualizerSettings) {
  return 10 ** (-equalizerHeadroomDecibels(settings) / 20);
}

export function formatEqualizerFrequency(frequency: number) {
  return frequency >= 1_000 ? `${frequency / 1_000} kHz` : `${frequency} Hz`;
}

export function formatEqualizerGain(gainStep: number) {
  const value = gainStep / 2;
  return `${value >= 0 ? '+' : ''}${value.toFixed(1)} dB`;
}
