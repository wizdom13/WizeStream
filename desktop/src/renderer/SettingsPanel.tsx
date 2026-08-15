import { useState } from 'react';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Divider, FormControlLabel,
  IconButton, List, ListItem, ListItemIcon, ListItemText, MenuItem, Stack, Switch,
  TextField, Tooltip, Typography,
} from '@mui/material';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import BackupRounded from '@mui/icons-material/BackupRounded';
import BlockRounded from '@mui/icons-material/BlockRounded';
import CleaningServicesRounded from '@mui/icons-material/CleaningServicesRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import PaletteRounded from '@mui/icons-material/PaletteRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import SystemUpdateAltRounded from '@mui/icons-material/SystemUpdateAltRounded';
import VideoSettingsRounded from '@mui/icons-material/VideoSettingsRounded';
import { defaultSponsorBlockSettings } from '../shared/contracts';
import type {
  BackupOperationResult, DesktopSettings, ServiceSummary, SponsorBlockBehavior,
  SponsorBlockCategoryId, SponsorBlockCategoryPreference, SponsorBlockSettings,
} from '../shared/contracts';
import { sponsorBlockCategories } from './sponsor-block';

type Category = 'video' | 'download' | 'appearance' | 'history' | 'content' | 'sponsorblock'
  | 'updates' | 'backup' | 'sync' | 'learning';

const categories: Array<{ id: Category; title: string; summary: string; icon: React.ReactNode }> = [
  { id: 'video', title: 'Video and audio', summary: 'Playback, resolution, formats, and audio behavior', icon: <VideoSettingsRounded /> },
  { id: 'download', title: 'Download', summary: 'Download folders, file names, and download options', icon: <DownloadRounded /> },
  { id: 'appearance', title: 'Appearance', summary: 'Theme, colors, tabs, and layout', icon: <PaletteRounded /> },
  { id: 'history', title: 'History and cache', summary: 'Watch history, search history, and cached data', icon: <HistoryRounded /> },
  { id: 'content', title: 'Content', summary: 'Services, languages, regions, and content filters', icon: <HomeRounded /> },
  { id: 'sponsorblock', title: 'SponsorBlock', summary: 'Skip sponsored segments and configure categories', icon: <BlockRounded /> },
  { id: 'updates', title: 'Updates', summary: 'Version, changelog, and update checks', icon: <SystemUpdateAltRounded /> },
  { id: 'backup', title: 'Backup and restore', summary: 'Export, import, and restore app data', icon: <BackupRounded /> },
  { id: 'sync', title: 'Device synchronization', summary: 'Securely pair trusted devices for private synchronization', icon: <DevicesRounded /> },
  { id: 'learning', title: 'Learning Mode', summary: 'Optional notes, progress, dashboard, and study statistics', icon: <SchoolRounded /> },
];

