import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  Alert, AppBar, Avatar, Box, Button, Card, CardActionArea, CardContent, Chip,
  Checkbox, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControlLabel, IconButton, InputAdornment, LinearProgress, List,
  ListItem, ListItemAvatar, ListItemButton, ListItemIcon, ListItemText, MenuItem,
  Slider, Stack, Switch, TextField, Toolbar, Tooltip, Typography,
} from '@mui/material';
import { defineMpvVideoElement, type MpvVideoElement } from 'electron-mpv-video/renderer';
import AddRounded from '@mui/icons-material/AddRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import FolderOpenRounded from '@mui/icons-material/FolderOpenRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import NoteAddRounded from '@mui/icons-material/NoteAddRounded';
import PauseRounded from '@mui/icons-material/PauseRounded';
import PlaylistAddRounded from '@mui/icons-material/PlaylistAddRounded';
import PlaylistPlayRounded from '@mui/icons-material/PlaylistPlayRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import ReplayRounded from '@mui/icons-material/ReplayRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import StopRounded from '@mui/icons-material/StopRounded';
import SubscriptionsRounded from '@mui/icons-material/SubscriptionsRounded';
import SystemUpdateAltRounded from '@mui/icons-material/SystemUpdateAltRounded';
import type {
  DownloadJob, DownloadSource, EmbeddedPlayerRequest, HistoryItem, LearningNote, LibraryStream, PlayerStatus, PlaylistItem, PlaylistSummary,
  SearchHistoryItem, SearchItem, ServiceSummary, StreamDetails, StreamVariant, SubtitleVariant,
  AutomaticSyncPolicy, SubscriptionItem, SyncRunLog, SyncRunResult, SyncStatus,
  UpdateState,
} from '../shared/contracts';

defineMpvVideoElement();

type Section = 'discover' | 'subscriptions' | 'playlists' | 'history' | 'learning' | 'downloads' | 'sync';

const navigation: Array<{ id: Section; label: string; icon: React.ReactNode }> = [
  { id: 'discover', label: 'Discover', icon: <HomeRounded /> },
  { id: 'subscriptions', label: 'Subscriptions', icon: <SubscriptionsRounded /> },
  { id: 'playlists', label: 'Playlists', icon: <PlaylistPlayRounded /> },
  { id: 'history', label: 'History', icon: <HistoryRounded /> },
  { id: 'learning', label: 'Learning', icon: <SchoolRounded /> },
  { id: 'downloads', label: 'Downloads', icon: <DownloadRounded /> },
  { id: 'sync', label: 'Devices', icon: <DevicesRounded /> },
];

