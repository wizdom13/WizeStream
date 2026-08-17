import { useEffect, useState } from 'react';
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel,
  MenuItem, Slider, Stack, Switch, TextField, Typography,
} from '@mui/material';
import type { EqualizerPresetId, EqualizerSettings } from '../shared/contracts';
import {
  equalizerFrequencies, equalizerHeadroomDecibels, equalizerMaximumGainStep,
  equalizerMinimumGainStep, equalizerPresetLabel, equalizerPresets, equalizerWithBandGain,
  equalizerWithPreset, formatEqualizerFrequency, formatEqualizerGain,
} from './equalizer';

export function EqualizerDialog({ open, value, appliesLive = false, onPreview, onCommit, onClose }: {
  open: boolean;
  value: EqualizerSettings;
  appliesLive?: boolean;
  onPreview?(value: EqualizerSettings): void;
  onCommit(value: EqualizerSettings): void;
  onClose(): void;
}) {
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    if (open) setDraft(value);
  }, [open, value]);

  function preview(next: EqualizerSettings) {
    setDraft(next);
    onPreview?.(next);
  }

  function finish() {
    onCommit(draft);
    onClose();
  }

  return <Dialog open={open} onClose={finish} fullWidth maxWidth="sm">
    <DialogTitle>Equalizer</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ pt: 1 }}>
        <FormControlLabel label="Enable equalizer" control={<Switch checked={draft.enabled}
          onChange={(event) => preview({ ...draft, enabled: event.target.checked })} />} />
        <Typography color="text.secondary" variant="body2">
          {!draft.enabled ? 'Equalizer is off.' : appliesLive
            ? 'Active for this playback session.' : 'Changes apply to the next playback session.'}
        </Typography>
        <TextField select label="Preset" value={draft.preset} onChange={(event) =>
          preview(equalizerWithPreset(draft, event.target.value as EqualizerPresetId))}>
          {equalizerPresets.map((preset) => <MenuItem key={preset.id} value={preset.id}>{preset.label}</MenuItem>)}
          <MenuItem value="custom">Custom</MenuItem>
        </TextField>
        <Typography color="text.secondary" variant="body2">
          The fixed 10-band curve is shared with WizeStream Android and adapted by libmpv during playback.
        </Typography>
        <Box className="equalizer-band-list">
          {equalizerFrequencies.map((frequency, band) => <Box key={frequency} className="equalizer-band-row">
            <Typography variant="body2" sx={{ fontWeight: 650 }}>{formatEqualizerFrequency(frequency)}</Typography>
            <Slider min={equalizerMinimumGainStep} max={equalizerMaximumGainStep} step={1}
              value={draft.gains[band]} disabled={!draft.enabled}
              aria-label={`${formatEqualizerFrequency(frequency)} equalizer gain`}
              onChange={(_event, value) => preview(equalizerWithBandGain(draft, band, Number(value)))} />
            <Typography className="mono" variant="body2">{formatEqualizerGain(draft.gains[band])}</Typography>
          </Box>)}
        </Box>
        <Typography color="text.secondary" variant="body2">
          Automatic clipping headroom: −{equalizerHeadroomDecibels(draft).toFixed(1)} dB · {equalizerPresetLabel(draft.preset)}
        </Typography>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={finish}>Close</Button></DialogActions>
  </Dialog>;
}
