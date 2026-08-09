import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  Alert, AppBar, Avatar, Box, Button, Card, CardActionArea, CardContent, Chip,
  CircularProgress, Container, Divider, IconButton, InputAdornment, List,
  ListItemButton, ListItemIcon, ListItemText, MenuItem, Stack, TextField,
  Toolbar, Tooltip, Typography,
} from '@mui/material';
import SearchRounded from '@mui/icons-material/SearchRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import SubscriptionsRounded from '@mui/icons-material/SubscriptionsRounded';
import PlaylistPlayRounded from '@mui/icons-material/PlaylistPlayRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import StopRounded from '@mui/icons-material/StopRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import type { SearchItem, ServiceSummary, StreamDetails, SyncStatus } from '../shared/contracts';

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

  async function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(undefined);
    setSelected(undefined);
    try {
      setResults(await window.wizestream.backend.invoke<SearchItem[]>('search', {
        serviceId, query: query.trim(),
      }));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }

  async function openResult(item: SearchItem) {
    if (item.type !== 'STREAM') return;
    setLoading(true);
    setError(undefined);
    try {
      setSelected(await window.wizestream.backend.invoke<StreamDetails>('stream.resolve', { url: item.url }));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoading(false);
    }
  }

  async function playSelected() {
    if (!selectedPlaybackUrl || !selected) return;
    try {
      await window.wizestream.player.play(selectedPlaybackUrl, selected.name);
      setMpv(await window.wizestream.player.status());
    } catch (reason) {
      setError(errorMessage(reason));
    }
  }

  return (
    <Box className="app-shell">
      <Box component="nav" className="navigation-rail">
        <Avatar sx={{ width: 48, height: 48, mb: 2, bgcolor: 'primary.main' }}>W</Avatar>
        <List sx={{ width: '100%' }}>
          {navigation.map((item) => (
            <ListItemButton key={item.id} selected={section === item.id} onClick={() => setSection(item.id)}>
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Box>
      <Box component="main" className="content-column">
        <AppBar position="sticky" color="transparent" elevation={0}>
          <Toolbar sx={{ gap: 2 }}>
            <Typography variant="h6" sx={{ flexGrow: 1 }}>WizeStream Desktop</Typography>
            <Chip color={mpv?.available ? 'success' : 'default'} label={mpv?.available ? 'mpv ready' : 'mpv not installed'} />
            {mpv?.running && (
              <Tooltip title="Stop player">
                <IconButton onClick={() => void window.wizestream.player.stop().then(() => setMpv({ ...mpv, running: false }))}>
                  <StopRounded />
                </IconButton>
              </Tooltip>
            )}
          </Toolbar>
        </AppBar>
        <Container maxWidth="xl" sx={{ py: 4 }}>
          {section === 'discover' ? (
            <>
              <Stack spacing={1} sx={{ mb: 4 }}>
                <Typography variant="h4">Watch without the noise</Typography>
                <Typography color="text.secondary">Search through the same WizeStreamExtractor used by Android.</Typography>
              </Stack>
              <Box component="form" onSubmit={submitSearch} className="search-row">
                <TextField select label="Service" value={serviceId} onChange={(event) => setServiceId(Number(event.target.value))} sx={{ minWidth: 190 }}>
                  {services.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}
                </TextField>
                <TextField fullWidth label="Search" value={query} onChange={(event) => setQuery(event.target.value)}
                  slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }} />
                <Button type="submit" variant="contained" size="large" disabled={loading}>Search</Button>
              </Box>
              {error && <Alert severity="error" sx={{ mt: 3 }}>{error}</Alert>}
              {loading && <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>}
              {selected && (
                <Card sx={{ mt: 4, overflow: 'hidden' }}>
                  <Box className="details-card">
                    {selected.thumbnailUrl && <Box component="img" src={selected.thumbnailUrl} alt="" className="details-thumbnail" />}
                    <CardContent sx={{ p: 4 }}>
                      <Chip label={selected.streamType} size="small" />
                      <Typography variant="h4" sx={{ mt: 2 }}>{selected.name}</Typography>
                      <Typography color="text.secondary" sx={{ mt: 1 }}>{selected.uploaderName}</Typography>
                      <Stack direction="row" spacing={1} sx={{ mt: 3, flexWrap: 'wrap' }}>
                        <Chip label={`${selected.videoStreams.length} video variants`} />
                        <Chip label={`${selected.audioStreams.length} audio variants`} />
                        <Chip label={`${Math.round(selected.duration / 60)} min`} />
                      </Stack>
                      <Button startIcon={<PlayArrowRounded />} variant="contained" size="large" sx={{ mt: 4 }}
                        disabled={!selectedPlaybackUrl} onClick={() => void playSelected()}>Play in mpv</Button>
                    </CardContent>
                  </Box>
                </Card>
              )}
              {!loading && !selected && results.length > 0 && (
                <Box className="result-grid" sx={{ mt: 4 }}>
                  {results.map((item) => (
                    <Card key={`${item.type}:${item.url}`} variant="outlined">
                      <CardActionArea onClick={() => void openResult(item)} disabled={item.type !== 'STREAM'} sx={{ height: '100%' }}>
                        {item.thumbnailUrl && <Box component="img" src={item.thumbnailUrl} alt="" className="result-thumbnail" />}
                        <CardContent>
                          <Chip label={item.type.toLowerCase()} size="small" />
                          <Typography variant="h6" sx={{ mt: 1 }} className="two-lines">{item.name}</Typography>
                          <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>{item.uploaderName}</Typography>
                        </CardContent>
                      </CardActionArea>
                    </Card>
                  ))}
                </Box>
              )}
            </>
          ) : section === 'sync' ? (
            <SyncPanel sync={sync} onRefresh={() => void window.wizestream.backend.invoke<SyncStatus>('sync.status').then(setSync)} />
          ) : (
            <EmptySection title={navigation.find((item) => item.id === section)?.label ?? section} />
          )}
        </Container>
      </Box>
    </Box>
  );
}