export function App() {
  const [section, setSection] = useState<Section>('discover');
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [serviceId, setServiceId] = useState(0);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchItem[]>([]);
  const [selected, setSelected] = useState<StreamDetails>();
  const [sync, setSync] = useState<SyncStatus>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [mpv, setMpv] = useState<PlayerStatus>();
  const [videoChoice, setVideoChoice] = useState('auto');
  const [audioChoice, setAudioChoice] = useState('auto');
  const [subtitleChoice, setSubtitleChoice] = useState('none');
  const [embeddedRequest, setEmbeddedRequest] = useState<EmbeddedPlayerRequest & { title: string; nonce: number }>();
  const [updateState, setUpdateState] = useState<UpdateState>();
  const [updateDialogOpen, setUpdateDialogOpen] = useState(false);

  useEffect(() => {
    Promise.all([
      window.wizestream.backend.invoke<ServiceSummary[]>('services.list'),
      window.wizestream.backend.invoke<SyncStatus>('sync.status'),
      window.wizestream.player.status(),
    ]).then(([availableServices, syncStatus, playerStatus]) => {
      setServices(availableServices);
      setServiceId(availableServices[0]?.id ?? 0);
      setSync(syncStatus);
      setMpv(playerStatus);
    }).catch((reason: unknown) => setError(errorMessage(reason)));
  }, []);

  useEffect(() => {
    void window.wizestream.updates.state().then((state) => {
      setUpdateState(state);
      if (state.status === 'available' || state.status === 'downloaded') setUpdateDialogOpen(true);
    });
    return window.wizestream.updates.onChanged((state) => {
      setUpdateState(state);
      if (state.status === 'available' || state.status === 'downloaded') setUpdateDialogOpen(true);
    });
  }, []);

  const selectedVideo = selected && videoChoice !== 'auto'
    ? selected.videoStreams[Number(videoChoice)] : undefined;
  const selectedAudio = selected && audioChoice !== 'auto'
    ? selected.audioStreams[Number(audioChoice)] : undefined;
  const selectedSubtitle = selected && subtitleChoice !== 'none'
    ? selected.subtitles[Number(subtitleChoice)] : undefined;
  const automaticVideo = selected && !selected.hlsUrl && !selected.dashMpdUrl
    ? selected.videoStreams.find((stream) => !stream.videoOnly) ?? selected.videoStreams[0]
    : undefined;
  const playbackVideo = selectedVideo ?? automaticVideo;
  const selectedPlaybackUrl = useMemo(() => selectedVideo?.url ?? selected?.hlsUrl
    ?? selected?.dashMpdUrl
    ?? automaticVideo?.url
    ?? selected?.audioStreams[0]?.url, [selected, selectedVideo, automaticVideo]);
  const selectedLibraryStream = useMemo(() => selected ? detailsToLibraryStream(selected) : undefined, [selected]);
  const effectiveAudio = playbackVideo?.videoOnly
    ? selectedAudio ?? selected?.audioStreams.find((stream) => !playbackVideo.audioTrackId
      || stream.audioTrackId === playbackVideo.audioTrackId) ?? selected?.audioStreams[0]
    : selectedAudio;
  const embeddedSelection = Boolean(mpv?.embeddedAvailable && selectedPlaybackUrl);

  async function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true); setError(undefined); setSelected(undefined); setEmbeddedRequest(undefined);
    try {
      const searchQuery = query.trim();
      setResults(await window.wizestream.backend.invoke<SearchItem[]>('search', {
        serviceId, query: searchQuery,
      }));
      await window.wizestream.backend.invoke('library.search-history.record', { serviceId, query: searchQuery });
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }

  const resolveStream = useCallback(async (url: string) => {
    setLoading(true); setError(undefined);
    try {
      setSelected(await window.wizestream.backend.invoke<StreamDetails>('stream.resolve', { url }));
      setVideoChoice('auto'); setAudioChoice('auto'); setSubtitleChoice('none'); setEmbeddedRequest(undefined);
      setSection('discover');
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }, []);

  async function openResult(item: SearchItem) {
    if (item.type === 'STREAM') await resolveStream(item.url);
  }

  async function playSelected() {
    if (!selectedPlaybackUrl || !selected || !selectedLibraryStream) return;
    try {
      if (embeddedSelection) {
        setEmbeddedRequest({
          source: selectedPlaybackUrl, title: selected.name, nonce: Date.now(),
          audio: effectiveAudio ? playerTrack(effectiveAudio, audioLabel(effectiveAudio)) : undefined,
          subtitle: selectedSubtitle ? playerTrack(selectedSubtitle, subtitleLabel(selectedSubtitle)) : undefined,
        });
      } else {
        await window.wizestream.player.play({
          url: selectedPlaybackUrl,
          title: selected.name,
          audioUrl: effectiveAudio?.url,
          subtitleUrl: selectedSubtitle?.url,
        });
      }
      await window.wizestream.backend.invoke('library.history.record', { ...selectedLibraryStream });
      setMpv(await window.wizestream.player.status());
    } catch (reason) { setError(errorMessage(reason)); }
  }

  async function stopAllPlayback() {
    window.dispatchEvent(new Event('wizestream-stop-player'));
    await window.wizestream.player.stop();
    setMpv(await window.wizestream.player.status());
  }

  async function openUpdates() {
    setUpdateDialogOpen(true);
    if (!updateState || ['idle', 'up-to-date', 'error'].includes(updateState.status)) {
      setUpdateState(await window.wizestream.updates.check());
    }
  }

  return (
    <Box className="app-shell">
      <Box component="nav" className="navigation-rail">
        <Avatar sx={{ width: 48, height: 48, mb: 2, bgcolor: 'primary.main' }}>W</Avatar>
        <List sx={{ width: '100%' }}>
          {navigation.map((item) => (
            <ListItemButton key={item.id} selected={section === item.id} onClick={() => setSection(item.id)}>
              <ListItemIcon>{item.icon}</ListItemIcon><ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Box>
      <Box component="main" className="content-column">
        <AppBar position="sticky" color="transparent" elevation={0}>
          <Toolbar sx={{ gap: 2 }}>
            <Typography variant="h6" sx={{ flexGrow: 1 }}>WizeStream Desktop</Typography>
            <Chip color={mpv?.embeddedAvailable || mpv?.externalAvailable ? 'success' : 'default'}
              label={mpv?.embeddedAvailable ? 'embedded libmpv' : mpv?.externalAvailable ? 'external mpv fallback' : 'mpv unavailable'} />
            <Tooltip title={updateState?.status === 'downloaded' ? 'Update ready to install'
              : updateState?.status === 'available' ? 'Update available' : 'Check for updates'}>
              <IconButton color={['available', 'downloaded'].includes(updateState?.status ?? '') ? 'primary' : 'default'}
                onClick={() => void openUpdates()}><SystemUpdateAltRounded /></IconButton>
            </Tooltip>
            {(mpv?.running || embeddedRequest) && <Tooltip title="Stop player"><IconButton onClick={() => void stopAllPlayback()}><StopRounded /></IconButton></Tooltip>}
          </Toolbar>
        </AppBar>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          {section === 'discover' ? <>
            <Stack spacing={1} sx={{ mb: 4 }}><Typography variant="h4">Watch without the noise</Typography><Typography color="text.secondary">Search through the same WizeStreamExtractor used by Android.</Typography></Stack>
            <Box component="form" onSubmit={submitSearch} className="search-row">
              <TextField select label="Service" value={serviceId} onChange={(event) => setServiceId(Number(event.target.value))} sx={{ minWidth: 190 }}>
                {services.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}
              </TextField>
              <TextField fullWidth label="Search" value={query} onChange={(event) => setQuery(event.target.value)} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }} />
              <Button type="submit" variant="contained" size="large" disabled={loading}>Search</Button>
            </Box>
            {error && <Alert severity="error" sx={{ mt: 3 }}>{error}</Alert>}
            {loading && <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>}
            {embeddedRequest && mpv?.embeddedAvailable && <EmbeddedPlayer request={embeddedRequest}
              externalAvailable={Boolean(mpv.externalAvailable)} onError={setError} />}
            {selected && <Card sx={{ mt: 4, overflow: 'hidden' }}><Box className="details-card">
              {selected.thumbnailUrl && <Box component="img" src={selected.thumbnailUrl} alt="" className="details-thumbnail" />}
              <CardContent sx={{ p: 4 }}><Chip label={selected.streamType} size="small" /><Typography variant="h4" sx={{ mt: 2 }}>{selected.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{selected.uploaderName}</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 3, flexWrap: 'wrap' }}><Chip label={`${selected.videoStreams.length} video variants`} /><Chip label={`${selected.audioStreams.length} audio variants`} /><Chip label={`${selected.subtitles.length} captions`} /><Chip label={`${Math.round(selected.duration / 60)} min`} /></Stack>
                <Box className="stream-selectors" sx={{ mt: 3 }}>
                  <TextField select label="Video" value={videoChoice} onChange={(event) => setVideoChoice(event.target.value)}>
                    <MenuItem value="auto">Automatic</MenuItem>
                    {selected.videoStreams.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{videoLabel(stream)}</MenuItem>)}
                  </TextField>
                  <TextField select label="Audio" value={audioChoice} onChange={(event) => {
                    const value = event.target.value; setAudioChoice(value);
                    const stream = value === 'auto' && playbackVideo?.videoOnly
                      ? selected.audioStreams.find((item) => !playbackVideo.audioTrackId
                        || item.audioTrackId === playbackVideo.audioTrackId) ?? selected.audioStreams[0]
                      : value === 'auto' ? undefined : selected.audioStreams[Number(value)];
                    setEmbeddedRequest((current) => current ? { ...current,
                      audio: stream ? playerTrack(stream, audioLabel(stream)) : undefined } : current);
                  }}>
                    <MenuItem value="auto">Automatic</MenuItem>
                    {selected.audioStreams.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{audioLabel(stream)}</MenuItem>)}
                  </TextField>
                  <TextField select label="Captions" value={subtitleChoice} onChange={(event) => {
                    const value = event.target.value; setSubtitleChoice(value);
                    const stream = value === 'none' ? undefined : selected.subtitles[Number(value)];
                    setEmbeddedRequest((current) => current ? { ...current,
                      subtitle: stream ? playerTrack(stream, subtitleLabel(stream)) : undefined } : current);
                  }}>
                    <MenuItem value="none">Off</MenuItem>
                    {selected.subtitles.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{subtitleLabel(stream)}</MenuItem>)}
                  </TextField>
                </Box>
                {playbackVideo?.videoOnly && !effectiveAudio && <Alert severity="warning" sx={{ mt: 2 }}>This video-only variant requires an audio track.</Alert>}
                <Stack direction="row" spacing={1} sx={{ mt: 4, flexWrap: 'wrap' }}>
                  <Button startIcon={<PlayArrowRounded />} variant="contained" size="large"
                    disabled={!selectedPlaybackUrl || (playbackVideo?.videoOnly && !effectiveAudio)
                      || (!embeddedSelection && !mpv?.externalAvailable)} onClick={() => void playSelected()}>{embeddedSelection ? 'Play embedded' : 'Play with mpv'}</Button>
                  <Button startIcon={<DownloadRounded />} variant="outlined" size="large" onClick={() => setSection('downloads')}>Download</Button>
                  <Button startIcon={<PlaylistAddRounded />} variant="outlined" size="large" onClick={() => setSection('playlists')}>Add to playlist</Button>
                  <Button startIcon={<NoteAddRounded />} variant="outlined" size="large" onClick={() => setSection('learning')}>Add note</Button>
                </Stack>
              </CardContent>
            </Box></Card>}
            {!loading && !selected && results.length > 0 && <Box className="result-grid" sx={{ mt: 4 }}>{results.map((item) => <Card key={`${item.type}:${item.url}`} variant="outlined"><CardActionArea onClick={() => void openResult(item)} disabled={item.type !== 'STREAM'} sx={{ height: '100%' }}>{item.thumbnailUrl && <Box component="img" src={item.thumbnailUrl} alt="" className="result-thumbnail" />}<CardContent><Chip label={item.type.toLowerCase()} size="small" /><Typography variant="h6" sx={{ mt: 1 }} className="two-lines">{item.name}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>{item.uploaderName}</Typography></CardContent></CardActionArea></Card>)}</Box>}
          </> : section === 'subscriptions' ? <SubscriptionsPanel services={services} />
            : section === 'playlists' ? <PlaylistsPanel currentStream={selectedLibraryStream} onOpen={resolveStream} />
              : section === 'history' ? <HistoryPanel onOpen={resolveStream} />
                : section === 'learning' ? <LearningPanel currentStream={selectedLibraryStream} onOpen={resolveStream} />
                  : section === 'downloads' ? <DownloadsPanel currentStream={selected} />
                    : <SyncPanel sync={sync} onRefresh={() => void window.wizestream.backend.invoke<SyncStatus>('sync.status').then(setSync)} />}
        </Container>
      </Box>
      <Dialog open={updateDialogOpen} onClose={() => setUpdateDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>WizeStream Desktop updates</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography color="text.secondary">
              Installed version {updateState?.currentVersion ?? 'unknown'} · beta channel
            </Typography>
            {updateState?.version && <Typography variant="h6">Version {updateState.version}</Typography>}
            {updateState?.message && <Alert severity={updateState.status === 'error' ? 'error'
              : updateState.status === 'downloaded' ? 'success' : 'info'}>{updateState.message}</Alert>}
            {updateState?.status === 'checking' && <LinearProgress />}
            {updateState?.status === 'downloading' && <LinearProgress variant="determinate" value={updateState.percent ?? 0} />}
            {updateState?.releaseNotes && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {updateState.releaseNotes}
            </Typography>}
            <Typography variant="caption" color="text.secondary">
              WizeStream never downloads or restarts for an update without your confirmation.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUpdateDialogOpen(false)}>
            {updateState?.status === 'available' || updateState?.status === 'downloaded' ? 'Later' : 'Close'}
          </Button>
          {updateState?.status === 'available' && <Button variant="contained"
            onClick={() => void window.wizestream.updates.download().then(setUpdateState)}>Download update</Button>}
          {updateState?.status === 'downloaded' && <Button variant="contained"
            onClick={() => void window.wizestream.updates.install()}>Restart and install</Button>}
          {updateState && ['idle', 'up-to-date', 'error'].includes(updateState.status) && <Button variant="contained"
            onClick={() => void window.wizestream.updates.check().then(setUpdateState)}>Check again</Button>}
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function EmbeddedPlayer({ request, externalAvailable, onError }: {
  request: EmbeddedPlayerRequest & { title: string; nonce: number }; externalAvailable: boolean;
  onError(value: string): void;
}) {
  const player = useRef<MpvVideoElement>(null);
  const [state, setState] = useState({ status: 'Opening', time: 0, duration: 0, rendererName: 'libmpv',
    audioTrack: 'auto', subtitleTrack: 'off' });
  const [localError, setLocalError] = useState<string>();

  useEffect(() => {
    const element = player.current;
    if (!element) return;
    const update = (event: Event) => setState((event as CustomEvent<typeof state>).detail);
    const fail = (event: Event) => { const value = String((event as CustomEvent<unknown>).detail); setLocalError(value); onError(value); };
    element.addEventListener('mpv-state', update);
    element.addEventListener('mpv-error', fail);
    setLocalError(undefined);
    void element.openMedia(request).then(() => element.play()).catch((reason: unknown) => {
      const value = errorMessage(reason); setLocalError(value); onError(value);
    });
    return () => { element.removeEventListener('mpv-state', update); element.removeEventListener('mpv-error', fail); };
  }, [request.nonce, request.source, onError]);

  useEffect(() => {
    if (!player.current) return;
    void player.current.setAudioTrack(request.audio).catch((reason: unknown) => onError(`Audio track: ${errorMessage(reason)}`));
  }, [request.audio?.url, onError]);

  useEffect(() => {
    if (!player.current) return;
    void player.current.setSubtitleTrack(request.subtitle).catch((reason: unknown) => onError(`Caption track: ${errorMessage(reason)}`));
  }, [request.subtitle?.url, onError]);

  useEffect(() => {
    const stop = () => { void player.current?.stop(); };
    window.addEventListener('wizestream-stop-player', stop);
    return () => window.removeEventListener('wizestream-stop-player', stop);
  }, []);

  return <Card sx={{ mt: 4, overflow: 'hidden' }}><Box className="embedded-player-frame">
    <mpv-video ref={player} render-mode="shared-texture" volume="80" title={request.title} />
  </Box><CardContent>{localError && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" disabled={!externalAvailable} onClick={() => void window.wizestream.player.play({
    url: request.source, title: request.title, audioUrl: request.audio?.url, subtitleUrl: request.subtitle?.url,
  })}>Open with external mpv</Button>}>{localError}</Alert>}<Stack direction="row" sx={{ alignItems: 'center', gap: 2 }}>
    <IconButton aria-label="Play" onClick={() => void player.current?.play()}><PlayArrowRounded /></IconButton>
    <IconButton aria-label="Pause" onClick={() => void player.current?.pause()}><PauseRounded /></IconButton>
    <IconButton aria-label="Stop" onClick={() => void player.current?.stop()}><StopRounded /></IconButton>
    <Typography className="mono" variant="body2">{formatTimestamp(state.time)}</Typography>
    <Slider min={0} max={Math.max(1, state.duration)} value={Math.min(state.time, Math.max(1, state.duration))}
      onChangeCommitted={(_event, value) => void player.current?.seek(Number(value))} sx={{ flexGrow: 1 }} />
    <Typography className="mono" variant="body2">{formatTimestamp(state.duration)}</Typography>
    <Chip size="small" label={`Audio ${request.audio?.title ?? state.audioTrack}`} />
    <Chip size="small" label={`Captions ${request.subtitle?.title ?? state.subtitleTrack}`} />
    <Chip size="small" label={`${state.status} · ${state.rendererName}`} />
  </Stack></CardContent></Card>;
}