export function SettingsPanel({ settings, services, currentVersion, onUpdate, onReset, onOpenDownloads,
  onOpenDevices, onOpenUpdates, onSettingsRestored }: {
  settings: DesktopSettings;
  services: ServiceSummary[];
  currentVersion?: string;
  onUpdate(patch: Partial<DesktopSettings>): Promise<void>;
  onReset(): Promise<void>;
  onOpenDownloads(): void;
  onOpenDevices(): void;
  onOpenUpdates(): void;
  onSettingsRestored(settings: DesktopSettings): void;
}) {
  const [category, setCategory] = useState<Category>();
  const [message, setMessage] = useState<string>();
  const [messageSeverity, setMessageSeverity] = useState<'success' | 'error' | 'info'>('info');
  const [busy, setBusy] = useState(false);

  async function update(patch: Partial<DesktopSettings>) {
    setMessage(undefined);
    try { await onUpdate(patch); }
    catch (reason) { setMessageSeverity('error'); setMessage(reason instanceof Error ? reason.message : String(reason)); }
  }

  function updateSponsorBlock(patch: Partial<SponsorBlockSettings>) {
    void update({ sponsorBlock: { ...settings.sponsorBlock, ...patch } });
  }

  function updateSponsorBlockCategory(
    id: SponsorBlockCategoryId,
    patch: Partial<SponsorBlockCategoryPreference>,
  ) {
    updateSponsorBlock({ categories: {
      ...settings.sponsorBlock.categories,
      [id]: { ...settings.sponsorBlock.categories[id], ...patch },
    } });
  }

  async function clear(method: 'library.history.clear' | 'library.search-history.clear', confirmation: string) {
    if (!window.confirm(confirmation)) return;
    setMessage(undefined);
    try {
      await window.wizestream.backend.invoke(method);
      setMessageSeverity('success');
      setMessage(method === 'library.history.clear' ? 'Watch history cleared.' : 'Search history cleared.');
    } catch (reason) { setMessageSeverity('error'); setMessage(reason instanceof Error ? reason.message : String(reason)); }
  }

  async function backupAction(operation: 'exportFull' | 'restoreFull' | 'importSubscriptions' | 'exportSubscriptions') {
    setBusy(true); setMessage(undefined);
    try {
      const result: BackupOperationResult = await window.wizestream.backup[operation]();
      if (result.cancelled) return;
      if (result.settings) onSettingsRestored(result.settings);
      const descriptions: Record<typeof operation, string> = {
        exportFull: `Backup exported: ${result.fileName ?? 'WizeStream backup'}`,
        restoreFull: `Backup restored from ${result.fileName ?? 'the selected file'}.`,
        importSubscriptions: `Imported ${result.imported ?? 0} subscriptions from ${result.fileName ?? 'the selected file'}.`,
        exportSubscriptions: `Exported ${result.exported ?? 0} subscriptions to ${result.fileName ?? 'the selected file'}.`,
      };
      setMessageSeverity('success'); setMessage(descriptions[operation]);
    } catch (reason) {
      setMessageSeverity('error'); setMessage(reason instanceof Error ? reason.message : String(reason));
    } finally { setBusy(false); }
  }

  if (!category) return <Stack spacing={3}>
    <Box><Typography variant="h4">Settings</Typography><Typography color="text.secondary">Choose how WizeStream Desktop works for you.</Typography></Box>
    {message && <Alert severity={messageSeverity}>{message}</Alert>}
    <Box className="settings-category-grid">{categories.map((item) => <Card key={item.id} variant="outlined">
      <CardActionArea onClick={() => setCategory(item.id)} sx={{ height: '100%' }}><CardContent sx={{ p: 3 }}>
        <Stack direction="row" sx={{ gap: 2, alignItems: 'center' }}><Box color="primary.main">{item.icon}</Box><Box>
          <Typography variant="h6">{item.title}</Typography><Typography color="text.secondary" variant="body2">{item.summary}</Typography>
        </Box></Stack>
      </CardContent></CardActionArea>
    </Card>)}</Box>
  </Stack>;

  const metadata = categories.find((item) => item.id === category)!;
  return <Stack spacing={3}>
    <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }}>
      <Tooltip title="Back to Settings"><IconButton onClick={() => { setCategory(undefined); setMessage(undefined); }}><ArrowBackRounded /></IconButton></Tooltip>
      <Box><Typography variant="h4">{metadata.title}</Typography><Typography color="text.secondary">{metadata.summary}</Typography></Box>
    </Stack>
    {message && <Alert severity={messageSeverity}>{message}</Alert>}
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
      {category === 'sponsorblock' && <List disablePadding>
        <SettingAction icon={<OpenInNewRounded />} title="SponsorBlock website" summary="Open SponsorBlock project website" action="Open"
          onClick={() => window.open('https://sponsor.ajay.app', '_blank', 'noopener')} />
        <Divider component="li" /><SettingAction icon={<OpenInNewRounded />} title="Privacy policy" summary="Open SponsorBlock privacy policy" action="Open"
          onClick={() => window.open('https://wiki.sponsor.ajay.app/w/Privacy_Policy', '_blank', 'noopener')} />
        <Divider component="li" /><SettingSwitch title="Enable SponsorBlock" summary="Skip or show controls for sponsored segments" checked={settings.sponsorBlock.enabled}
          onChange={(enabled) => updateSponsorBlock({ enabled })} />
        <Divider component="li" /><SettingSwitch title="Rewind after manual seek" summary="Allow skipped segments to play if you manually seek back into them" checked={settings.sponsorBlock.gracedRewind}
          disabled={!settings.sponsorBlock.enabled} onChange={(gracedRewind) => updateSponsorBlock({ gracedRewind })} />
        <Divider component="li" /><SettingSwitch title="Skip notifications" summary="Show a short message when a segment is skipped" checked={settings.sponsorBlock.notifications}
          disabled={!settings.sponsorBlock.enabled} onChange={(notifications) => updateSponsorBlock({ notifications })} />
        <Divider component="li" /><ListItem sx={{ py: 2, gap: 1, flexWrap: 'wrap' }}>
          <ListItemText primary="Category presets" secondary="Choose which segment categories are requested and how they behave" />
          <Button disabled={!settings.sponsorBlock.enabled} onClick={() => updateSponsorBlock({ categories: Object.fromEntries(
            sponsorBlockCategories.map((item) => [item.id, { enabled: true, behavior: item.markerOnly ? 'dont_skip' : settings.sponsorBlock.categories[item.id].behavior }]),
          ) as SponsorBlockSettings['categories'] })}>Activate all</Button>
          <Button disabled={!settings.sponsorBlock.enabled} onClick={() => updateSponsorBlock({ categories: Object.fromEntries(
            sponsorBlockCategories.map((item) => [item.id, { ...settings.sponsorBlock.categories[item.id], enabled: false }]),
          ) as SponsorBlockSettings['categories'] })}>Deactivate all</Button>
          <Button disabled={!settings.sponsorBlock.enabled} onClick={() => updateSponsorBlock({ categories: defaultSponsorBlockSettings.categories })}>Reset to defaults</Button>
        </ListItem>
        {sponsorBlockCategories.map((item) => <Box component="li" key={item.id} sx={{ listStyle: 'none' }}>
          <Divider />
          <ListItem sx={{ py: 2, gap: 2 }}>
            <Box aria-hidden sx={{ width: 14, height: 38, borderRadius: 1, bgcolor: item.color, flexShrink: 0 }} />
            <ListItemText primary={item.title} secondary={item.summary} />
            <FormControlLabel sx={{ ml: 1 }} label={settings.sponsorBlock.categories[item.id].enabled ? 'On' : 'Off'} control={<Switch
              checked={settings.sponsorBlock.categories[item.id].enabled} disabled={!settings.sponsorBlock.enabled}
              onChange={(event) => updateSponsorBlockCategory(item.id, { enabled: event.target.checked })} />} />
            <TextField select size="small" label="Behavior" value={settings.sponsorBlock.categories[item.id].behavior}
              disabled={!settings.sponsorBlock.enabled || !settings.sponsorBlock.categories[item.id].enabled || item.markerOnly}
              onChange={(event) => updateSponsorBlockCategory(item.id, { behavior: event.target.value as SponsorBlockBehavior })}
              sx={{ minWidth: 145 }}>
              <MenuItem value="skip">Skip</MenuItem><MenuItem value="manual">Manual</MenuItem><MenuItem value="dont_skip">Don&apos;t skip</MenuItem>
            </TextField>
          </ListItem>
        </Box>)}
        <Divider component="li" /><ListItem sx={{ py: 2.5 }}><ListItemIcon><BlockRounded /></ListItemIcon>
          <ListItemText primary="Privacy" secondary="When enabled, WizeStream connects to SponsorBlock to request segments for videos you open. SponsorBlock can see your IP address." />
        </ListItem>
      </List>}
      {category === 'sync' && <SettingAction icon={<DevicesRounded />} title="Device synchronization" summary="Pair devices, choose data categories, and manage automatic local-network synchronization in Devices." action="Open Devices" onClick={onOpenDevices} />}
      {category === 'learning' && <List disablePadding>
        <SettingSwitch title="Enable Learning Mode" summary="Show optional learning tools without changing the normal WizeStream experience" checked={settings.learningMode} onChange={(checked) => void update({ learningMode: checked })} />
        <Divider component="li" /><SettingSwitch title="Timestamped notes" summary="Create notes linked to exact positions in videos" checked={settings.learningNotes} disabled={!settings.learningMode} onChange={(checked) => void update({ learningNotes: checked })} />
      </List>}
      {category === 'updates' && <Box sx={{ p: 4 }}><Typography variant="h6">WizeStream Desktop {currentVersion ?? ''}</Typography>
        <Alert severity="info" sx={{ my: 2 }}>Preview builds are explicitly unsigned. Updates remain manual until signed public releases are available.</Alert>
        <Button startIcon={<SystemUpdateAltRounded />} variant="outlined" onClick={onOpenUpdates}>Check for updates</Button>
      </Box>}
      {category === 'backup' && <List disablePadding>
        <ListItem sx={{ py: 2.5 }}><ListItemIcon><BackupRounded /></ListItemIcon><ListItemText primary="What full backup includes" secondary="Full backup includes subscriptions, playlists, app settings including SponsorBlock, history, search history, and Learning Mode notes." /></ListItem>
        <Divider component="li" /><SettingAction icon={<BackupRounded />} title="Import full backup" summary="Restore subscriptions, playlists, app settings, and local data from a ZIP backup." action="Import" disabled={busy} onClick={() => void backupAction('restoreFull')} />
        <Divider component="li" /><SettingAction icon={<BackupRounded />} title="Export full backup" summary="Create a ZIP backup with subscriptions, playlists, app settings, and local data." action="Export" disabled={busy} onClick={() => void backupAction('exportFull')} />
        <Divider component="li" /><SettingAction icon={<CleaningServicesRounded />} title="Reset settings" summary="Reset all settings to their default values" action="Reset" disabled={busy} onClick={() => {
          if (!window.confirm('Do you want to restore defaults?')) return;
          setBusy(true); setMessage(undefined);
          void onReset().then(() => { setMessageSeverity('success'); setMessage('Settings restored to defaults.'); })
            .catch((reason: unknown) => { setMessageSeverity('error'); setMessage(reason instanceof Error ? reason.message : String(reason)); })
            .finally(() => setBusy(false));
        }} />
        <Divider component="li" /><SettingAction icon={<BackupRounded />} title="Export subscriptions only" summary="Use this for Android-compatible JSON subscription migration only. Full backup already includes subscriptions." action="Export" disabled={busy} onClick={() => void backupAction('exportSubscriptions')} />
        <Divider component="li" /><SettingAction icon={<BackupRounded />} title="Import subscriptions only" summary="Import an Android JSON subscription export or subscriptions from an Android full-backup ZIP. Existing subscriptions are merged." action="Import" disabled={busy} onClick={() => void backupAction('importSubscriptions')} />
      </List>}
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

function SettingAction({ icon, title, summary, action, disabled, onClick }: { icon: React.ReactNode; title: string; summary: string; action: string; disabled?: boolean; onClick(): void }) {
  return <ListItem sx={{ py: 2.5 }}><ListItemIcon>{icon}</ListItemIcon><ListItemText primary={title} secondary={summary} /><Button sx={{ ml: 2 }} variant="outlined" disabled={disabled} onClick={onClick}>{action}</Button></ListItem>;
}
