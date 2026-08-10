import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  Alert, AppBar, Avatar, Box, Button, Card, CardActionArea, CardContent, Chip,
  Checkbox, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControlLabel, IconButton, InputAdornment, List,
  ListItem, ListItemAvatar, ListItemButton, ListItemIcon, ListItemText, MenuItem,
  Stack, TextField, Toolbar, Tooltip, Typography,
} from '@mui/material';
import AddRounded from '@mui/icons-material/AddRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import NoteAddRounded from '@mui/icons-material/NoteAddRounded';
import PlaylistAddRounded from '@mui/icons-material/PlaylistAddRounded';
import PlaylistPlayRounded from '@mui/icons-material/PlaylistPlayRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import StopRounded from '@mui/icons-material/StopRounded';
import SubscriptionsRounded from '@mui/icons-material/SubscriptionsRounded';
import type {
  HistoryItem, LearningNote, LibraryStream, PlaylistItem, PlaylistSummary, SearchHistoryItem, SearchItem,
  ServiceSummary, StreamDetails, SubscriptionItem, SyncRunResult, SyncStatus,
} from '../shared/contracts';

type Section = 'discover' | 'subscriptions' | 'playlists' | 'history' | 'learning' | 'sync';

const navigation: Array<{ id: Section; label: string; icon: React.ReactNode }> = [
  { id: 'discover', label: 'Discover', icon: <HomeRounded /> },
  { id: 'subscriptions', label: 'Subscriptions', icon: <SubscriptionsRounded /> },
  { id: 'playlists', label: 'Playlists', icon: <PlaylistPlayRounded /> },
  { id: 'history', label: 'History', icon: <HistoryRounded /> },
  { id: 'learning', label: 'Learning', icon: <SchoolRounded /> },
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
  const [mpv, setMpv] = useState<{ available: boolean; running: boolean }>();

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

  const selectedPlaybackUrl = useMemo(() => selected?.hlsUrl
    ?? selected?.dashMpdUrl
    ?? selected?.videoStreams.find((stream) => !stream.videoOnly)?.url
    ?? selected?.audioStreams[0]?.url, [selected]);
  const selectedLibraryStream = useMemo(() => selected ? detailsToLibraryStream(selected) : undefined, [selected]);

  async function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true); setError(undefined); setSelected(undefined);
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
      setSection('discover');
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }, []);

  async function openResult(item: SearchItem) {
    if (item.type === 'STREAM') await resolveStream(item.url);
  }

  async function playSelected() {
    if (!selectedPlaybackUrl || !selected || !selectedLibraryStream) return;
    try {
      await window.wizestream.player.play(selectedPlaybackUrl, selected.name);
      await window.wizestream.backend.invoke('library.history.record', { ...selectedLibraryStream });
      setMpv(await window.wizestream.player.status());
    } catch (reason) { setError(errorMessage(reason)); }
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
            <Chip color={mpv?.available ? 'success' : 'default'} label={mpv?.available ? 'mpv ready' : 'mpv not installed'} />
            {mpv?.running && <Tooltip title="Stop player"><IconButton onClick={() => void window.wizestream.player.stop().then(() => setMpv({ ...mpv, running: false }))}><StopRounded /></IconButton></Tooltip>}
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
            {selected && <Card sx={{ mt: 4, overflow: 'hidden' }}><Box className="details-card">
              {selected.thumbnailUrl && <Box component="img" src={selected.thumbnailUrl} alt="" className="details-thumbnail" />}
              <CardContent sx={{ p: 4 }}><Chip label={selected.streamType} size="small" /><Typography variant="h4" sx={{ mt: 2 }}>{selected.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{selected.uploaderName}</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 3, flexWrap: 'wrap' }}><Chip label={`${selected.videoStreams.length} video variants`} /><Chip label={`${selected.audioStreams.length} audio variants`} /><Chip label={`${Math.round(selected.duration / 60)} min`} /></Stack>
                <Stack direction="row" spacing={1} sx={{ mt: 4, flexWrap: 'wrap' }}>
                  <Button startIcon={<PlayArrowRounded />} variant="contained" size="large" disabled={!selectedPlaybackUrl} onClick={() => void playSelected()}>Play in mpv</Button>
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
                  : <SyncPanel sync={sync} onRefresh={() => void window.wizestream.backend.invoke<SyncStatus>('sync.status').then(setSync)} />}
        </Container>
      </Box>
    </Box>
  );
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
  useEffect(() => { if (sync && selectedCategories.length === 0) setSelectedCategories(sync.categories); }, [sync, selectedCategories.length]);
  async function createInvitation() { setBusy(true); setPanelError(undefined); try { const value = await window.wizestream.backend.invoke<{ pairingCode: string }>('sync.invitation'); setInvitation(value.pairingCode); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  async function pairDevice() { if (!pairingCode.trim()) return; setBusy(true); setPanelError(undefined); try { await window.wizestream.backend.invoke('sync.pair', { pairingCode: pairingCode.trim() }); setPairingCode(''); onRefresh(); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  async function synchronize() { if (selectedCategories.length === 0) return; setBusy(true); setPanelError(undefined); setResult(undefined); try { setResult(await window.wizestream.backend.invoke<SyncRunResult>('sync.run', { categories: selectedCategories })); onRefresh(); } catch (reason) { setPanelError(errorMessage(reason)); } finally { setBusy(false); } }
  return <Stack spacing={3}><Box><Typography variant="h4">Trusted devices</Typography><Typography color="text.secondary">Encrypted local pairing and category synchronization use WizeStream protocol v1.</Typography></Box>{!sync ? <CircularProgress /> : <><Card variant="outlined"><CardContent sx={{ p: 4 }}><Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}><Box><Typography variant="overline">Desktop Peer ID</Typography><Typography className="mono">{sync.peerId}</Typography></Box><Button onClick={onRefresh}>Refresh</Button></Stack><Divider sx={{ my: 3 }} />{sync.trustedPeers.length === 0 ? <Typography color="text.secondary">No trusted devices yet. Generate a code on either device and enter it on the other.</Typography> : sync.trustedPeers.map((peer) => <Box key={peer.peerId} sx={{ py: 1 }}><Typography sx={{ fontWeight: 650 }}>{peer.deviceName}</Typography><Typography className="mono" color="text.secondary">{peer.peerId}</Typography>{peer.lastSyncError && <Typography color="error" variant="body2">{peer.lastSyncError}</Typography>}</Box>)}<Alert severity="success" sx={{ mt: 3 }}>Phase 3 library editors are enabled. Local changes are reconciled into the shared journal before synchronization.</Alert></CardContent></Card><Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Pair a device</Typography><Stack spacing={2} sx={{ mt: 2 }}><Button variant="outlined" disabled={busy} onClick={() => void createInvitation()}>Generate pairing code</Button>{invitation && <TextField label="This desktop's one-time pairing code" value={invitation} multiline minRows={3} slotProps={{ input: { readOnly: true } }} />}<TextField label="Code from another WizeStream device" value={pairingCode} multiline minRows={3} onChange={(event) => setPairingCode(event.target.value)} /><Button variant="contained" disabled={busy || !pairingCode.trim()} onClick={() => void pairDevice()}>Pair device</Button></Stack></CardContent></Card><Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Synchronize categories</Typography><Box className="sync-category-grid" sx={{ my: 2 }}>{sync.categories.map((category) => <FormControlLabel key={category} control={<Checkbox checked={selectedCategories.includes(category)} onChange={(event) => setSelectedCategories((current) => event.target.checked ? [...current, category] : current.filter((value) => value !== category))} />} label={syncCategoryLabels[category] ?? category} />)}</Box><Button variant="contained" disabled={busy || sync.trustedPeers.length === 0 || selectedCategories.length === 0} onClick={() => void synchronize()}>{busy ? 'Working…' : 'Sync selected'}</Button>{result && <Alert severity={result.failed === 0 ? 'success' : 'warning'} sx={{ mt: 2 }}>Synchronization finished: {result.succeeded} device(s) succeeded, {result.failed} failed.</Alert>}{panelError && <Alert severity="error" sx={{ mt: 2 }}>{panelError}</Alert>}</CardContent></Card></>}</Stack>;
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

function errorMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : String(reason);
}