function SubscriptionsPanel({ services }: { services: ServiceSummary[] }) {
  const [items, setItems] = useState<SubscriptionItem[]>([]);
  const [editing, setEditing] = useState<SubscriptionItem>();
  const [open, setOpen] = useState(false);
  const [serviceId, setServiceId] = useState(0);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [error, setError] = useState<string>();
  const load = useCallback(() => window.wizestream.backend.invoke<SubscriptionItem[]>('library.subscriptions.list').then(setItems).catch((reason: unknown) => setError(errorMessage(reason))), []);
  useEffect(() => { void load(); }, [load]);

  function showEditor(item?: SubscriptionItem) {
    setEditing(item); setServiceId(item?.serviceId ?? services[0]?.id ?? 0); setName(item?.name ?? '');
    setUrl(item?.url ?? ''); setAvatarUrl(item?.avatarUrl ?? ''); setError(undefined); setOpen(true);
  }
  async function save() {
    try {
      await window.wizestream.backend.invoke('library.subscriptions.save', { serviceId, name, url, avatarUrl });
      setOpen(false); await load();
    } catch (reason) { setError(errorMessage(reason)); }
  }
  async function remove(item: SubscriptionItem) {
    if (!window.confirm(`Remove the subscription to ${item.name}?`)) return;
    try { await window.wizestream.backend.invoke('library.subscriptions.delete', { serviceId: item.serviceId, url: item.url }); await load(); }
    catch (reason) { setError(errorMessage(reason)); }
  }
  return <Stack spacing={3}><PanelHeader title="Subscriptions" description="Manage channels synchronized with your Android devices." action={<Button startIcon={<AddRounded />} variant="contained" onClick={() => showEditor()}>Add channel</Button>} />
    {error && <Alert severity="error">{error}</Alert>}
    {items.length === 0 ? <LibraryEmpty text="No subscriptions yet." /> : <Card variant="outlined"><List disablePadding>{items.map((item, index) => <Box key={`${item.serviceId}:${item.url}`}>{index > 0 && <Divider />}<ListItem secondaryAction={<Stack direction="row"><IconButton aria-label="Edit subscription" onClick={() => showEditor(item)}><EditRounded /></IconButton><IconButton aria-label="Delete subscription" onClick={() => void remove(item)}><DeleteOutlineRounded /></IconButton></Stack>}><ListItemAvatar><Avatar src={item.avatarUrl}><SubscriptionsRounded /></Avatar></ListItemAvatar><ListItemText primary={item.name} secondary={item.url} /></ListItem></Box>)}</List></Card>}
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm"><DialogTitle>{editing ? 'Edit subscription' : 'Add subscription'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField select label="Service" value={serviceId} disabled={Boolean(editing)} onChange={(event) => setServiceId(Number(event.target.value))}>{services.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}</TextField><TextField label="Channel name" value={name} onChange={(event) => setName(event.target.value)} /><TextField label="Channel URL" value={url} disabled={Boolean(editing)} onChange={(event) => setUrl(event.target.value)} /><TextField label="Avatar URL (optional)" value={avatarUrl} onChange={(event) => setAvatarUrl(event.target.value)} />{error && <Alert severity="error">{error}</Alert>}</Stack></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" disabled={!name.trim() || !url.trim()} onClick={() => void save()}>Save</Button></DialogActions></Dialog>
  </Stack>;
}