function SyncPanel({ sync, onRefresh }: { sync?: SyncStatus; onRefresh(): void }) {
  return <Stack spacing={3}>
    <Box><Typography variant="h4">Trusted devices</Typography><Typography color="text.secondary">Encrypted local pairing uses WizeStream sync protocol v1.</Typography></Box>
    {!sync ? <CircularProgress /> : <Card variant="outlined"><CardContent sx={{ p: 4 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between' }}><Box><Typography variant="overline">Desktop Peer ID</Typography><Typography className="mono">{sync.peerId}</Typography></Box><Button onClick={onRefresh}>Refresh</Button></Stack>
      <Divider sx={{ my: 3 }} />
      {sync.trustedPeers.length === 0 ? <Typography color="text.secondary">No trusted devices yet. Pairing UI is enabled; category data adapters follow in Phase 2.</Typography>
        : sync.trustedPeers.map((peer) => <Box key={peer.peerId} sx={{ py: 1 }}><Typography sx={{ fontWeight: 650 }}>{peer.deviceName}</Typography><Typography className="mono" color="text.secondary">{peer.peerId}</Typography></Box>)}
      <Alert severity="info" sx={{ mt: 3 }}>Pairing transport is implemented. Subscription, playlist, history, Learning Mode and settings adapters are intentionally gated until the desktop schemas pass compatibility fixtures.</Alert>
    </CardContent></Card>}
  </Stack>;
}

function EmptySection({ title }: { title: string }) {
  return <Card variant="outlined"><CardContent sx={{ p: 6 }}><Typography variant="h4">{title}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>The SQLite schema and navigation foundation are ready. Feature data adapters are part of the next desktop milestone.</Typography></CardContent></Card>;
}

function errorMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : String(reason);
}
