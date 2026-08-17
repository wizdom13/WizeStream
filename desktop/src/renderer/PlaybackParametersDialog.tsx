import { useEffect, useRef, useState } from 'react';
import {
  Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, Divider,
  FormControlLabel, Slider, Stack, ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material';
import type { PlaybackParameterSettings } from '../shared/contracts';
import {
  defaultPlaybackParameters, formatPlaybackPitch, formatPlaybackSpeed, formatPlaybackStep,
  pitchFromSemitones, playbackAdjustmentSteps, playbackParameterFromSlider,
  playbackParameterMaximum, playbackParameterMinimum, playbackParameterSliderMaximum,
  playbackParameterToSlider, playbackWithParameter, playbackWithStep, playbackWithUnhook,
  semitonesFromPitch,
} from './playback-parameters';

export function PlaybackParametersDialog({ open, value, onPreview, onCommit, onClose }: {
  open: boolean;
  value: PlaybackParameterSettings;
  onPreview(value: PlaybackParameterSettings): void;
  onCommit(value: PlaybackParameterSettings): void;
  onClose(): void;
}) {
  const [draft, setDraft] = useState(value);
  const initial = useRef(value);

  useEffect(() => {
    if (!open) return;
    initial.current = value;
    setDraft(value);
  }, [open]); // Capture the session once; live previews intentionally change value.

  function preview(next: PlaybackParameterSettings) {
    setDraft(next);
    onPreview(next);
  }

  function cancel() {
    onPreview(initial.current);
    onClose();
  }

  function reset() {
    onPreview(defaultPlaybackParameters);
    onCommit(defaultPlaybackParameters);
    onClose();
  }

  function finish() {
    onCommit(draft);
    onClose();
  }

  function parameterControl(parameter: 'speed' | 'pitch') {
    const isTempo = parameter === 'speed';
    const semitoneMode = !isTempo && draft.pitchMode === 'semitone';
    const valueLabel = semitoneMode
      ? `${semitonesFromPitch(draft.pitch) >= 0 ? '+' : ''}${semitonesFromPitch(draft.pitch)} st`
      : isTempo ? formatPlaybackSpeed(draft.speed) : formatPlaybackPitch(draft.pitch);
    const setSlider = (sliderValue: number) => preview(playbackWithParameter(
      draft,
      parameter,
      semitoneMode ? pitchFromSemitones(sliderValue) : playbackParameterFromSlider(sliderValue),
    ));
    const sliderValue = semitoneMode ? semitonesFromPitch(draft.pitch) : playbackParameterToSlider(draft[parameter]);

    return <Box>
      <Typography align="center" sx={{ fontWeight: 700 }}>{isTempo ? 'Tempo' : 'Pitch'}</Typography>
      {!isTempo && <ToggleButtonGroup exclusive size="small" fullWidth value={draft.pitchMode} sx={{ my: 1 }}
        onChange={(_event, mode: PlaybackParameterSettings['pitchMode'] | null) => {
          if (mode) preview({ ...draft, pitchMode: mode });
        }}>
        <ToggleButton value="percent">Percent</ToggleButton>
        <ToggleButton value="semitone">Semitones</ToggleButton>
      </ToggleButtonGroup>}
      <Box className="playback-parameter-control">
        <Button aria-label={`Decrease ${isTempo ? 'tempo' : 'pitch'}`} onClick={() => preview(semitoneMode
          ? playbackWithParameter(draft, parameter, pitchFromSemitones(semitonesFromPitch(draft.pitch) - 1))
          : playbackWithStep(draft, parameter, -1))}>
          {semitoneMode ? '−1 st' : `−${formatPlaybackStep(draft.adjustmentStep)}`}
        </Button>
        <Box sx={{ minWidth: 0 }}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography color="text.secondary" variant="body2">{semitoneMode ? '−12 st' : isTempo
              ? formatPlaybackSpeed(playbackParameterMinimum) : formatPlaybackPitch(playbackParameterMinimum)}</Typography>
            <Typography color="primary" sx={{ fontWeight: 700 }}>{valueLabel}</Typography>
            <Typography color="text.secondary" variant="body2">{semitoneMode ? '+12 st' : isTempo
              ? formatPlaybackSpeed(playbackParameterMaximum) : formatPlaybackPitch(playbackParameterMaximum)}</Typography>
          </Stack>
          <Slider min={semitoneMode ? -12 : 0} max={semitoneMode ? 12 : playbackParameterSliderMaximum}
            step={1} value={sliderValue} aria-label={isTempo ? 'Tempo' : 'Pitch'}
            onChange={(_event, slider) => setSlider(Number(slider))} />
        </Box>
        <Button aria-label={`Increase ${isTempo ? 'tempo' : 'pitch'}`} onClick={() => preview(semitoneMode
          ? playbackWithParameter(draft, parameter, pitchFromSemitones(semitonesFromPitch(draft.pitch) + 1))
          : playbackWithStep(draft, parameter, 1))}>
          {semitoneMode ? '+1 st' : `+${formatPlaybackStep(draft.adjustmentStep)}`}
        </Button>
      </Box>
    </Box>;
  }

  return <Dialog open={open} onClose={cancel} fullWidth maxWidth="sm">
    <DialogTitle>Playback Speed Controls</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ pt: 1 }}>
        {parameterControl('speed')}
        <Divider />
        {parameterControl('pitch')}
        <Divider />
        <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 700, mr: 1 }}>Step</Typography>
          <ToggleButtonGroup exclusive size="small" value={draft.adjustmentStep}
            onChange={(_event, step: PlaybackParameterSettings['adjustmentStep'] | null) => {
              if (step) preview({ ...draft, adjustmentStep: step });
            }}>
            {playbackAdjustmentSteps.map((step) => <ToggleButton key={step} value={step}>
              {formatPlaybackStep(step)}
            </ToggleButton>)}
          </ToggleButtonGroup>
        </Stack>
        <FormControlLabel label="Unhook (may cause distortion)" control={<Checkbox checked={draft.unhook}
          onChange={(event) => preview(playbackWithUnhook(draft, event.target.checked))} />} />
        <FormControlLabel label="Fast-forward during silence" control={<Checkbox checked={draft.skipSilence}
          onChange={(event) => preview({ ...draft, skipSilence: event.target.checked })} />} />
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={reset}>Reset</Button><Box sx={{ flexGrow: 1 }} />
      <Button onClick={cancel}>Cancel</Button><Button onClick={finish}>OK</Button>
    </DialogActions>
  </Dialog>;
}