function PlaylistsPanel({ currentStream, onOpen }: { currentStream?: LibraryStream; onOpen(url: string): Promise<void> }) {
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [items, setItems] = useState<PlaylistItem[]>([]);
  const [dialog, setDialog] = useState<'create' | 'rename'>();
  const [name, setName] = useState('');
  const [error, setError] = useState<string>();
  const loadPlaylists = useCallback(async () => {
    try {
      const value = await window.wizestream.backend.invoke<PlaylistSummary[]>('library.playlists.list');
      setPlaylists(value); setSelectedId((current) => current && value.some((item) => item.id === current) ? current : value[0]?.id);
    } catch (reason) { setError(errorMessage(reason)); }
  }, []);
  const loadItems = useCallback(async (id?: string) => {
    if (!id) { setItems([]); return; }
    try { setItems(await window.wizestream.backend.invoke<PlaylistItem[]>('library.playlists.items', { playlistId: id })); }
    catch (reason) { setError(errorMessage(reason)); }
  }, []);
  useEffect(() => { void loadPlaylists(); }, [loadPlaylists]);
  useEffect(() => { void loadItems(selectedId); }, [loadItems, selectedId]);
  const selectedPlaylist = playlists.find((item) => item.id === selectedId);

  async function savePlaylist() {
    try {
      if (dialog === 'create') await window.wizestream.backend.invoke('library.playlists.create', { name });
      else if (selectedId) await window.wizestream.backend.invoke('library.playlists.rename', { id: selectedId, name });
      setDialog(undefined); setName(''); await loadPlaylists();
    } catch (reason) { setError(errorMessage(reason)); }
  }
  async function deletePlaylist() {
    if (!selectedId) return;
    if (!window.confirm(`Delete ${selectedPlaylist?.name ?? 'this playlist'} and all of its items?`)) return;
    try { await window.wizestream.backend.invoke('library.playlists.delete', { id: selectedId }); setSelectedId(undefined); await loadPlaylists(); }
    catch (reason) { setError(errorMessage(reason)); }
  }
  async function addCurrent() {
    if (!selectedId || !currentStream) return;
    try { await window.wizestream.backend.invoke('library.playlists.add-item', { playlistId: selectedId, ...currentStream }); await Promise.all([loadItems(selectedId), loadPlaylists()]); }
    catch (reason) { setError(errorMessage(reason)); }
  }
  async function removeItem(itemId: string) {
    if (!selectedId) return;
    if (!window.confirm('Remove this item from the playlist?')) return;
    try { await window.wizestream.backend.invoke('library.playlists.delete-item', { playlistId: selectedId, itemId }); await Promise.all([loadItems(selectedId), loadPlaylists()]); }
    catch (reason) { setError(errorMessage(reason)); }
  }
  return <Stack spacing={3}><PanelHeader title="Playlists" description="Create local playlists and edit their synchronized items." action={<Button startIcon={<AddRounded />} variant="contained" onClick={() => { setName(''); setDialog('create'); }}>New playlist</Button>} />
    {error && <Alert severity="error">{error}</Alert>}
    <Box className="library-split"><Card variant="outlined"><List disablePadding>{playlists.length === 0 ? <ListItem><ListItemText primary="No playlists yet" /></ListItem> : playlists.map((playlist) => <ListItemButton key={playlist.id} selected={playlist.id === selectedId} onClick={() => setSelectedId(playlist.id)}><ListItemIcon><PlaylistPlayRounded /></ListItemIcon><ListItemText primary={playlist.name} secondary={`${playlist.itemCount} item${playlist.itemCount === 1 ? '' : 's'}`} /></ListItemButton>)}</List></Card>
      <Card variant="outlined"><CardContent><Stack direction="row" sx={{ alignItems: 'center', gap: 1 }}><Box sx={{ flexGrow: 1 }}><Typography variant="h6">{selectedPlaylist?.name ?? 'Choose a playlist'}</Typography>{currentStream && <Typography color="text.secondary" variant="body2">Ready to add: {currentStream.title}</Typography>}</Box>{selectedPlaylist && <><Tooltip title="Rename"><IconButton onClick={() => { setName(selectedPlaylist.name); setDialog('rename'); }}><EditRounded /></IconButton></Tooltip><Tooltip title="Delete playlist"><IconButton onClick={() => void deletePlaylist()}><DeleteOutlineRounded /></IconButton></Tooltip></>}<Button startIcon={<PlaylistAddRounded />} variant="contained" disabled={!selectedId || !currentStream} onClick={() => void addCurrent()}>Add current</Button></Stack></CardContent><Divider />
        {items.length === 0 ? <CardContent><Typography color="text.secondary">This playlist is empty. Select a stream in Discover, then return here to add it.</Typography></CardContent> : <List disablePadding>{items.map((item, index) => <Box key={item.itemId}>{index > 0 && <Divider />}<ListItemButton onClick={() => void onOpen(item.url)}><ListItemAvatar><Avatar variant="rounded" src={item.thumbnailUrl}><PlayArrowRounded /></Avatar></ListItemAvatar><ListItemText primary={item.title} secondary={item.uploader} /><IconButton aria-label="Remove playlist item" onClick={(event) => { event.stopPropagation(); void removeItem(item.itemId); }}><DeleteOutlineRounded /></IconButton></ListItemButton></Box>)}</List>}
      </Card></Box>
    <Dialog open={Boolean(dialog)} onClose={() => setDialog(undefined)} fullWidth maxWidth="xs"><DialogTitle>{dialog === 'rename' ? 'Rename playlist' : 'New playlist'}</DialogTitle><DialogContent><TextField autoFocus fullWidth label="Name" value={name} onChange={(event) => setName(event.target.value)} sx={{ mt: 1 }} /></DialogContent><DialogActions><Button onClick={() => setDialog(undefined)}>Cancel</Button><Button variant="contained" disabled={!name.trim()} onClick={() => void savePlaylist()}>Save</Button></DialogActions></Dialog>
  </Stack>;
}

