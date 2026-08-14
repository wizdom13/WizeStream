import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Divider, FormControlLabel,
  IconButton, List, ListItem, ListItemIcon, ListItemText, MenuItem, Stack, Switch,
  TextField, Tooltip, Typography,
} from '@mui/material';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import CleaningServicesRounded from '@mui/icons-material/CleaningServicesRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import PaletteRounded from '@mui/icons-material/PaletteRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import SystemUpdateAltRounded from '@mui/icons-material/SystemUpdateAltRounded';
import VideoSettingsRounded from '@mui/icons-material/VideoSettingsRounded';
import type { DesktopSettings, ServiceSummary } from '../shared/contracts';

type Category = 'video' | 'download' | 'appearance' | 'history' | 'content' | 'sync' | 'learning' | 'updates';

const categories: Array<{ id: Category; title: string; summary: string; icon: React.ReactNode }> = [
  { id: 'video', title: 'Video and audio', summary: 'Playback, resolution, formats, and audio behavior', icon: <VideoSettingsRounded /> },
  { id: 'download', title: 'Download', summary: 'Download folders, file names, and download options', icon: <DownloadRounded /> },
  { id: 'appearance', title: 'Appearance', summary: 'Theme, colors, tabs, and layout', icon: <PaletteRounded /> },
  { id: 'history', title: 'History and cache', summary: 'Watch history, search history, and cached data', icon: <HistoryRounded /> },
  { id: 'content', title: 'Content', summary: 'Services, languages, regions, and content filters', icon: <HomeRounded /> },
  { id: 'sync', title: 'Device synchronization', summary: 'Securely pair trusted devices for private synchronization', icon: <DevicesRounded /> },
  { id: 'learning', title: 'Learning Mode', summary: 'Optional notes, progress, dashboard, and study statistics', icon: <SchoolRounded /> },
  { id: 'updates', title: 'Updates', summary: 'Version, changelog, and update checks', icon: <SystemUpdateAltRounded /> },
];

