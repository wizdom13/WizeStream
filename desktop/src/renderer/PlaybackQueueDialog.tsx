import {
  Avatar, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, IconButton, List, ListItem, ListItemAvatar, ListItemText, Stack, Tooltip, Typography,
} from '@mui/material';
import ArrowDownwardRounded from '@mui/icons-material/ArrowDownwardRounded';
import ArrowUpwardRounded from '@mui/icons-material/ArrowUpwardRounded';
import ClearAllRounded from '@mui/icons-material/ClearAllRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import RepeatOneRounded from '@mui/icons-material/RepeatOneRounded';
import RepeatRounded from '@mui/icons-material/RepeatRounded';
import ShuffleRounded from '@mui/icons-material/ShuffleRounded';
import SubscriptionsRounded from '@mui/icons-material/SubscriptionsRounded';
import type { PlaybackQueueState, RepeatMode } from './playback-queue';

export function PlaybackQueueDialog({ open, queue, onClose, onPlay, onMove, onRemove, onClear,
  onRepeatMode, onShuffle }: {
  open: boolean;
  queue: PlaybackQueueState;
  onClose(): void;
  onPlay(index: number): void;
  onMove(from: number, to: number): void;
  onRemove(index: number): void;
  onClear(): void;
  onRepeatMode(mode: RepeatMode): void;
  onShuffle(enabled: boolean): void;
}) {
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
    <DialogTitle>Playback queue</DialogTitle>
    <DialogContent dividers sx={{ p: 0 }}>
      <Stack direction="row" sx={{ px: 2, py: 1.5, gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Button startIcon={queue.repeatMode === 'one' ? <RepeatOneRounded /> : <RepeatRounded />}
          variant={queue.repeatMode === 'off' ? 'outlined' : 'contained'} onClick={() => onRepeatMode(nextRepeatMode(queue.repeatMode))}>
          {repeatLabel(queue.repeatMode)}
        </Button>
        <Button startIcon={<ShuffleRounded />} variant={queue.shuffle ? 'contained' : 'outlined'}
          onClick={() => onShuffle(!queue.shuffle)}>Shuffle</Button>
        <Box sx={{ flexGrow: 1 }} />
        <Typography color="text.secondary">{queue.items.length} item(s)</Typography>
      </Stack>
      <Divider />
      {queue.items.length === 0 ? <Typography color="text.secondary" sx={{ p: 4, textAlign: 'center' }}>
        The playback queue is empty.
      </Typography> : <List disablePadding>{queue.items.map((item, index) => <Box key={`${item.serviceId}:${item.url}:${index}`}>
        {index > 0 && <Divider component="li" />}
        <ListItem sx={{ py: 1.5 }}>
          <ListItemAvatar><Avatar variant="rounded" src={item.thumbnailUrl}><SubscriptionsRounded /></Avatar></ListItemAvatar>
          <ListItemText primary={<Typography className="two-lines">{item.title}</Typography>} secondary={item.uploader} />
          {index === queue.currentIndex && <Chip size="small" color="primary" label="Playing" sx={{ mr: 1 }} />}
          <Tooltip title="Play"><IconButton aria-label={`Play ${item.title}`} onClick={() => onPlay(index)}><PlayArrowRounded /></IconButton></Tooltip>
          <Tooltip title="Move up"><span><IconButton aria-label={`Move ${item.title} up`} disabled={index === 0}
            onClick={() => onMove(index, index - 1)}><ArrowUpwardRounded /></IconButton></span></Tooltip>
          <Tooltip title="Move down"><span><IconButton aria-label={`Move ${item.title} down`} disabled={index === queue.items.length - 1}
            onClick={() => onMove(index, index + 1)}><ArrowDownwardRounded /></IconButton></span></Tooltip>
          <Tooltip title="Remove"><IconButton aria-label={`Remove ${item.title}`} onClick={() => onRemove(index)}><DeleteOutlineRounded /></IconButton></Tooltip>
        </ListItem>
      </Box>)}</List>}
    </DialogContent>
    <DialogActions>
      <Button color="error" startIcon={<ClearAllRounded />} disabled={queue.items.length === 0} onClick={onClear}>Clear queue</Button>
      <Box sx={{ flexGrow: 1 }} /><Button onClick={onClose}>Close</Button>
    </DialogActions>
  </Dialog>;
}

function nextRepeatMode(mode: RepeatMode): RepeatMode {
  if (mode === 'off') return 'all';
  if (mode === 'all') return 'one';
  return 'off';
}

function repeatLabel(mode: RepeatMode) {
  if (mode === 'all') return 'Repeat all';
  if (mode === 'one') return 'Repeat one';
  return 'Repeat off';
}