function HistoryPanel({ onOpen }: { onOpen(url: string): Promise<void> }) {
  const [items, setItems] = useState<HistoryItem[]>([]);
  const [searches, setSearches] = useState<SearchHistoryItem[]>([]);
  const [error, setError] = useState<string>();
  const load = useCallback(() => Promise.all([
    window.wizestream.backend.invoke<HistoryItem[]>('library.history.list'),
    window.wizestream.backend.invoke<SearchHistoryItem[]>('library.search-history.list'),
  ]).then(([history, searchHistory]) => { setItems(history); setSearches(searchHistory); })
    .catch((reason: unknown) => setError(errorMessage(reason))), []);
  useEffect(() => { void load(); }, [load]);
  async function remove(id: string) { if (!window.confirm('Delete this watch-history entry?')) return; try { await window.wizestream.backend.invoke('library.history.delete', { id }); await load(); } catch (reason) { setError(errorMessage(reason)); } }
  async function clear() { if (!window.confirm('Clear all watch history on this device?')) return; try { await window.wizestream.backend.invoke('library.history.clear'); await load(); } catch (reason) { setError(errorMessage(reason)); } }
  async function removeSearch(id: string) { if (!window.confirm('Delete this search-history entry?')) return; try { await window.wizestream.backend.invoke('library.search-history.delete', { id }); await load(); } catch (reason) { setError(errorMessage(reason)); } }
  async function clearSearches() { if (!window.confirm('Clear all search history on this device?')) return; try { await window.wizestream.backend.invoke('library.search-history.clear'); await load(); } catch (reason) { setError(errorMessage(reason)); } }
  return <Stack spacing={4}><PanelHeader title="History" description="Manage watch and search records shared through device sync." action={<></>} />{error && <Alert severity="error">{error}</Alert>}
    <Stack spacing={2}><PanelHeader title="Watch history" description="Playback records are added when mpv starts." action={<Button startIcon={<DeleteOutlineRounded />} variant="outlined" color="error" disabled={items.length === 0} onClick={() => void clear()}>Clear watch history</Button>} />{items.length === 0 ? <LibraryEmpty text="No watch history yet. Playing a stream adds it here." /> : <Card variant="outlined"><List disablePadding>{items.map((item, index) => <Box key={item.id}>{index > 0 && <Divider />}<ListItemButton onClick={() => void onOpen(item.url)}><ListItemAvatar><Avatar variant="rounded" src={item.thumbnailUrl}><HistoryRounded /></Avatar></ListItemAvatar><ListItemText primary={item.title} secondary={`${item.uploader || 'Unknown uploader'} · ${new Date(item.watchedAt).toLocaleString()}`} /><IconButton aria-label="Delete history entry" onClick={(event) => { event.stopPropagation(); void remove(item.id); }}><DeleteOutlineRounded /></IconButton></ListItemButton></Box>)}</List></Card>}</Stack>
    <Stack spacing={2}><PanelHeader title="Search history" description="Successful extractor searches are retained locally." action={<Button startIcon={<DeleteOutlineRounded />} variant="outlined" color="error" disabled={searches.length === 0} onClick={() => void clearSearches()}>Clear searches</Button>} />{searches.length === 0 ? <LibraryEmpty text="No search history yet." /> : <Card variant="outlined"><List disablePadding>{searches.map((item, index) => <Box key={item.id}>{index > 0 && <Divider />}<ListItem secondaryAction={<IconButton aria-label="Delete search entry" onClick={() => void removeSearch(item.id)}><DeleteOutlineRounded /></IconButton>}><ListItemAvatar><Avatar><SearchRounded /></Avatar></ListItemAvatar><ListItemText primary={item.query} secondary={new Date(item.searchedAt).toLocaleString()} /></ListItem></Box>)}</List></Card>}</Stack>
  </Stack>;
}

function LearningPanel({ currentStream, onOpen }: { currentStream?: LibraryStream; onOpen(url: string): Promise<void> }) {
  const [notes, setNotes] = useState<LearningNote[]>([]);
  const [editing, setEditing] = useState<LearningNote>();
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState('0');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string>();
  const load = useCallback(() => window.wizestream.backend.invoke<LearningNote[]>('library.learning.list').then(setNotes).catch((reason: unknown) => setError(errorMessage(reason))), []);
  useEffect(() => { void load(); }, [load]);
  function showEditor(value?: LearningNote) { setEditing(value); setPosition(String(value?.positionSeconds ?? 0)); setNote(value?.note ?? ''); setError(undefined); setOpen(true); }
  async function save() {
    const stream = editing ?? currentStream;
    if (!stream) return;
    try { await window.wizestream.backend.invoke('library.learning.save', { ...stream, id: editing?.id, positionSeconds: Number(position), note }); setOpen(false); await load(); }
    catch (reason) { setError(errorMessage(reason)); }
  }
  async function remove(id: string) { if (!window.confirm('Delete this Learning Mode note?')) return; try { await window.wizestream.backend.invoke('library.learning.delete', { id }); await load(); } catch (reason) { setError(errorMessage(reason)); } }
  return <Stack spacing={3}><PanelHeader title="Learning notes" description={currentStream ? `Current stream: ${currentStream.title}` : 'Select a stream in Discover to create a timestamped note.'} action={<Button startIcon={<NoteAddRounded />} variant="contained" disabled={!currentStream} onClick={() => showEditor()}>Add note</Button>} />{error && <Alert severity="error">{error}</Alert>}{notes.length === 0 ? <LibraryEmpty text="No Learning Mode notes yet." /> : <Box className="note-grid">{notes.map((item) => <Card key={item.id} variant="outlined"><CardActionArea onClick={() => void onOpen(item.url)}><CardContent><Stack direction="row" sx={{ alignItems: 'start', gap: 1 }}><Box sx={{ flexGrow: 1 }}><Typography variant="overline">{formatTimestamp(item.positionSeconds)}</Typography><Typography variant="h6" className="two-lines">{item.title}</Typography></Box><IconButton aria-label="Edit note" onClick={(event) => { event.stopPropagation(); showEditor(item); }}><EditRounded /></IconButton><IconButton aria-label="Delete note" onClick={(event) => { event.stopPropagation(); void remove(item.id); }}><DeleteOutlineRounded /></IconButton></Stack><Typography sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>{item.note}</Typography><Typography color="text.secondary" variant="caption" sx={{ display: 'block', mt: 2 }}>Updated {new Date(item.updatedAt).toLocaleString()}</Typography></CardContent></CardActionArea></Card>)}</Box>}
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm"><DialogTitle>{editing ? 'Edit note' : 'Add timestamped note'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField label="Timestamp (seconds)" type="number" value={position} onChange={(event) => setPosition(event.target.value)} slotProps={{ htmlInput: { min: 0 } }} /><TextField label="Note" value={note} onChange={(event) => setNote(event.target.value)} multiline minRows={5} />{error && <Alert severity="error">{error}</Alert>}</Stack></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" disabled={!note.trim() || !Number.isFinite(Number(position)) || Number(position) < 0} onClick={() => void save()}>Save</Button></DialogActions></Dialog>
  </Stack>;
}