export function SettingsPanel({ settings, services, currentVersion, onUpdate, onReset, onOpenDownloads,
  onOpenDevices, onOpenUpdates }: {
  settings: DesktopSettings;
  services: ServiceSummary[];
  currentVersion?: string;
  onUpdate(patch: Partial<DesktopSettings>): Promise<void>;
  onReset(): Promise<void>;
  onOpenDownloads(): void;
  onOpenDevices(): void;
  onOpenUpdates(): void;
}) {
  const [category, setCategory] = useState<Category>();
  const [message, setMessage] = useState<string>();

  async function update(patch: Partial<DesktopSettings>) {
    setMessage(undefined);
    try { await onUpdate(patch); }
    catch (reason) { setMessage(reason instanceof Error ? reason.message : String(reason)); }
  }

  async function clear(method: 'library.history.clear' | 'library.search-history.clear', confirmation: string) {
    if (!window.confirm(confirmation)) return;
    setMessage(undefined);
    try {
      await window.wizestream.backend.invoke(method);
      setMessage(method === 'library.history.clear' ? 'Watch history cleared.' : 'Search history cleared.');
    } catch (reason) { setMessage(reason instanceof Error ? reason.message : String(reason)); }
  }

  if (!category) return <Stack spacing={3}>
    <Box><Typography variant="h4">Settings</Typography><Typography color="text.secondary">Choose how WizeStream Desktop works for you.</Typography></Box>
    {message && <Alert severity="info">{message}</Alert>}
    <Box className="settings-category-grid">{categories.map((item) => <Card key={item.id} variant="outlined">
      <CardActionArea onClick={() => setCategory(item.id)} sx={{ height: '100%' }}><CardContent sx={{ p: 3 }}>
        <Stack direction="row" sx={{ gap: 2, alignItems: 'center' }}><Box color="primary.main">{item.icon}</Box><Box>
          <Typography variant="h6">{item.title}</Typography><Typography color="text.secondary" variant="body2">{item.summary}</Typography>
        </Box></Stack>
      </CardContent></CardActionArea>
    </Card>)}</Box>
    <Box><Button color="inherit" startIcon={<CleaningServicesRounded />} onClick={() => {
      if (window.confirm('Reset all desktop settings to their defaults?')) void onReset();
    }}>Reset settings</Button></Box>
  </Stack>;

  const metadata = categories.find((item) => item.id === category)!;
  return <Stack spacing={3}>
    <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
      <Tooltip title="Back to Settings"><IconButton onClick={() => { setCategory(undefined); setMessage(undefined); }}><ArrowBackRounded /></IconButton></Tooltip>
      <Box><Typography variant="h4">{metadata.title}</Typography><Typography color="text.secondary">{metadata.summary}</Typography></Box>
    </Stack>
    {message && <Alert severity={message.endsWith('cleared.') ? 'success' : 'error'}>{message}</Alert>}
    <Card variant="outlined"><CardContent sx={{ p: 0 }}>
      {category === 'video' && <List disablePadding>
        <SettingSelect title="Default resolution" value={settings.defaultResolution} onChange={(value) => void update({ defaultResolution: value as DesktopSettings['defaultResolution'] })}
          options={[['best_resolution', 'Best available'], ['1080p60', '1080p60'], ['1080p', '1080p'], ['720p60', '720p60'], ['720p', '720p'], ['480p', '480p'], ['360p', '360p'], ['240p', '240p'], ['144p', '144p']]} />
        <Divider component="li" /><SettingSelect title="Default video format" value={settings.defaultVideoFormat} onChange={(value) => void update({ defaultVideoFormat: value as DesktopSettings['defaultVideoFormat'] })}
          options={[['video_mp4', 'MPEG-4'], ['video_webm', 'WebM'], ['video_3gp', '3GP']]} />
        <Divider component="li" /><SettingSelect title="Default audio format" value={settings.defaultAudioFormat} onChange={(value) => void update({ defaultAudioFormat: value as DesktopSettings['defaultAudioFormat'] })}
          options={[['audio_m4a', 'M4A'], ['audio_webm', 'WebM']]} />
        <Divider component="li" /><SettingSwitch title="Prefer original audio" summary="Select the original audio track regardless of the language" checked={settings.preferOriginalAudio} onChange={(checked) => void update({ preferOriginalAudio: checked })} />
        <Divider component="li" /><SettingSwitch title="Prefer descriptive audio" summary="Select an audio track with descriptions for visually impaired people if available" checked={settings.preferDescriptiveAudio} onChange={(checked) => void update({ preferDescriptiveAudio: checked })} />
      </List>}
      {category === 'download' && <SettingAction icon={<DownloadRounded />} title="Download folder" summary="Media and captions are stored in the WizeStream folder inside your system Downloads folder." action="Open folder" onClick={onOpenDownloads} />}
      {category === 'appearance' && <List disablePadding><SettingSelect title="Theme" value={settings.theme} onChange={(value) => void update({ theme: value as DesktopSettings['theme'] })}
        options={[['light', 'Light'], ['dark', 'Dark'], ['system', 'Follow the system']]} /></List>}
      {category === 'history' && <List disablePadding>
        <SettingSwitch title="Watch history" summary="Keep track of watched videos" checked={settings.enableWatchHistory} onChange={(checked) => void update({ enableWatchHistory: checked })} />
        <Divider component="li" /><SettingSwitch title="Search history" summary="Store search queries locally" checked={settings.enableSearchHistory} onChange={(checked) => void update({ enableSearchHistory: checked })} />
        <Divider component="li" /><SettingAction icon={<CleaningServicesRounded />} title="Clear watch history" summary="Deletes the history of played streams and the playback positions" action="Clear" onClick={() => void clear('library.history.clear', 'Clear all watch history?')} />
        <Divider component="li" /><SettingAction icon={<CleaningServicesRounded />} title="Clear search history" summary="Deletes history of search keywords" action="Clear" onClick={() => void clear('library.search-history.clear', 'Clear all search history?')} />
      </List>}
      {category === 'content' && <List disablePadding><SettingSelect title="Default content service" value={settings.defaultServiceId === null ? 'automatic' : String(settings.defaultServiceId)}
        onChange={(value) => void update({ defaultServiceId: value === 'automatic' ? null : Number(value) })}
        options={[['automatic', 'First available'], ...services.map((service) => [String(service.id), service.name] as [string, string])]} /></List>}
      {category === 'sync' && <SettingAction icon={<DevicesRounded />} title="Device synchronization" summary="Pair devices, choose data categories, and manage automatic local-network synchronization in Devices." action="Open Devices" onClick={onOpenDevices} />}
      {category === 'learning' && <List disablePadding>
        <SettingSwitch title="Enable Learning Mode" summary="Show optional learning tools without changing the normal WizeStream experience" checked={settings.learningMode} onChange={(checked) => void update({ learningMode: checked })} />
        <Divider component="li" /><SettingSwitch title="Timestamped notes" summary="Create notes linked to exact positions in videos" checked={settings.learningNotes} disabled={!settings.learningMode} onChange={(checked) => void update({ learningNotes: checked })} />
      </List>}
      {category === 'updates' && <Box sx={{ p: 4 }}><Typography variant="h6">WizeStream Desktop {currentVersion ?? ''}</Typography>
        <Alert severity="info" sx={{ my: 2 }}>Preview builds are explicitly unsigned. Updates remain manual until signed public releases are available.</Alert>
        <Button startIcon={<SystemUpdateAltRounded />} variant="outlined" onClick={onOpenUpdates}>Check for updates</Button>
      </Box>}
    </CardContent></Card>
  </Stack>;
}

function SettingSelect({ title, value, options, onChange }: { title: string; value: string; options: Array<[string, string]>; onChange(value: string): void }) {
  return <ListItem sx={{ py: 2.5 }}><ListItemText primary={title} /><TextField select size="small" value={value} onChange={(event) => onChange(event.target.value)} sx={{ minWidth: 210 }}>
    {options.map(([option, label]) => <MenuItem key={option} value={option}>{label}</MenuItem>)}</TextField></ListItem>;
}

function SettingSwitch({ title, summary, checked, disabled, onChange }: { title: string; summary: string; checked: boolean; disabled?: boolean; onChange(value: boolean): void }) {
  return <ListItem sx={{ py: 2 }}><ListItemText primary={title} secondary={summary} /><FormControlLabel sx={{ ml: 2 }} label={checked ? 'On' : 'Off'} control={<Switch checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />} /></ListItem>;
}

function SettingAction({ icon, title, summary, action, onClick }: { icon: React.ReactNode; title: string; summary: string; action: string; onClick(): void }) {
  return <ListItem sx={{ py: 2.5 }}><ListItemIcon>{icon}</ListItemIcon><ListItemText primary={title} secondary={summary} /><Button sx={{ ml: 2 }} variant="outlined" onClick={onClick}>{action}</Button></ListItem>;
}