function DownloadsPanel({ currentStream }: { currentStream?: StreamDetails }) {
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [choice, setChoice] = useState('');
  const [audioChoice, setAudioChoice] = useState('');
  const [error, setError] = useState<string>();
  const options = useMemo(() => currentStream ? [
    ...currentStream.videoStreams.map((stream, index) => ({ key: `video:${index}`, label: `Video · ${videoLabel(stream)}`, stream, kind: 'video' as const })),
    ...currentStream.audioStreams.map((stream, index) => ({ key: `audio:${index}`, label: `Audio · ${audioLabel(stream)}`, stream, kind: 'audio' as const })),
    ...currentStream.subtitles.map((stream, index) => ({ key: `caption:${index}`, label: `Caption · ${subtitleLabel(stream)}`, stream, kind: 'caption' as const })),
  ] : [], [currentStream]);
  const selectedOption = options.find((value) => value.key === choice);

  useEffect(() => {
    void window.wizestream.downloads.list().then(setJobs).catch((reason: unknown) => setError(errorMessage(reason)));
    return window.wizestream.downloads.onChanged(setJobs);
  }, []);
  useEffect(() => { setChoice(options[0]?.key ?? ''); }, [options]);
  useEffect(() => { setAudioChoice(currentStream?.audioStreams[0] ? '0' : ''); }, [currentStream]);

  async function start() {
    const option = options.find((value) => value.key === choice);
    if (!option || !currentStream) return;
    try {
      const source = downloadSource(option.stream, option.kind);
      if (option.kind === 'video' && 'videoOnly' in option.stream && option.stream.videoOnly) {
        const audio = currentStream.audioStreams[Number(audioChoice)];
        if (!audio) throw new Error('Select an audio track for this adaptive video');
        await window.wizestream.downloads.start({ sourceUrl: currentStream.url,
          title: `${currentStream.name} - ${option.label}`, video: source,
          audio: downloadSource(audio, 'audio') });
      } else {
        await window.wizestream.downloads.start({ sourceUrl: currentStream.url,
          title: `${currentStream.name} - ${option.label}`,
          ...(option.kind === 'video' ? { video: source }
            : option.kind === 'audio' ? { audio: source } : { caption: source }) });
      }
    } catch (reason) { setError(errorMessage(reason)); }
  }

  async function action(operation: 'pause' | 'resume' | 'cancel' | 'show', id: string) {
    try { await window.wizestream.downloads[operation](id); }
    catch (reason) { setError(errorMessage(reason)); }
  }

  return <Stack spacing={3}><PanelHeader title="Downloads" description="Resumable media and caption downloads are stored in your WizeStream Downloads folder."
    action={<Button startIcon={<FolderOpenRounded />} variant="outlined" onClick={() => void window.wizestream.downloads.openFolder()}>Open folder</Button>} />
    {error && <Alert severity="error">{error}</Alert>}
    <Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Download current stream</Typography>
      {!currentStream ? <Typography color="text.secondary" sx={{ mt: 1 }}>Select a stream in Discover first.</Typography>
        : <Stack direction="row" sx={{ mt: 2, gap: 2, alignItems: 'center' }}><TextField select fullWidth label="Media or caption" value={choice} onChange={(event) => setChoice(event.target.value)}>
          {options.map((option) => <MenuItem key={option.key} value={option.key}>{option.label}</MenuItem>)}
        </TextField>{selectedOption?.kind === 'video' && 'videoOnly' in selectedOption.stream && selectedOption.stream.videoOnly && <TextField select fullWidth label="Audio track" value={audioChoice} onChange={(event) => setAudioChoice(event.target.value)}>
          {currentStream.audioStreams.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{audioLabel(stream)}</MenuItem>)}
        </TextField>}<Button startIcon={<DownloadRounded />} variant="contained" disabled={!choice || (selectedOption?.kind === 'video' && 'videoOnly' in selectedOption.stream && selectedOption.stream.videoOnly && !audioChoice)} onClick={() => void start()}>Download</Button></Stack>}
      {selectedOption?.kind === 'video' && 'videoOnly' in selectedOption.stream && selectedOption.stream.videoOnly && <Alert severity="info" sx={{ mt: 2 }}>WizeStream downloads both selected tracks, combines them without re-encoding, and validates the final file.</Alert>}
    </CardContent></Card>
    {jobs.length === 0 ? <LibraryEmpty text="No desktop downloads yet." /> : <Card variant="outlined"><List disablePadding>{jobs.map((job, index) => {
      const progress = job.totalBytes ? Math.min(100, job.bytesDownloaded / job.totalBytes * 100) : undefined;
      const pausable = ['downloading', 'queued', 'muxing', 'validating'].includes(job.state);
      return <Box key={job.id}>{index > 0 && <Divider />}<ListItem secondaryAction={<Stack direction="row">
        {pausable && <Tooltip title="Pause"><IconButton onClick={() => void action('pause', job.id)}><PauseRounded /></IconButton></Tooltip>}
        {(job.state === 'paused' || job.state === 'failed') && <Tooltip title="Resume"><IconButton onClick={() => void action('resume', job.id)}><ReplayRounded /></IconButton></Tooltip>}
        {job.state === 'completed' && <Tooltip title="Show file"><IconButton onClick={() => void action('show', job.id)}><FolderOpenRounded /></IconButton></Tooltip>}
        {!['completed', 'cancelled'].includes(job.state) && <Tooltip title="Cancel"><IconButton onClick={() => void action('cancel', job.id)}><DeleteOutlineRounded /></IconButton></Tooltip>}
      </Stack>}><ListItemIcon><DownloadRounded /></ListItemIcon><ListItemText primary={job.title} secondary={<Box component="span" sx={{ display: 'block', pr: 12 }}>
        <Typography component="span" variant="body2" color={job.state === 'failed' ? 'error' : 'text.secondary'}>{downloadStageLabel(job)} · {formatBytes(job.bytesDownloaded)}{job.totalBytes ? ` of ${formatBytes(job.totalBytes)}` : ''}{job.outputContainer ? ` · ${job.outputContainer.toUpperCase()}` : ''}{job.error ? ` · ${job.error}` : ''}</Typography>
        {(job.state === 'downloading' || job.state === 'queued') && <LinearProgress variant={progress === undefined ? 'indeterminate' : 'determinate'} value={progress} sx={{ mt: 1 }} />}
      </Box>} /></ListItem></Box>;
    })}</List></Card>}
  </Stack>;
}

const syncCategoryLabels: Record<string, string> = {
  subscriptions: 'Subscriptions', playlists: 'Playlists', watchHistory: 'Watch history',
  searchHistory: 'Search history', learningNotes: 'Learning notes', feedGroups: 'Feed groups',
  homeTabs: 'Home tabs', channelProfiles: 'Channel profiles', filters: 'Filters',
  settings: 'Portable settings', completedDownloads: 'Completed download metadata',
};

function SyncPanel({ sync, onRefresh }: { sync?: SyncStatus; onRefresh(): void }) {
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [pairingCode, setPairingCode] = useState('');
  const [invitation, setInvitation] = useState('');
  const [busy, setBusy] = useState(false);
  const [panelError, setPanelError] = useState<string>();
  const [result, setResult] = useState<SyncRunResult>();
  const [automaticDraft, setAutomaticDraft] = useState<AutomaticSyncPolicy>();
  const [runs, setRuns] = useState<SyncRunLog[]>([]);
  useEffect(() => { if (sync && selectedCategories.length === 0) setSelectedCategories(sync.categories); }, [sync, selectedCategories.length]);
  useEffect(() => {
    if (!sync) return;
    const policy = sync.automaticPolicy;
    setAutomaticDraft((current) => current && current.updatedAtEpochMillis === policy.updatedAtEpochMillis
      ? current
      : { ...policy, peerIds: policy.updatedAtEpochMillis === 0 && policy.peerIds.length === 0
        ? sync.trustedPeers.map((peer) => peer.peerId) : policy.peerIds });
  }, [sync?.automaticPolicy.updatedAtEpochMillis, sync?.trustedPeers.length]);
  useEffect(() => {
    const refresh = () => {
      onRefresh();
      void window.wizestream.backend.invoke<SyncRunLog[]>('sync.runs.list', { limit: 20 }).then(setRuns);
    };
    refresh();
    const timer = window.setInterval(refresh, 15_000);
    return () => window.clearInterval(timer);
  }, []);
  async function createInvitation() { setBusy(true); setPanelError(undefined); try { const value = await window.wizestream.backend.invoke<{ pairingCode: string }>('sync.invitation'); setInvitation(value.pairingCode); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  async function pairDevice() { if (!pairingCode.trim()) return; setBusy(true); setPanelError(undefined); try { await window.wizestream.backend.invoke('sync.pair', { pairingCode: pairingCode.trim() }); setPairingCode(''); onRefresh(); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  async function synchronize() { if (selectedCategories.length === 0) return; setBusy(true); setPanelError(undefined); setResult(undefined); try { setResult(await window.wizestream.backend.invoke<SyncRunResult>('sync.run', { categories: selectedCategories })); onRefresh(); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  async function saveAutomaticPolicy() {
    if (!automaticDraft) return;
    setBusy(true); setPanelError(undefined);
    try {
      await window.wizestream.backend.invoke<AutomaticSyncPolicy>('sync.policy.update', {
        enabled: automaticDraft.enabled,
        intervalMinutes: automaticDraft.intervalMinutes,
        categories: automaticDraft.categories,
        peerIds: automaticDraft.peerIds,
      });
      onRefresh();
    } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); }
  }
  if (!sync || !automaticDraft) return <CircularProgress />;
  const automaticInvalid = automaticDraft.enabled
    && (automaticDraft.categories.length === 0 || automaticDraft.peerIds.length === 0);
  return <Stack spacing={3}>
    <Box><Typography variant="h4">Trusted devices</Typography><Typography color="text.secondary">Encrypted local pairing and synchronization use WizeStream protocol v1.</Typography></Box>
    {panelError && <Alert severity="error">{panelError}</Alert>}
    <Card variant="outlined"><CardContent sx={{ p: 4 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}><Box><Typography variant="overline">Desktop Peer ID</Typography><Typography className="mono">{sync.peerId}</Typography></Box><Button onClick={onRefresh}>Refresh</Button></Stack>
      <Divider sx={{ my: 3 }} />
      {sync.trustedPeers.length === 0 ? <Typography color="text.secondary">No trusted devices yet. Generate a code on either device and enter it on the other.</Typography> : sync.trustedPeers.map((peer) => <Box key={peer.peerId} sx={{ py: 1 }}><Typography sx={{ fontWeight: 650 }}>{peer.deviceName}</Typography><Typography className="mono" color="text.secondary">{peer.peerId}</Typography>{peer.lastSyncError && <Typography color="error" variant="body2">{peer.lastSyncError}</Typography>}{peer.automaticRetry?.nextRetryAtEpochMillis && <Typography color="warning.main" variant="body2">Automatic retry {new Date(peer.automaticRetry.nextRetryAtEpochMillis).toLocaleString()}</Typography>}</Box>)}
    </CardContent></Card>
    <Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Pair a device</Typography><Stack spacing={2} sx={{ mt: 2 }}><Button variant="outlined" disabled={busy} onClick={() => void createInvitation()}>Generate pairing code</Button>{invitation && <TextField label="This desktop's one-time pairing code" value={invitation} multiline minRows={3} slotProps={{ input: { readOnly: true } }} />}<TextField label="Code from another WizeStream device" value={pairingCode} multiline minRows={3} onChange={(event) => setPairingCode(event.target.value)} /><Button variant="contained" disabled={busy || !pairingCode.trim()} onClick={() => void pairDevice()}>Pair device</Button></Stack></CardContent></Card>
    <Card variant="outlined"><CardContent sx={{ p: 4 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}><Box><Typography variant="h6">Automatic synchronization</Typography><Typography color="text.secondary">Runs while WizeStream Desktop is open, on a private local network only.</Typography></Box><FormControlLabel label={automaticDraft.enabled ? 'Enabled' : 'Disabled'} control={<Switch checked={automaticDraft.enabled} onChange={(event) => setAutomaticDraft({ ...automaticDraft, enabled: event.target.checked })} />} /></Stack>
      <Stack direction={{ xs: 'column', md: 'row' }} sx={{ mt: 3, gap: 3, alignItems: 'start' }}>
        <TextField select label="Interval" value={automaticDraft.intervalMinutes} onChange={(event) => setAutomaticDraft({ ...automaticDraft, intervalMinutes: Number(event.target.value) })} sx={{ minWidth: 220 }}>{[15, 30, 60, 180, 360, 720, 1440].map((minutes) => <MenuItem key={minutes} value={minutes}>{minutes < 60 ? `${minutes} minutes` : minutes === 60 ? '1 hour' : `${minutes / 60} hours`}</MenuItem>)}</TextField>
        <Alert severity={sync.networkEligibility.eligible ? 'success' : 'warning'} sx={{ flexGrow: 1 }}>{sync.networkEligibility.eligible ? 'Private local network available' : 'Offline or no private local network; automatic runs will wait'}</Alert>
      </Stack>
      <Typography variant="subtitle1" sx={{ mt: 3, fontWeight: 650 }}>Categories</Typography>
      <Box className="sync-category-grid">{sync.categories.map((category) => <FormControlLabel key={category} control={<Checkbox checked={automaticDraft.categories.includes(category)} onChange={(event) => setAutomaticDraft({ ...automaticDraft, categories: event.target.checked ? [...automaticDraft.categories, category] : automaticDraft.categories.filter((value) => value !== category) })} />} label={syncCategoryLabels[category] ?? category} />)}</Box>
      <Typography variant="subtitle1" sx={{ mt: 3, fontWeight: 650 }}>Trusted devices</Typography>
      {sync.trustedPeers.length === 0 ? <Typography color="text.secondary">Pair a trusted device before enabling automatic synchronization.</Typography> : sync.trustedPeers.map((peer) => <FormControlLabel key={peer.peerId} sx={{ display: 'flex' }} control={<Checkbox checked={automaticDraft.peerIds.includes(peer.peerId)} onChange={(event) => setAutomaticDraft({ ...automaticDraft, peerIds: event.target.checked ? [...automaticDraft.peerIds, peer.peerId] : automaticDraft.peerIds.filter((value) => value !== peer.peerId) })} />} label={peer.deviceName} />)}
      {automaticInvalid && <Alert severity="warning" sx={{ mt: 2 }}>Select at least one category and trusted device before enabling automatic synchronization.</Alert>}
      <Stack direction="row" sx={{ mt: 3, gap: 2, alignItems: 'center', flexWrap: 'wrap' }}><Button variant="contained" disabled={busy || automaticInvalid} onClick={() => void saveAutomaticPolicy()}>Save automatic policy</Button>{sync.activeRun.running && <Chip color="info" label={`${sync.activeRun.trigger} synchronization running`} />}{sync.automaticSchedule.nextRunAtEpochMillis && <Typography color="text.secondary">Next regular run: {new Date(sync.automaticSchedule.nextRunAtEpochMillis).toLocaleString()}</Typography>}</Stack>
    </CardContent></Card>
    <Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Sync selected now</Typography><Typography color="text.secondary">Manual synchronization remains available even when automatic synchronization is disabled.</Typography><Box className="sync-category-grid" sx={{ my: 2 }}>{sync.categories.map((category) => <FormControlLabel key={category} control={<Checkbox checked={selectedCategories.includes(category)} onChange={(event) => setSelectedCategories((current) => event.target.checked ? [...current, category] : current.filter((value) => value !== category))} />} label={syncCategoryLabels[category] ?? category} />)}</Box><Button variant="contained" disabled={busy || sync.activeRun.running || sync.trustedPeers.length === 0 || selectedCategories.length === 0} onClick={() => void synchronize()}>{busy ? 'Working…' : 'Sync selected'}</Button>{result && <Alert severity={result.failed === 0 ? 'success' : 'warning'} sx={{ mt: 2 }}>Synchronization finished: {result.succeeded} device(s) succeeded, {result.failed} failed.</Alert>}</CardContent></Card>
    <Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Recent synchronization activity</Typography>{runs.length === 0 ? <Typography color="text.secondary" sx={{ mt: 1 }}>No synchronization attempts recorded yet.</Typography> : <List disablePadding sx={{ mt: 2 }}>{runs.map((run, index) => <Box key={run.runId}>{index > 0 && <Divider />}<ListItem><ListItemText primary={`${run.trigger === 'automatic' ? 'Automatic' : 'Manual'} · ${run.outcome.replaceAll('_', ' ')}`} secondary={`${new Date(run.startedAtEpochMillis).toLocaleString()} · ${run.succeeded} succeeded, ${run.failed} failed${run.error ? ` · ${run.error}` : ''}`} /></ListItem></Box>)}</List>}</CardContent></Card>
  </Stack>;
}

function PanelHeader({ title, description, action }: { title: string; description: string; action: React.ReactNode }) {
  return <Stack direction="row" sx={{ alignItems: 'center', gap: 2 }}><Box sx={{ flexGrow: 1 }}><Typography variant="h4">{title}</Typography><Typography color="text.secondary">{description}</Typography></Box>{action}</Stack>;
}

function LibraryEmpty({ text }: { text: string }) {
  return <Card variant="outlined"><CardContent sx={{ p: 6, textAlign: 'center' }}><Typography color="text.secondary">{text}</Typography></CardContent></Card>;
}

function detailsToLibraryStream(value: StreamDetails): LibraryStream {
  return { serviceId: value.serviceId, url: value.url, title: value.name, duration: value.duration,
    streamType: value.streamType, uploader: value.uploaderName, thumbnailUrl: value.thumbnailUrl };
}

function formatTimestamp(seconds: number): string {
  const value = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(value / 60);
  return `${Math.floor(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}`;
}

function videoLabel(stream: StreamVariant): string {
  const parts = [stream.resolution || 'Video', stream.format, stream.codec];
  if (stream.videoOnly) parts.push('video only');
  if (stream.audioTrackName || stream.audioLocale) parts.push(stream.audioTrackName || stream.audioLocale);
  return parts.filter(Boolean).join(' · ');
}

function audioLabel(stream: StreamVariant): string {
  const type = stream.audioTrackType ? stream.audioTrackType.toLowerCase() : undefined;
  const bitrate = stream.bitrate && stream.bitrate > 0 ? `${stream.bitrate} kbps` : undefined;
  return [stream.audioTrackName || stream.audioLocale || 'Audio', type, bitrate, stream.format]
    .filter(Boolean).join(' · ');
}

function subtitleLabel(stream: SubtitleVariant): string {
  return [stream.displayLanguage || stream.languageTag, stream.autoGenerated ? 'auto-generated' : undefined, stream.format]
    .filter(Boolean).join(' · ');
}

function mimeType(format: string | undefined, kind: 'video' | 'audio' | 'caption'): string {
  const normalized = format?.toUpperCase();
  if (kind === 'caption') return normalized === 'SRT' ? 'application/x-subrip' : normalized === 'TTML' ? 'application/ttml+xml' : 'text/vtt';
  if (kind === 'audio') return normalized?.includes('WEBM') ? 'audio/webm' : normalized === 'MP3' ? 'audio/mpeg' : normalized === 'OGG' || normalized === 'OPUS' ? 'audio/ogg' : 'audio/mp4';
  return normalized?.includes('WEBM') ? 'video/webm' : 'video/mp4';
}

function playerTrack(stream: StreamVariant | SubtitleVariant, title: string) {
  return { url: stream.url, title, language: 'languageTag' in stream ? stream.languageTag : stream.audioLocale };
}

function downloadSource(stream: StreamVariant | SubtitleVariant, kind: 'video' | 'audio' | 'caption'): DownloadSource {
  return {
    url: stream.url, kind, id: stream.id, format: stream.format,
    deliveryMethod: stream.deliveryMethod, mimeType: mimeType(stream.format, kind),
    ...('resolution' in stream ? { resolution: stream.resolution, codec: stream.codec,
      audioTrackId: stream.audioTrackId, videoOnly: stream.videoOnly } : {}),
  };
}

function downloadStageLabel(job: DownloadJob): string {
  const labels: Record<DownloadJob['stage'], string> = {
    queued: 'Queued', downloading_video: 'Downloading video', downloading_audio: 'Downloading audio',
    downloading_caption: 'Downloading caption', muxing: 'Combining tracks', validating: 'Validating output',
    paused: 'Paused', completed: 'Completed', failed: 'Failed', cancelled: 'Cancelled',
  };
  return labels[job.stage];
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KiB', 'MiB', 'GiB', 'TiB'];
  let value = bytes / 1024;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) { value /= 1024; index += 1; }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${units[index]}`;
}

function errorMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : String(reason);
}
