import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import {
  Alert, AppBar, Avatar, Box, Button, Card, CardActionArea, CardContent, Chip,
  Checkbox, CircularProgress, Container, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControlLabel, IconButton, InputAdornment, LinearProgress, List,
  ListItem, ListItemAvatar, ListItemButton, ListItemIcon, ListItemText, MenuItem,
  Radio, RadioGroup, Slider, Snackbar, Stack, Switch, Tab, Tabs, TextField, Toolbar, Tooltip, Typography,
} from '@mui/material';
import { useColorScheme } from '@mui/material/styles';
import { defineMpvVideoElement, type MpvVideoElement } from 'electron-mpv-video/renderer';
import { QRCodeSVG } from 'qrcode.react';
import AddRounded from '@mui/icons-material/AddRounded';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded';
import BedtimeRounded from '@mui/icons-material/BedtimeRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import DescriptionRounded from '@mui/icons-material/DescriptionRounded';
import DevicesRounded from '@mui/icons-material/DevicesRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import EqualizerRounded from '@mui/icons-material/EqualizerRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import FolderOpenRounded from '@mui/icons-material/FolderOpenRounded';
import FullscreenExitRounded from '@mui/icons-material/FullscreenExitRounded';
import FullscreenRounded from '@mui/icons-material/FullscreenRounded';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import HomeRounded from '@mui/icons-material/HomeRounded';
import InfoRounded from '@mui/icons-material/InfoRounded';
import NoteAddRounded from '@mui/icons-material/NoteAddRounded';
import PauseRounded from '@mui/icons-material/PauseRounded';
import PlaylistAddRounded from '@mui/icons-material/PlaylistAddRounded';
import PlaylistPlayRounded from '@mui/icons-material/PlaylistPlayRounded';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import ReplayRounded from '@mui/icons-material/ReplayRounded';
import QueuePlayNextRounded from '@mui/icons-material/QueuePlayNextRounded';
import QueueMusicRounded from '@mui/icons-material/QueueMusicRounded';
import SchoolRounded from '@mui/icons-material/SchoolRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import SettingsRounded from '@mui/icons-material/SettingsRounded';
import SpeedRounded from '@mui/icons-material/SpeedRounded';
import StopRounded from '@mui/icons-material/StopRounded';
import SkipNextRounded from '@mui/icons-material/SkipNextRounded';
import SkipPreviousRounded from '@mui/icons-material/SkipPreviousRounded';
import SubscriptionsRounded from '@mui/icons-material/SubscriptionsRounded';
import SystemUpdateAltRounded from '@mui/icons-material/SystemUpdateAltRounded';
import ThumbDownRounded from '@mui/icons-material/ThumbDownRounded';
import ThumbUpRounded from '@mui/icons-material/ThumbUpRounded';
import VolumeOffRounded from '@mui/icons-material/VolumeOffRounded';
import VolumeUpRounded from '@mui/icons-material/VolumeUpRounded';
import wizestreamLogo from '../../../assets/wizestream_logo_round.svg';
import { defaultDesktopSettings } from '../shared/contracts';
import type {
  ChannelDetails, CommentItem, DesktopSettings, DownloadJob, DownloadSource, EmbeddedPlayerRequest, EqualizerSettings,
  HistoryItem, LearningNote, LibraryStream, PlayerStatus, PlaylistItem, PlaylistSummary,
  PlaybackParameterSettings, PlaybackState, SearchHistoryItem, SearchItem, ServiceSummary, StreamDetails, StreamVariant, SubtitleVariant,
  SponsorBlockSegment, SponsorBlockSettings, StreamComments, SubscriptionFeed,
  AutomaticSyncPolicy, SubscriptionItem, SyncRunLog, SyncRunResult, SyncStatus,
  UpdateState,
} from '../shared/contracts';
import { loadSubscriptionFeedCache, saveSubscriptionFeedCache } from './feed-cache';
import { AboutPanel } from './AboutPanel';
import { SettingsPanel } from './SettingsPanel';
import {
  historyResumePosition, matchesFeedFilter, playbackKey, publishedAgeLabel, viewCountLabel,
  type FeedFilter,
} from './feed';
import { subscriberCountLabel } from './subscriber-count';
import {
  activeSponsorBlockSegment, sponsorBlockCategoryColor, sponsorBlockCategoryTitle,
  sponsorBlockSegmentKey, validSponsorBlockSegments,
} from './sponsor-block';
import {
  channelPlaybackProfile, preferredAudioIndex, preferredSubtitleIndex, preferredVideoIndex,
  updatedChannelProfile,
} from './stream-preferences';
import { EqualizerDialog } from './EqualizerDialog';
import { equalizerHeadroomMultiplier, equalizerPresetLabel } from './equalizer';
import { PlaybackParametersDialog } from './PlaybackParametersDialog';
import { formatPlaybackSpeed } from './playback-parameters';
import { resolvePlaybackSelection } from './playback-selection';
import { mediaNetworkProfile } from './media-network';
import {
  inactiveSleepTimer, sleepTimerFadeMultiplier, sleepTimerRemainingMillis, sleepTimerStatus,
  type SleepTimerState,
} from './sleep-timer';
import {
  adjacentQueueIndex, emptyPlaybackQueue, enqueue, loadPlaybackQueue, moveQueueItem,
  playNext, playNow, removeQueueItem, savePlaybackQueue, searchItemToLibraryStream,
  type PlaybackQueueState,
} from './playback-queue';
import { previewFrameAt } from './stream-preview';
import { PlaybackQueueDialog } from './PlaybackQueueDialog';
import { waitForMpvMediaReady } from './mpv-readiness';

defineMpvVideoElement();

type Section = 'discover' | 'subscriptions' | 'playlists' | 'history' | 'learning' | 'downloads' | 'sync' | 'settings' | 'about';

const navigation: Array<{ id: Section; label: string; icon: React.ReactNode }> = [
  { id: 'discover', label: "What's New", icon: <HomeRounded /> },
  { id: 'subscriptions', label: 'Subscriptions', icon: <SubscriptionsRounded /> },
  { id: 'playlists', label: 'Playlists', icon: <PlaylistPlayRounded /> },
  { id: 'history', label: 'History', icon: <HistoryRounded /> },
  { id: 'learning', label: 'Learning', icon: <SchoolRounded /> },
  { id: 'downloads', label: 'Downloads', icon: <DownloadRounded /> },
  { id: 'sync', label: 'Devices', icon: <DevicesRounded /> },
  { id: 'settings', label: 'Settings', icon: <SettingsRounded /> },
  { id: 'about', label: 'About & FAQ', icon: <InfoRounded /> },
];

const attemptedSubscriptionMetadata = new Set<string>();
const feedFilters: Array<{ id: Exclude<FeedFilter, 'none'>; label: string }> = [
  { id: 'unwatched', label: 'Unwatched' },
  { id: 'live', label: 'Live' },
  { id: 'shorts', label: 'Shorts' },
  { id: 'partially-watched', label: 'Partially watched' },
];
type SleepTimerChoice = '15' | '30' | '45' | '60' | 'end_current' | 'end_queue' | 'custom';

function loadPlayerVolume() {
  const stored = Number(window.localStorage.getItem('wizestream.desktop.player.volume.v1'));
  return Number.isFinite(stored) && stored >= 0 && stored <= 100 ? stored : 80;
}

export function App() {
  const { setMode } = useColorScheme();
  const [section, setSection] = useState<Section>('discover');
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [serviceId, setServiceId] = useState(0);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchItem[]>([]);
  const [searchActive, setSearchActive] = useState(false);
  const [subscriptionFeed, setSubscriptionFeed] = useState<SubscriptionFeed>(() =>
    loadSubscriptionFeedCache(window.localStorage) ?? {
      items: [], totalChannels: 0, failedChannels: 0, refreshedAt: 0,
    });
  const [playbackStates, setPlaybackStates] = useState<PlaybackState[]>([]);
  const [feedFilter, setFeedFilter] = useState<FeedFilter>('none');
  const [feedLoading, setFeedLoading] = useState(false);
  const [feedError, setFeedError] = useState<string>();
  const [selected, setSelected] = useState<StreamDetails>();
  const [sync, setSync] = useState<SyncStatus>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [mpv, setMpv] = useState<PlayerStatus>();
  const [videoChoice, setVideoChoice] = useState('auto');
  const [audioChoice, setAudioChoice] = useState('auto');
  const [subtitleChoice, setSubtitleChoice] = useState('none');
  const [resumePosition, setResumePosition] = useState(0);
  const [embeddedRequest, setEmbeddedRequest] = useState<EmbeddedPlayerRequest & { title: string; nonce: number }>();
  const [updateState, setUpdateState] = useState<UpdateState>();
  const [updateDialogOpen, setUpdateDialogOpen] = useState(false);
  const [settings, setSettings] = useState<DesktopSettings>(defaultDesktopSettings);
  const [queue, setQueue] = useState<PlaybackQueueState>(() => loadPlaybackQueue(window.localStorage));
  const [queueOpen, setQueueOpen] = useState(false);

  const loadSubscriptionFeed = useCallback(async (refresh = false) => {
    setFeedLoading(true); setFeedError(undefined);
    try {
      const states = await window.wizestream.backend.invoke<PlaybackState[]>('library.playback-state.list');
      setPlaybackStates(states);
      const feed = await window.wizestream.backend.invoke<SubscriptionFeed>('feed.subscriptions', { refresh });
      if (feed.totalChannels > 0 && feed.failedChannels >= feed.totalChannels && feed.items.length === 0) {
        setFeedError('The feed could not be refreshed. Showing the last cached videos.');
        return;
      }
      setSubscriptionFeed(feed);
      saveSubscriptionFeedCache(window.localStorage, feed);
    } catch (reason) { setFeedError(errorMessage(reason)); } finally { setFeedLoading(false); }
  }, []);

  useEffect(() => {
    Promise.all([
      window.wizestream.backend.invoke<ServiceSummary[]>('services.list'),
      window.wizestream.backend.invoke<SyncStatus>('sync.status'),
      window.wizestream.player.status(),
      window.wizestream.settings.get(),
    ]).then(([availableServices, syncStatus, playerStatus, savedSettings]) => {
      setServices(availableServices);
      setServiceId(availableServices.some((service) => service.id === savedSettings.defaultServiceId)
        ? savedSettings.defaultServiceId! : availableServices[0]?.id ?? 0);
      setSync(syncStatus);
      setMpv(playerStatus);
      setSettings(savedSettings);
    }).catch((reason: unknown) => setError(errorMessage(reason)));
  }, []);

  useEffect(() => { void loadSubscriptionFeed(); }, [loadSubscriptionFeed]);

  useEffect(() => { setMode(settings.theme); }, [setMode, settings.theme]);

  useEffect(() => { savePlaybackQueue(window.localStorage, queue); }, [queue]);

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

  const playbackSelection = useMemo(() => selected
    ? resolvePlaybackSelection(selected, videoChoice, audioChoice, subtitleChoice)
    : undefined, [selected, videoChoice, audioChoice, subtitleChoice]);
  const playbackVideo = playbackSelection?.video;
  const selectedPlaybackUrl = playbackSelection?.source;
  const selectedSubtitle = playbackSelection?.subtitle;
  const selectedLibraryStream = useMemo(() => selected ? detailsToLibraryStream(selected) : undefined, [selected]);
  const selectedPlaybackParameters = useMemo(() => {
    if (!selected) return settings.playbackParameters;
    const profile = channelPlaybackProfile(selected, settings);
    const speed = isLiveStream(selected) && !settings.rememberLiveStreamSpeed
      ? 1 : profile?.speed ?? settings.playbackParameters.speed;
    return { ...settings.playbackParameters, speed };
  }, [selected, settings]);
  const effectiveAudio = playbackSelection?.audio;
  const embeddedSelection = Boolean(mpv?.embeddedAvailable && selectedPlaybackUrl);
  const showPlayingInfo = Boolean(selected && (embeddedRequest || mpv?.running));
  const playbackByStream = useMemo(() => new Map(playbackStates.map((state) => [
    playbackKey(state.serviceId, state.url), state,
  ])), [playbackStates]);
  const feedItems = useMemo(() => subscriptionFeed.items.filter((item) => matchesFeedFilter(
    item, feedFilter, playbackByStream.get(playbackKey(item.serviceId, item.url)),
  )), [subscriptionFeed.items, feedFilter, playbackByStream]);
  const visibleResults = useMemo(() => searchActive
    ? results.filter((item) => item.type === 'STREAM' && matchesFeedFilter(
      item, feedFilter, playbackByStream.get(playbackKey(item.serviceId, item.url)),
    ))
    : feedItems,
  [searchActive, results, feedItems, feedFilter, playbackByStream]);

  async function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true); setError(undefined); setSelected(undefined); setEmbeddedRequest(undefined); setSearchActive(true);
    try {
      const searchQuery = query.trim();
      setResults(await window.wizestream.backend.invoke<SearchItem[]>('search', {
        serviceId, query: searchQuery,
      }));
      if (settings.enableSearchHistory) {
        await window.wizestream.backend.invoke('library.search-history.record', { serviceId, query: searchQuery });
      }
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }

  async function startResolvedPlayback(
    details: StreamDetails,
    choices: { video: string; audio: string; subtitle: string },
    startPosition = 0,
    ensureQueued = true,
  ) {
    const selection = resolvePlaybackSelection(details, choices.video, choices.audio, choices.subtitle);
    if (!selection.source) throw new Error('No playable stream is available');
    const libraryStream = detailsToLibraryStream(details);
    if (ensureQueued) setQueue((current) => playNow(current, libraryStream));
    if (mpv?.embeddedAvailable) {
      setEmbeddedRequest({
        source: selection.source, title: details.name, nonce: Date.now(), startSeconds: startPosition,
        ...mediaNetworkProfile(selection.source),
        audio: selection.audio ? playerTrack(selection.audio, audioLabel(selection.audio)) : undefined,
        subtitle: selection.subtitle ? playerTrack(selection.subtitle, subtitleLabel(selection.subtitle)) : undefined,
      });
    } else {
      await window.wizestream.player.play({
        url: selection.source, title: details.name, audioUrl: selection.audio?.url,
        subtitleUrl: selection.subtitle?.url, startSeconds: startPosition,
      });
    }
    if (settings.enableWatchHistory) {
      await window.wizestream.backend.invoke('library.history.record', { ...libraryStream });
    }
    setResumePosition(0);
    setMpv(await window.wizestream.player.status());
  }

  async function resolveStream(url: string, startPosition = 0, options?: { play?: boolean; queueIndex?: number }) {
    setLoading(true); setError(undefined);
    try {
      const details = await window.wizestream.backend.invoke<StreamDetails>('stream.resolve', {
        url, sponsorBlock: settings.sponsorBlock,
      });
      setSelected(details);
      const videoIndex = preferredVideoIndex(details, settings);
      const audioIndex = preferredAudioIndex(details, settings);
      const subtitleIndex = preferredSubtitleIndex(details, settings);
      const choices = {
        video: videoIndex === undefined ? 'auto' : String(videoIndex),
        audio: audioIndex === undefined ? 'auto' : String(audioIndex),
        subtitle: subtitleIndex === undefined ? 'none' : String(subtitleIndex),
      };
      setVideoChoice(choices.video); setAudioChoice(choices.audio); setSubtitleChoice(choices.subtitle);
      setResumePosition(Math.max(0, startPosition)); setEmbeddedRequest(undefined);
      if (options?.queueIndex !== undefined) {
        setQueue((current) => ({ ...current, currentIndex: options.queueIndex! }));
      }
      setSection('discover');
      if (options?.play) await startResolvedPlayback(details, choices, Math.max(0, startPosition),
        options.queueIndex === undefined);
    } catch (reason) { setError(errorMessage(reason)); } finally { setLoading(false); }
  }

  async function openResult(item: SearchItem) {
    if (item.type !== 'STREAM') return;
    if (settings.preferredOpenAction === 'enqueue') {
      setQueue((current) => enqueue(current, searchItemToLibraryStream(item)));
      setQueueOpen(true);
      return;
    }
    await resolveStream(item.url, 0, { play: settings.preferredOpenAction === 'play' });
  }

  async function playSelected() {
    if (!selectedPlaybackUrl || !selected || !selectedLibraryStream) return;
    try {
      await startResolvedPlayback(selected, { video: videoChoice, audio: audioChoice, subtitle: subtitleChoice }, resumePosition);
    } catch (reason) { setError(errorMessage(reason)); }
  }

  function changeVideoChoice(value: string, positionSeconds?: number) {
    setVideoChoice(value);
    if (selected) {
      const stream = value === 'auto' ? undefined : selected.videoStreams[Number(value)];
      const profiles = updatedChannelProfile(selected, settings, {
        videoResolution: stream?.resolution, videoFormat: stream?.format,
      });
      if (profiles) void saveSettings({ channelPlaybackProfiles: profiles });
    }
    if (!selected || !embeddedRequest || positionSeconds === undefined) return;
    const next = resolvePlaybackSelection(selected, value, audioChoice, subtitleChoice);
    if (!next.source) return;
    const source = next.source;
    setEmbeddedRequest((current) => current ? {
      ...current,
      source,
      ...mediaNetworkProfile(source),
      audio: next.audio ? playerTrack(next.audio, audioLabel(next.audio)) : undefined,
      subtitle: next.subtitle ? playerTrack(next.subtitle, subtitleLabel(next.subtitle)) : undefined,
      startSeconds: Math.max(0, positionSeconds),
      nonce: Date.now(),
    } : current);
  }

  function changeAudioChoice(value: string) {
    setAudioChoice(value);
    if (!selected) return;
    const stream = value === 'auto' ? undefined : selected.audioStreams[Number(value)];
    const profiles = updatedChannelProfile(selected, settings, {
      audioTrackId: stream?.audioTrackId, audioLocale: stream?.audioLocale,
    });
    if (profiles) void saveSettings({ channelPlaybackProfiles: profiles });
    const next = resolvePlaybackSelection(selected, videoChoice, value, subtitleChoice);
    setEmbeddedRequest((current) => current ? {
      ...current,
      audio: next.audio ? playerTrack(next.audio, audioLabel(next.audio)) : undefined,
    } : current);
  }

  function changeSubtitleChoice(value: string) {
    setSubtitleChoice(value);
    if (!selected) return;
    const subtitle = value === 'none' ? undefined : selected.subtitles[Number(value)];
    const profiles = updatedChannelProfile(selected, settings, {
      subtitleLanguageTag: subtitle?.languageTag ?? null,
    });
    if (profiles) void saveSettings({ channelPlaybackProfiles: profiles });
    const next = resolvePlaybackSelection(selected, videoChoice, audioChoice, value);
    setEmbeddedRequest((current) => current ? {
      ...current,
      subtitle: next.subtitle ? playerTrack(next.subtitle, subtitleLabel(next.subtitle)) : undefined,
    } : current);
  }

  async function stopAllPlayback() {
    window.dispatchEvent(new Event('wizestream-stop-player'));
    await window.wizestream.player.stop();
    setEmbeddedRequest(undefined);
    setMpv(await window.wizestream.player.status());
  }

  async function playQueueIndex(index: number) {
    const item = queue.items[index];
    if (!item) return;
    await resolveStream(item.url, 0, { play: true, queueIndex: index });
  }

  async function advanceQueue(direction: 'next' | 'previous') {
    const index = adjacentQueueIndex(queue, direction);
    if (index !== undefined) {
      await playQueueIndex(index);
      return;
    }
    if (direction !== 'next' || !settings.autoQueueRelated || !selected) return;
    const queued = new Set(queue.items.map((item) => `${item.serviceId}:${item.url}`));
    const related = selected.relatedItems.find((item) => item.type === 'STREAM'
      && !queued.has(`${item.serviceId}:${item.url}`));
    if (!related) return;
    const nextQueue = enqueue(queue, searchItemToLibraryStream(related));
    const nextIndex = nextQueue.items.length - 1;
    setQueue({ ...nextQueue, currentIndex: nextIndex });
    await resolveStream(related.url, 0, { play: true, queueIndex: nextIndex });
  }

  function addSelectedToQueue(mode: 'next' | 'end') {
    if (!selectedLibraryStream) return;
    setQueue((current) => mode === 'next' ? playNext(current, selectedLibraryStream)
      : enqueue(current, selectedLibraryStream));
    setQueueOpen(true);
  }

  function clearQueue() {
    if (settings.clearQueueConfirmation && !window.confirm('Clear the entire playback queue?')) return;
    setQueue({ ...emptyPlaybackQueue, repeatMode: queue.repeatMode, shuffle: queue.shuffle });
  }

  async function savePlaybackParameters(value: PlaybackParameterSettings) {
    let channelPlaybackProfiles = settings.channelPlaybackProfiles;
    if (selected && (!isLiveStream(selected) || settings.rememberLiveStreamSpeed)) {
      channelPlaybackProfiles = updatedChannelProfile(selected, settings, { speed: value.speed })
        ?? channelPlaybackProfiles;
    }
    await saveSettings({ playbackParameters: value, channelPlaybackProfiles });
  }

  async function openUpdates() {
    setUpdateDialogOpen(true);
    if (!updateState || ['idle', 'up-to-date', 'error'].includes(updateState.status)) {
      setUpdateState(await window.wizestream.updates.check());
    }
  }

  async function saveSettings(patch: Partial<DesktopSettings>) {
    const saved = await window.wizestream.settings.update(patch);
    setSettings(saved);
    if (patch.defaultServiceId !== undefined) {
      const preferred = services.find((service) => service.id === saved.defaultServiceId);
      if (preferred) setServiceId(preferred.id);
    }
  }

  async function resetSettings() {
    const saved = await window.wizestream.settings.reset();
    setSettings(saved);
    setServiceId(services[0]?.id ?? 0);
  }

  return (
    <Box className="app-shell">
      <Box component="nav" className="navigation-rail">
        <Stack direction="row" className="navigation-brand" sx={{ alignItems: 'center', gap: 1.25, mb: 2 }}>
          <Avatar src={wizestreamLogo} alt="WizeStream" sx={{ width: 48, height: 48, flexShrink: 0 }} />
          <Box className="navigation-brand-copy" sx={{ minWidth: 0 }}>
            <Typography variant="subtitle2" noWrap sx={{ fontWeight: 700 }}>WizeStream Desktop</Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              v{updateState?.currentVersion ?? '0.6.0-beta'}
            </Typography>
          </Box>
        </Stack>
        <List sx={{ width: '100%' }}>
          {navigation.filter((item) => item.id !== 'learning' || settings.learningMode).map((item) => (
            <ListItemButton key={item.id} selected={section === item.id} onClick={() => {
              setSection(item.id);
              if (item.id === 'discover') {
                setSelected(undefined); setEmbeddedRequest(undefined); setSearchActive(false); setQuery(''); setResults([]);
              }
            }}>
              <ListItemIcon>{item.icon}</ListItemIcon><ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Box>
      <Box component="main" className="content-column">
        <AppBar position="sticky" color="inherit" elevation={0}
          sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
          <Toolbar sx={{ gap: 2 }}>
            <Typography variant="h6" sx={{ flexGrow: 1 }}>WizeStream Desktop</Typography>
            <Chip color={mpv?.embeddedAvailable || mpv?.externalAvailable ? 'success' : 'default'}
              label={mpv?.embeddedAvailable ? 'embedded libmpv' : mpv?.externalAvailable ? 'external mpv fallback' : 'mpv unavailable'} />
            <Button startIcon={<QueueMusicRounded />} variant="text" onClick={() => setQueueOpen(true)}>
              Queue · {queue.items.length}
            </Button>
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
            <Stack spacing={1} sx={{ mb: 4 }}><Typography variant="h4">What&apos;s New</Typography><Typography color="text.secondary">Recent videos from your subscribed channels.</Typography></Stack>
            <Box component="form" onSubmit={submitSearch} className="search-row">
              <TextField select label="Service" value={serviceId} onChange={(event) => setServiceId(Number(event.target.value))} sx={{ minWidth: 190 }}>
                {services.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}
              </TextField>
              <TextField fullWidth label="Search" value={query} onChange={(event) => {
                const value = event.target.value; setQuery(value);
                if (!value.trim()) { setSearchActive(false); setResults([]); }
              }} slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }} />
              <Button type="submit" variant="contained" size="large" disabled={loading || feedLoading}>Search</Button>
            </Box>
            <Stack direction="row" spacing={1} className="feed-filter-row" sx={{ mt: 2, alignItems: 'center' }}>
              {feedFilters.map((filter) => <Chip key={filter.id} label={filter.label} clickable
                color={feedFilter === filter.id ? 'primary' : 'default'}
                variant={feedFilter === filter.id ? 'filled' : 'outlined'}
                onClick={() => setFeedFilter((current) => current === filter.id ? 'none' : filter.id)} />)}
              <Box sx={{ flexGrow: 1 }} />
              <Button startIcon={feedLoading ? <CircularProgress size={18} /> : <ReplayRounded />} variant="text"
                disabled={feedLoading} onClick={() => void loadSubscriptionFeed(true)}>
                {feedLoading ? 'Refreshing…' : 'Refresh feed'}
              </Button>
            </Stack>
            {error && <Alert severity="error" sx={{ mt: 3 }}>{error}</Alert>}
            {feedError && <Alert severity="error" sx={{ mt: 3 }}>{feedError}</Alert>}
            {!feedLoading && subscriptionFeed.failedChannels > 0 && <Alert severity="warning" sx={{ mt: 3 }}>
              {subscriptionFeed.failedChannels} of {subscriptionFeed.totalChannels} channels could not be refreshed.
            </Alert>}
            {feedLoading && subscriptionFeed.items.length > 0 && <LinearProgress sx={{ mt: 2 }} />}
            {loading && <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>}
            {embeddedRequest && selected && mpv?.embeddedAvailable && <EmbeddedPlayer request={embeddedRequest}
              stream={selected} recordPlayback={settings.enableWatchHistory}
              videoChoice={videoChoice} audioChoice={audioChoice} subtitleChoice={subtitleChoice}
              onVideoChoiceChange={changeVideoChoice} onAudioChoiceChange={changeAudioChoice}
              onSubtitleChoiceChange={changeSubtitleChoice}
              sponsorBlockSettings={settings.sponsorBlock}
              equalizerSettings={settings.equalizer}
              onEqualizerChange={(equalizer) => saveSettings({ equalizer })}
              playbackParameters={selectedPlaybackParameters}
              onPlaybackParametersChange={savePlaybackParameters}
              seekDurationSeconds={settings.seekDurationSeconds} exactSeeking={!settings.useInexactSeek}
              startFullscreen={settings.startPlayerFullscreen}
              hasPrevious={adjacentQueueIndex(queue, 'previous') !== undefined}
              hasNext={adjacentQueueIndex(queue, 'next') !== undefined || settings.autoQueueRelated}
              hasLinearQueueNext={queue.currentIndex >= 0 && queue.currentIndex < queue.items.length - 1}
              onPrevious={() => void advanceQueue('previous')} onNext={() => void advanceQueue('next')}
              onEnded={() => { if (settings.autoplayNext) void advanceQueue('next'); }}
              onOpenQueue={() => setQueueOpen(true)}
              externalAvailable={Boolean(mpv.externalAvailable)} onError={setError} />}
            {selected && showPlayingInfo && <VideoInformationPanel details={selected}
              onOpen={resolveStream} onDownload={() => setSection('downloads')}
              onAddToPlaylist={() => setSection('playlists')}
              onPlayNext={() => addSelectedToQueue('next')} onAddToQueue={() => addSelectedToQueue('end')}
              onAddNote={settings.learningMode && settings.learningNotes ? () => setSection('learning') : undefined} />}
            {selected && !showPlayingInfo && <Card sx={{ mt: 4, overflow: 'hidden' }}><Box className="details-card">
              {selected.thumbnailUrl && <Box component="img" src={selected.thumbnailUrl} alt="" className="details-thumbnail" />}
              <CardContent sx={{ p: 4 }}><Chip label={selected.streamType} size="small" /><Typography variant="h4" sx={{ mt: 2 }}>{selected.name}</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>{selected.uploaderName}</Typography>
                <Stack direction="row" sx={{ mt: 3, gap: 1, flexWrap: 'wrap' }}><Chip label={`${selected.videoStreams.length} video variants`} /><Chip label={`${selected.audioStreams.length} audio variants`} /><Chip label={`${selected.subtitles.length} captions`} /><Chip label={`${Math.round(selected.duration / 60)} min`} /></Stack>
                <Box className="stream-selectors" sx={{ mt: 3 }}>
                  <TextField select label="Video" value={videoChoice} onChange={(event) => changeVideoChoice(event.target.value)}>
                    <MenuItem value="auto">Automatic</MenuItem>
                    {selected.videoStreams.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{videoLabel(stream)}</MenuItem>)}
                  </TextField>
                  <TextField select label="Audio" value={audioChoice}
                    onChange={(event) => changeAudioChoice(event.target.value)}>
                    <MenuItem value="auto">Automatic</MenuItem>
                    {selected.audioStreams.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{audioLabel(stream)}</MenuItem>)}
                  </TextField>
                  <TextField select label="Captions" value={subtitleChoice}
                    onChange={(event) => changeSubtitleChoice(event.target.value)}>
                    <MenuItem value="none">Off</MenuItem>
                    {selected.subtitles.map((stream, index) => <MenuItem key={`${stream.id}:${index}`} value={String(index)}>{subtitleLabel(stream)}</MenuItem>)}
                  </TextField>
                </Box>
                {playbackVideo?.videoOnly && !effectiveAudio && <Alert severity="warning" sx={{ mt: 2 }}>This video-only variant requires an audio track.</Alert>}
                <Stack direction="row" sx={{ mt: 4, gap: 1, flexWrap: 'wrap' }}>
                  <Button startIcon={<PlayArrowRounded />} variant="contained" size="large"
                    disabled={!selectedPlaybackUrl || (playbackVideo?.videoOnly && !effectiveAudio)
                      || (!embeddedSelection && !mpv?.externalAvailable)} onClick={() => void playSelected()}>{embeddedSelection ? 'Play embedded' : 'Play with mpv'}</Button>
                  <Button startIcon={<DownloadRounded />} variant="outlined" size="large" onClick={() => setSection('downloads')}>Download</Button>
                  <Button startIcon={<PlaylistAddRounded />} variant="outlined" size="large" onClick={() => setSection('playlists')}>Add to playlist</Button>
                  <Button startIcon={<QueuePlayNextRounded />} variant="outlined" size="large" onClick={() => addSelectedToQueue('next')}>Play next</Button>
                  <Button startIcon={<QueueMusicRounded />} variant="outlined" size="large" onClick={() => addSelectedToQueue('end')}>Add to queue</Button>
                  {settings.learningMode && settings.learningNotes && <Button startIcon={<NoteAddRounded />} variant="outlined" size="large" onClick={() => setSection('learning')}>Add note</Button>}
                </Stack>
              </CardContent>
            </Box></Card>}
            {!loading && !selected && feedLoading && subscriptionFeed.items.length === 0
              && <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}><CircularProgress /></Box>}
            {!loading && !selected && visibleResults.length > 0 && <Box className="feed-video-grid" sx={{ mt: 4 }}>
              {visibleResults.map((item) => <FeedVideoCard key={`${item.serviceId}:${item.url}`} item={item}
                onOpen={() => void openResult(item)} />)}
            </Box>}
            {!loading && !selected && !feedLoading && visibleResults.length === 0
              && <LibraryEmpty text={searchActive ? 'No search results match this filter.'
                : subscriptionFeed.totalChannels === 0 ? 'Subscribe to channels to see their latest videos here.'
                  : 'No subscription videos match this filter.'} />}
          </> : section === 'subscriptions' ? <SubscriptionsPanel services={services} onOpen={resolveStream} />
            : section === 'playlists' ? <PlaylistsPanel currentStream={selectedLibraryStream} onOpen={resolveStream} />
              : section === 'history' ? <HistoryPanel onOpen={resolveStream} />
                : section === 'learning' ? <LearningPanel currentStream={selectedLibraryStream} onOpen={resolveStream} />
                  : section === 'downloads' ? <DownloadsPanel currentStream={selected} />
                    : section === 'sync' ? <SyncPanel sync={sync} onRefresh={() => void window.wizestream.backend.invoke<SyncStatus>('sync.status').then(setSync)} />
                      : section === 'settings' ? <SettingsPanel settings={settings} services={services} currentVersion={updateState?.currentVersion}
                        onUpdate={saveSettings} onReset={resetSettings}
                        onOpenDownloads={() => void window.wizestream.downloads.openFolder()} onOpenDevices={() => setSection('sync')}
                        onOpenUpdates={() => void openUpdates()} onSettingsRestored={(restored) => {
                          setSettings(restored);
                          const preferred = services.find((service) => service.id === restored.defaultServiceId);
                          setServiceId(preferred?.id ?? services[0]?.id ?? 0);
                        }} />
                        : <AboutPanel currentVersion={updateState?.currentVersion} />}
        </Container>
      </Box>
      <PlaybackQueueDialog open={queueOpen} queue={queue} onClose={() => setQueueOpen(false)}
        onPlay={(index) => void playQueueIndex(index)}
        onMove={(from, to) => setQueue((current) => moveQueueItem(current, from, to))}
        onRemove={(index) => setQueue((current) => removeQueueItem(current, index))}
        onClear={clearQueue}
        onRepeatMode={(repeatMode) => setQueue((current) => ({ ...current, repeatMode }))}
        onShuffle={(shuffle) => setQueue((current) => ({ ...current, shuffle }))} />
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

function FeedVideoCard({ item, onOpen }: { item: SearchItem; onOpen(): void }) {
  const metadata = [viewCountLabel(item.viewCount), publishedAgeLabel(item)].filter(Boolean).join(' · ');
  const live = item.streamType === 'LIVE_STREAM' || item.streamType === 'AUDIO_LIVE_STREAM';
  return <Card variant="outlined" className="feed-video-card">
    <CardActionArea onClick={onOpen} sx={{ height: '100%' }} aria-label={`Open ${item.name}`}>
      <Box className="feed-video-cover">
        {item.thumbnailUrl
          ? <Box component="img" src={item.thumbnailUrl} alt="" className="result-thumbnail" />
          : <Box className="result-thumbnail" />}
        {live ? <Chip size="small" color="error" label="LIVE" className="feed-video-badge" />
          : item.duration != null && item.duration > 0
            ? <Chip size="small" label={formatTimestamp(item.duration)} className="feed-video-badge" /> : null}
      </Box>
      <CardContent>
        <Typography variant="h6" className="two-lines">{item.name}</Typography>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mt: 2 }}>
          <Avatar src={item.uploaderAvatarUrl} alt="" sx={{ width: 38, height: 38 }}>
            <SubscriptionsRounded fontSize="small" />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" className="two-lines">{item.uploaderName || 'Unknown channel'}</Typography>
            {metadata && <Typography color="text.secondary" variant="caption">{metadata}</Typography>}
          </Box>
        </Stack>
      </CardContent>
    </CardActionArea>
  </Card>;
}

function EmbeddedPlayer({ request, stream, recordPlayback, sponsorBlockSettings, videoChoice, audioChoice,
  subtitleChoice, onVideoChoiceChange, onAudioChoiceChange, onSubtitleChoiceChange, equalizerSettings,
  onEqualizerChange, playbackParameters, onPlaybackParametersChange, seekDurationSeconds, exactSeeking,
  startFullscreen, hasPrevious, hasNext, hasLinearQueueNext, onPrevious, onNext, onEnded, onOpenQueue,
  externalAvailable, onError }: {
  request: EmbeddedPlayerRequest & { title: string; nonce: number }; externalAvailable: boolean;
  stream: StreamDetails; recordPlayback: boolean; sponsorBlockSettings: SponsorBlockSettings;
  videoChoice: string; audioChoice: string; subtitleChoice: string;
  onVideoChoiceChange(value: string, positionSeconds: number): void;
  onAudioChoiceChange(value: string): void;
  onSubtitleChoiceChange(value: string): void;
  equalizerSettings: EqualizerSettings;
  onEqualizerChange(value: EqualizerSettings): Promise<void>;
  playbackParameters: PlaybackParameterSettings;
  onPlaybackParametersChange(value: PlaybackParameterSettings): Promise<void>;
  seekDurationSeconds: number;
  exactSeeking: boolean;
  startFullscreen: boolean;
  hasPrevious: boolean;
  hasNext: boolean;
  hasLinearQueueNext: boolean;
  onPrevious(): void;
  onNext(): void;
  onEnded(): void;
  onOpenQueue(): void;
  onError(value: string): void;
}) {
  const player = useRef<MpvVideoElement>(null);
  const fullscreenContainer = useRef<HTMLDivElement>(null);
  const [state, setState] = useState({ status: 'Opening', time: 0, duration: 0, rendererName: 'libmpv',
    audioTrack: 'auto', subtitleTrack: 'off' });
  const latestState = useRef(state);
  const lastPlaybackSave = useRef(0);
  const skippedSponsorBlockSegments = useRef(new Set<string>());
  const ignoredSponsorBlockSegment = useRef<string | undefined>(undefined);
  const sponsorBlockSettingsRef = useRef(sponsorBlockSettings);
  const sponsorBlockSegmentsRef = useRef(stream.sponsorBlockSegments ?? []);
  const [localError, setLocalError] = useState<string>();
  const [manualSponsorBlockSegment, setManualSponsorBlockSegment] = useState<SponsorBlockSegment>();
  const [sponsorBlockNotice, setSponsorBlockNotice] = useState<string>();
  const [volume, setVolume] = useState(loadPlayerVolume);
  const lastAudibleVolume = useRef(volume > 0 ? volume : 80);
  const [muted, setMuted] = useState(false);
  const [liveEqualizer, setLiveEqualizer] = useState(equalizerSettings);
  const liveEqualizerRef = useRef(liveEqualizer);
  const [equalizerOpen, setEqualizerOpen] = useState(false);
  const [livePlaybackParameters, setLivePlaybackParameters] = useState(playbackParameters);
  const livePlaybackParametersRef = useRef(livePlaybackParameters);
  const [playbackParametersOpen, setPlaybackParametersOpen] = useState(false);
  const [sleepTimer, setSleepTimer] = useState<SleepTimerState>(inactiveSleepTimer);
  const [sleepTimerNow, setSleepTimerNow] = useState(Date.now());
  const [sleepFade, setSleepFade] = useState(1);
  const sleepFadeWindow = useRef(0);
  const [sleepTimerOpen, setSleepTimerOpen] = useState(false);
  const [sleepTimerChoice, setSleepTimerChoice] = useState<SleepTimerChoice>('30');
  const [customSleepMinutes, setCustomSleepMinutes] = useState('90');
  const [customSleepError, setCustomSleepError] = useState<string>();
  const [sleepTimerNotice, setSleepTimerNotice] = useState<string>();
  const [fullscreen, setFullscreen] = useState(false);
  const [seekPreviewTime, setSeekPreviewTime] = useState<number>();
  const effectiveVolume = (muted ? 0 : volume) * equalizerHeadroomMultiplier(liveEqualizer) * sleepFade;
  const effectiveVolumeRef = useRef(effectiveVolume);
  const mediaControlsReady = useRef(false);
  const endedHandled = useRef(false);
  const sleepTimerRef = useRef(sleepTimer);
  const hasLinearQueueNextRef = useRef(hasLinearQueueNext);
  const onEndedRef = useRef(onEnded);
  const exactSeekingRef = useRef(exactSeeking);
  const startFullscreenRef = useRef(startFullscreen);

  useEffect(() => { sponsorBlockSettingsRef.current = sponsorBlockSettings; }, [sponsorBlockSettings]);
  useEffect(() => { sponsorBlockSegmentsRef.current = stream.sponsorBlockSegments ?? []; }, [stream.sponsorBlockSegments]);
  useEffect(() => { sleepTimerRef.current = sleepTimer; }, [sleepTimer]);
  useEffect(() => { hasLinearQueueNextRef.current = hasLinearQueueNext; }, [hasLinearQueueNext]);
  useEffect(() => { onEndedRef.current = onEnded; }, [onEnded]);
  useEffect(() => { exactSeekingRef.current = exactSeeking; }, [exactSeeking]);
  useEffect(() => { startFullscreenRef.current = startFullscreen; }, [startFullscreen]);
  useEffect(() => {
    const updateFullscreen = () => setFullscreen(document.fullscreenElement === fullscreenContainer.current);
    document.addEventListener('fullscreenchange', updateFullscreen);
    updateFullscreen();
    return () => document.removeEventListener('fullscreenchange', updateFullscreen);
  }, []);
  useEffect(() => {
    if (!equalizerOpen) setLiveEqualizer(equalizerSettings);
  }, [equalizerOpen, equalizerSettings]);
  useEffect(() => {
    if (!playbackParametersOpen) setLivePlaybackParameters(playbackParameters);
  }, [playbackParameters, playbackParametersOpen]);

  useEffect(() => {
    liveEqualizerRef.current = liveEqualizer;
    const element = player.current;
    if (!element || !mediaControlsReady.current) return;
    void element.setEqualizer(liveEqualizer.enabled ? liveEqualizer.gains : undefined)
      .catch((reason: unknown) => onError(`Equalizer: ${errorMessage(reason)}`));
  }, [liveEqualizer, onError]);

  useEffect(() => {
    livePlaybackParametersRef.current = livePlaybackParameters;
    const element = player.current;
    if (!element || !mediaControlsReady.current) return;
    void element.setPlaybackParameters(
      livePlaybackParameters.speed,
      livePlaybackParameters.pitch,
      livePlaybackParameters.skipSilence,
    ).catch((reason: unknown) => onError(`Playback speed: ${errorMessage(reason)}`));
  }, [livePlaybackParameters, onError]);

  useEffect(() => {
    effectiveVolumeRef.current = effectiveVolume;
    if (!mediaControlsReady.current) return;
    void player.current?.setVolume(effectiveVolume)
      .catch((reason: unknown) => onError(`Volume: ${errorMessage(reason)}`));
  }, [effectiveVolume, onError]);

  useEffect(() => {
    const element = player.current;
    if (!element) return;
    const openingState = { status: 'Opening', time: 0, duration: 0, rendererName: 'libmpv',
      audioTrack: 'auto', subtitleTrack: 'off' };
    latestState.current = openingState; setState(openingState);
    mediaControlsReady.current = false;
    endedHandled.current = false;
    lastPlaybackSave.current = 0;
    skippedSponsorBlockSegments.current.clear();
    ignoredSponsorBlockSegment.current = undefined;
    setManualSponsorBlockSegment(undefined);
    setSponsorBlockNotice(undefined);
    const savePosition = (time: number, force = false) => {
      if (!recordPlayback || !Number.isFinite(time) || time < 0) return;
      const now = Date.now();
      if (!force && now - lastPlaybackSave.current < 10_000) return;
      lastPlaybackSave.current = now;
      void window.wizestream.backend.invoke('library.playback-state.save', {
        serviceId: stream.serviceId, url: stream.url, positionMillis: Math.round(time * 1_000),
      }).catch(() => undefined);
    };
    const update = (event: Event) => {
      const next = (event as CustomEvent<typeof state>).detail;
      latestState.current = next; setState(next); savePosition(next.time);
      if (next.status === 'Ended' && !endedHandled.current) {
        endedHandled.current = true;
        const timer = sleepTimerRef.current;
        const stopForTimer = timer.mode === 'end_current'
          || (timer.mode === 'end_queue' && !hasLinearQueueNextRef.current);
        if (stopForTimer) {
          void element.stop();
          setSleepTimer(inactiveSleepTimer);
          setSleepFade(1);
          setSleepTimerNotice('Sleep timer finished');
        } else {
          onEndedRef.current();
        }
      }
      const currentSettings = sponsorBlockSettingsRef.current;
      const active = activeSponsorBlockSegment(
        sponsorBlockSegmentsRef.current,
        next.time,
        currentSettings,
        skippedSponsorBlockSegments.current,
        ignoredSponsorBlockSegment.current,
      );
      if (ignoredSponsorBlockSegment.current) {
        const ignored = sponsorBlockSegmentsRef.current.find((segment) =>
          sponsorBlockSegmentKey(segment) === ignoredSponsorBlockSegment.current);
        const positionMillis = next.time * 1_000;
        if (!ignored || positionMillis < ignored.startTime || positionMillis >= ignored.endTime) {
          ignoredSponsorBlockSegment.current = undefined;
        }
      }
      if (!active) {
        setManualSponsorBlockSegment(undefined);
        return;
      }
      const preference = currentSettings.categories[active.category];
      if (preference.behavior === 'manual') {
        setManualSponsorBlockSegment(active);
        return;
      }
      if (next.status !== 'Playing') return;
      const key = sponsorBlockSegmentKey(active);
      skippedSponsorBlockSegments.current.add(key);
      setManualSponsorBlockSegment(undefined);
      void element.seek(Math.max(0, active.endTime / 1_000));
      if (currentSettings.notifications) {
        setSponsorBlockNotice(`Skipped ${sponsorBlockCategoryTitle(active.category)}`);
      }
    };
    const fail = (event: Event) => { const value = String((event as CustomEvent<unknown>).detail); setLocalError(value); onError(value); };
    element.addEventListener('mpv-state', update);
    element.addEventListener('mpv-error', fail);
    setLocalError(undefined);
    let cancelled = false;
    const mediaReady = waitForMpvMediaReady(element);
    void Promise.all([element.openMedia(request), mediaReady.promise]).then(async () => {
      if (cancelled) return;
      if (request.startSeconds && request.startSeconds > 0) await element.seek(request.startSeconds);
      await element.play();
      if (cancelled) return;
      mediaControlsReady.current = true;
      if (startFullscreenRef.current && !document.fullscreenElement) {
        void fullscreenContainer.current?.requestFullscreen().catch(() => undefined);
      }

      // Audio enhancements are optional. Apply them only after playback starts so an
      // unavailable native filter or an older addon can never block the video itself.
      const equalizer = liveEqualizerRef.current;
      if (equalizer.enabled) {
        void element.setEqualizer(equalizer.gains)
          .catch((reason: unknown) => onError(`Equalizer: ${errorMessage(reason)}`));
      }
      const parameters = livePlaybackParametersRef.current;
      if (parameters.speed !== 1 || parameters.pitch !== 1 || parameters.skipSilence) {
        void element.setPlaybackParameters(parameters.speed, parameters.pitch, parameters.skipSilence)
          .catch((reason: unknown) => onError(`Playback speed: ${errorMessage(reason)}`));
      }
      void element.setVolume(effectiveVolumeRef.current)
        .catch((reason: unknown) => onError(`Volume: ${errorMessage(reason)}`));
    }).catch((reason: unknown) => {
      if (cancelled) return;
      const value = errorMessage(reason); setLocalError(value); onError(value);
    });
    return () => {
      cancelled = true;
      mediaReady.cancel();
      mediaControlsReady.current = false;
      if (latestState.current.time > 0) savePosition(latestState.current.time, true);
      element.removeEventListener('mpv-state', update); element.removeEventListener('mpv-error', fail);
    };
  }, [request.nonce, request.source, stream.serviceId, stream.url, recordPlayback, onError]);

  useEffect(() => {
    if (!player.current) return;
    void player.current.setAudioTrack(request.audio).catch((reason: unknown) => onError(`Audio track: ${errorMessage(reason)}`));
  }, [request.audio?.url, onError]);

  useEffect(() => {
    if (!player.current) return;
    void player.current.setSubtitleTrack(request.subtitle).catch((reason: unknown) => onError(`Caption track: ${errorMessage(reason)}`));
  }, [request.subtitle?.url, onError]);

  useEffect(() => {
    const stop = () => {
      void player.current?.stop();
      setSleepTimer(inactiveSleepTimer);
      setSleepFade(1);
    };
    window.addEventListener('wizestream-stop-player', stop);
    return () => window.removeEventListener('wizestream-stop-player', stop);
  }, []);

  useEffect(() => {
    if (sleepTimer.mode === 'none') {
      setSleepFade(1);
      sleepFadeWindow.current = 0;
      return;
    }
    const tick = () => {
      const now = Date.now();
      const playback = latestState.current;
      const playbackRemaining = sleepTimer.mode === 'end_queue' && hasLinearQueueNext
        ? -1 : playback.duration > 0 ? playback.duration - playback.time : -1;
      const remaining = sleepTimerRemainingMillis(sleepTimer, now, playbackRemaining);
      setSleepTimerNow(now);
      if (remaining > 0 && remaining < 30_000 && sleepFadeWindow.current === 0) {
        sleepFadeWindow.current = remaining;
      } else if (remaining >= 30_000) {
        sleepFadeWindow.current = 0;
      }
      setSleepFade(sleepTimerFadeMultiplier(sleepTimer, remaining,
        sleepFadeWindow.current || 30_000));
      const finished = sleepTimer.mode === 'duration' && remaining === 0;
      if (finished) {
        void player.current?.stop();
        setSleepTimer(inactiveSleepTimer);
        setSleepFade(1);
        setSleepTimerNotice('Sleep timer finished');
      }
    };
    tick();
    const timer = window.setInterval(tick, 1_000);
    return () => window.clearInterval(timer);
  }, [hasLinearQueueNext, sleepTimer]);

  function seekManually(value: number) {
    const settings = sponsorBlockSettingsRef.current;
    const active = activeSponsorBlockSegment(
      sponsorBlockSegmentsRef.current, value, settings, new Set(), undefined,
    );
    if (settings.gracedRewind) {
      ignoredSponsorBlockSegment.current = active ? sponsorBlockSegmentKey(active) : undefined;
    } else {
      ignoredSponsorBlockSegment.current = undefined;
      if (active) skippedSponsorBlockSegments.current.delete(sponsorBlockSegmentKey(active));
    }
    void player.current?.seek(value, exactSeekingRef.current);
  }

  function seekRelative(delta: number) {
    const duration = latestState.current.duration;
    const target = Math.max(0, duration > 0
      ? Math.min(duration, latestState.current.time + delta) : latestState.current.time + delta);
    seekManually(target);
  }

  function skipManualSponsorBlockSegment() {
    if (!manualSponsorBlockSegment) return;
    skippedSponsorBlockSegments.current.add(sponsorBlockSegmentKey(manualSponsorBlockSegment));
    void player.current?.seek(Math.max(0, manualSponsorBlockSegment.endTime / 1_000));
    if (sponsorBlockSettingsRef.current.notifications) {
      setSponsorBlockNotice(`Skipped ${sponsorBlockCategoryTitle(manualSponsorBlockSegment.category)}`);
    }
    setManualSponsorBlockSegment(undefined);
  }

  function updateVolume(value: number) {
    const next = Math.max(0, Math.min(100, value));
    setVolume(next);
    if (next > 0) {
      lastAudibleVolume.current = next;
      setMuted(false);
    }
    window.localStorage.setItem('wizestream.desktop.player.volume.v1', String(next));
  }

  function toggleMute() {
    if (muted || volume === 0) {
      if (volume === 0) updateVolume(lastAudibleVolume.current);
      setMuted(false);
      return;
    }
    setMuted(true);
  }

  function stopPlayback() {
    void player.current?.stop();
    setSleepTimer(inactiveSleepTimer);
    setSleepFade(1);
  }

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await fullscreenContainer.current?.requestFullscreen();
    } catch (reason) {
      onError(`Fullscreen: ${errorMessage(reason)}`);
    }
  }

  function openSleepTimer() {
    setCustomSleepError(undefined);
    if (sleepTimer.mode === 'end_current') setSleepTimerChoice('end_current');
    else if (sleepTimer.mode === 'end_queue') setSleepTimerChoice('end_queue');
    else if (sleepTimer.mode === 'duration') {
      const minutes = Math.max(1, Math.ceil(sleepTimerRemainingMillis(sleepTimer, Date.now(), -1) / 60_000));
      const preset = [15, 30, 45, 60].includes(minutes) ? String(minutes) as SleepTimerChoice : 'custom';
      setSleepTimerChoice(preset);
      if (preset === 'custom') setCustomSleepMinutes(String(minutes));
    } else setSleepTimerChoice('30');
    setSleepTimerOpen(true);
  }

  function startSleepTimer() {
    sleepFadeWindow.current = 0;
    if (sleepTimerChoice === 'end_current' || sleepTimerChoice === 'end_queue') {
      setSleepTimer({ mode: sleepTimerChoice, fadeOut: sleepTimer.fadeOut });
    } else {
      const minutes = sleepTimerChoice === 'custom' ? Number(customSleepMinutes) : Number(sleepTimerChoice);
      if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1_440) {
        setCustomSleepError('Enter a duration from 1 to 1440 minutes');
        return;
      }
      setSleepTimer({ mode: 'duration', deadline: Date.now() + minutes * 60_000, fadeOut: sleepTimer.fadeOut });
    }
    setSleepTimerNow(Date.now());
    setSleepTimerOpen(false);
  }

  const markerSegments = validSponsorBlockSegments(
    stream.sponsorBlockSegments ?? [], sponsorBlockSettings,
  );
  const chapters = stream.chapters ?? [];
  const currentChapter = chapters.reduce((active, chapter, index) =>
    chapter.startTimeSeconds <= state.time ? index : active, -1);
  const seekPreview = seekPreviewTime === undefined ? undefined
    : previewFrameAt(stream.previewFrames ?? [], seekPreviewTime);

  return <Card ref={fullscreenContainer} className="embedded-player-card" sx={{ mt: 4, overflow: 'hidden' }}><Box className="embedded-player-frame">
    <mpv-video ref={player} render-mode="shared-texture" volume="80" title={request.title} />
  </Box><CardContent>{localError && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" disabled={!externalAvailable} onClick={() => void window.wizestream.player.play({
    url: request.source, title: request.title, audioUrl: request.audio?.url, subtitleUrl: request.subtitle?.url,
    startSeconds: request.startSeconds,
  })}>Open with external mpv</Button>}>{localError}</Alert>}
    <Box className="player-controls-primary">
      <Stack direction="row" spacing={0.5}>
        <IconButton aria-label="Previous queued video" disabled={!hasPrevious} onClick={onPrevious}><SkipPreviousRounded /></IconButton>
        <IconButton aria-label="Play" onClick={() => void player.current?.play()}><PlayArrowRounded /></IconButton>
        <IconButton aria-label="Pause" onClick={() => void player.current?.pause()}><PauseRounded /></IconButton>
        <IconButton aria-label="Stop" onClick={stopPlayback}><StopRounded /></IconButton>
        <IconButton aria-label="Next queued video" disabled={!hasNext} onClick={onNext}><SkipNextRounded /></IconButton>
      </Stack>
      <Button size="small" onClick={() => seekRelative(-seekDurationSeconds)}>-{seekDurationSeconds}s</Button>
      <Typography className="mono player-time" variant="body2">{formatTimestamp(state.time)}</Typography>
      <Box className="player-timeline">
        <Slider min={0} max={Math.max(1, state.duration)} value={Math.min(state.time, Math.max(1, state.duration))}
          onChange={(_event, value) => setSeekPreviewTime(Number(value))}
          onChangeCommitted={(_event, value) => { seekManually(Number(value)); setSeekPreviewTime(undefined); }} />
        {seekPreviewTime !== undefined && <Box className="seek-preview" sx={{
          left: `${state.duration > 0 ? Math.max(0, Math.min(100, seekPreviewTime / state.duration * 100)) : 0}%`,
        }}>
          {seekPreview && <Box className="seek-preview-image" sx={{
            width: seekPreview.width, height: seekPreview.height,
            backgroundImage: `url(${JSON.stringify(seekPreview.url)})`,
            backgroundPosition: `-${seekPreview.left}px -${seekPreview.top}px`,
            backgroundSize: `${seekPreview.pageWidth}px ${seekPreview.pageHeight}px`,
          }} />}
          <Typography className="mono" variant="caption">{formatTimestamp(seekPreviewTime)}</Typography>
        </Box>}
        {state.duration > 0 && <Box className="sponsor-block-markers" aria-hidden>
          {markerSegments.map((segment) => <Box key={sponsorBlockSegmentKey(segment)} sx={{
            left: `${Math.max(0, segment.startTime / 10 / state.duration)}%`,
            width: `${Math.max(0.25, (segment.endTime - segment.startTime) / 10 / state.duration)}%`,
            bgcolor: sponsorBlockCategoryColor(segment.category),
          }} />)}
        </Box>}
      </Box>
      <Typography className="mono player-time" variant="body2">{formatTimestamp(state.duration)}</Typography>
      <Button size="small" onClick={() => seekRelative(seekDurationSeconds)}>+{seekDurationSeconds}s</Button>
      <Tooltip title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}>
        <IconButton aria-label={fullscreen ? 'Exit fullscreen' : 'Fullscreen'} onClick={() => void toggleFullscreen()}>
          {fullscreen ? <FullscreenExitRounded /> : <FullscreenRounded />}
        </IconButton>
      </Tooltip>
    </Box>
    {chapters.length > 0 && <TextField select fullWidth size="small" label="Chapter"
      value={currentChapter < 0 ? '' : String(currentChapter)} sx={{ mt: 1.5 }}
      onChange={(event) => seekManually(chapters[Number(event.target.value)].startTimeSeconds)}>
      {chapters.map((chapter, index) => <MenuItem key={`${chapter.startTimeSeconds}:${index}`} value={String(index)}>
        {formatTimestamp(chapter.startTimeSeconds)} · {chapter.title}
      </MenuItem>)}
    </TextField>}
    <Box className="stream-selectors player-stream-selectors" sx={{ mt: 1.5 }}>
      <TextField select size="small" label="Video" value={videoChoice}
        onChange={(event) => onVideoChoiceChange(event.target.value, latestState.current.time)}>
        <MenuItem value="auto">Automatic</MenuItem>
        {stream.videoStreams.map((item, index) => <MenuItem key={`${item.id}:${index}`} value={String(index)}>
          {videoLabel(item)}
        </MenuItem>)}
      </TextField>
      <TextField select size="small" label="Audio" value={audioChoice}
        onChange={(event) => onAudioChoiceChange(event.target.value)}>
        <MenuItem value="auto">Automatic</MenuItem>
        {stream.audioStreams.map((item, index) => <MenuItem key={`${item.id}:${index}`} value={String(index)}>
          {audioLabel(item)}
        </MenuItem>)}
      </TextField>
      <TextField select size="small" label="Captions" value={subtitleChoice}
        onChange={(event) => onSubtitleChoiceChange(event.target.value)}>
        <MenuItem value="none">Off</MenuItem>
        {stream.subtitles.map((item, index) => <MenuItem key={`${item.id}:${index}`} value={String(index)}>
          {subtitleLabel(item)}
        </MenuItem>)}
      </TextField>
    </Box>
    <Box className="player-tools-row" sx={{ mt: 1.5 }}>
      <Stack direction="row" className="player-audio-controls" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Tooltip title={muted || volume === 0 ? 'Unmute' : 'Mute'}>
          <IconButton aria-label={muted || volume === 0 ? 'Unmute' : 'Mute'} onClick={toggleMute}>
            {muted || volume === 0 ? <VolumeOffRounded /> : <VolumeUpRounded />}
          </IconButton>
        </Tooltip>
        <Slider className="player-volume-slider" min={0} max={100} value={volume}
          aria-label="Volume" valueLabelDisplay="auto" valueLabelFormat={(value) => `${value}%`}
          onChange={(_event, value) => updateVolume(Number(value))} />
        <Typography className="mono player-volume-label" variant="body2">{Math.round(volume)}%</Typography>
        <Button startIcon={<EqualizerRounded />} variant={liveEqualizer.enabled ? 'contained' : 'outlined'}
          onClick={() => setEqualizerOpen(true)}>Equalizer · {equalizerPresetLabel(liveEqualizer.preset)}</Button>
        <Button startIcon={<SpeedRounded />} variant={livePlaybackParameters.speed === 1
          && livePlaybackParameters.pitch === 1 && !livePlaybackParameters.skipSilence ? 'outlined' : 'contained'}
          onClick={() => setPlaybackParametersOpen(true)}>Speed · {formatPlaybackSpeed(livePlaybackParameters.speed)}</Button>
        <Button startIcon={<BedtimeRounded />} variant={sleepTimer.mode === 'none' ? 'outlined' : 'contained'}
          onClick={openSleepTimer}>{sleepTimer.mode === 'none' ? 'Sleep timer'
            : sleepTimerStatus(sleepTimer, sleepTimerRemainingMillis(sleepTimer, sleepTimerNow,
              sleepTimer.mode === 'end_queue' && hasLinearQueueNext
                ? -1 : state.duration > 0 ? state.duration - state.time : -1))}</Button>
        <Button startIcon={<QueueMusicRounded />} variant="outlined" onClick={onOpenQueue}>Queue · Open</Button>
      </Stack>
    </Box>
    <Stack direction="row" className="player-status-row" sx={{ mt: 1.5, gap: 1, flexWrap: 'wrap' }}>
      {manualSponsorBlockSegment && <Button size="small" variant="contained" onClick={skipManualSponsorBlockSegment}>
        {manualSponsorBlockSegment.category === 'sponsor' ? 'Skip sponsor' : 'Skip segment'}
      </Button>}
      <Chip size="small" label={`Audio · ${request.audio?.title ?? state.audioTrack}`} />
      <Chip size="small" label={`Captions · ${request.subtitle?.title ?? state.subtitleTrack}`} />
      <Chip size="small" label={`${state.status} · ${state.rendererName}`} />
    </Stack>
    <Snackbar open={Boolean(sponsorBlockNotice)} autoHideDuration={3_000}
      onClose={() => setSponsorBlockNotice(undefined)} message={sponsorBlockNotice} />
    <Snackbar open={Boolean(sleepTimerNotice)} autoHideDuration={3_000}
      onClose={() => setSleepTimerNotice(undefined)} message={sleepTimerNotice} />
    <EqualizerDialog open={equalizerOpen} value={liveEqualizer} appliesLive
      onPreview={setLiveEqualizer} onCommit={(value) => {
        setLiveEqualizer(value);
        void onEqualizerChange(value).catch((reason: unknown) => onError(`Equalizer settings: ${errorMessage(reason)}`));
      }} onClose={() => setEqualizerOpen(false)} />
    <PlaybackParametersDialog open={playbackParametersOpen} value={livePlaybackParameters}
      onPreview={setLivePlaybackParameters} onCommit={(value) => {
        setLivePlaybackParameters(value);
        void onPlaybackParametersChange(value)
          .catch((reason: unknown) => onError(`Playback speed settings: ${errorMessage(reason)}`));
      }} onClose={() => setPlaybackParametersOpen(false)} />
    <Dialog open={sleepTimerOpen} onClose={() => setSleepTimerOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Sleep timer</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {sleepTimer.mode !== 'none' && <Alert severity="info">Active · {sleepTimerStatus(sleepTimer,
            sleepTimerRemainingMillis(sleepTimer, sleepTimerNow, state.duration > 0 ? state.duration - state.time : -1))}</Alert>}
          <RadioGroup value={sleepTimerChoice} onChange={(event) => {
            setSleepTimerChoice(event.target.value as SleepTimerChoice); setCustomSleepError(undefined);
          }}>
            <FormControlLabel value="15" control={<Radio />} label="15 minutes" />
            <FormControlLabel value="30" control={<Radio />} label="30 minutes" />
            <FormControlLabel value="45" control={<Radio />} label="45 minutes" />
            <FormControlLabel value="60" control={<Radio />} label="60 minutes" />
            <FormControlLabel value="end_current" control={<Radio />} label="End of current video" />
            <FormControlLabel value="end_queue" control={<Radio />} label="End of queue" />
            <FormControlLabel value="custom" control={<Radio />} label="Custom duration" />
          </RadioGroup>
          {sleepTimerChoice === 'custom' && <TextField label="Minutes (1–1440)" value={customSleepMinutes}
            type="number" error={Boolean(customSleepError)} helperText={customSleepError}
            onChange={(event) => { setCustomSleepMinutes(event.target.value); setCustomSleepError(undefined); }}
            slotProps={{ htmlInput: { min: 1, max: 1_440 } }} />}
          <FormControlLabel label="Fade out during the final 30 seconds" control={<Switch checked={sleepTimer.fadeOut}
            onChange={(event) => setSleepTimer((current) => ({ ...current, fadeOut: event.target.checked }))} />} />
        </Stack>
      </DialogContent>
      <DialogActions>
        {sleepTimer.mode !== 'none' && <Button color="error" onClick={() => {
          setSleepTimer(inactiveSleepTimer); setSleepFade(1); setSleepTimerOpen(false);
        }}>Turn off</Button>}
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={() => setSleepTimerOpen(false)}>Cancel</Button>
        <Button variant="contained" onClick={startSleepTimer}>Start</Button>
      </DialogActions>
    </Dialog>
  </CardContent></Card>;
}

type VideoInfoTab = 'comments' | 'related' | 'description';

function VideoInformationPanel({ details, onOpen, onDownload, onAddToPlaylist, onPlayNext,
  onAddToQueue, onAddNote }: {
  details: StreamDetails;
  onOpen(url: string): Promise<void>;
  onDownload(): void;
  onAddToPlaylist(): void;
  onPlayNext(): void;
  onAddToQueue(): void;
  onAddNote?: () => void;
}) {
  const [tab, setTab] = useState<VideoInfoTab>('related');
  const [comments, setComments] = useState<StreamComments>();
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState<string>();

  useEffect(() => {
    setTab('related'); setComments(undefined); setCommentsError(undefined); setCommentsLoading(false);
  }, [details.url]);

  useEffect(() => {
    if (tab !== 'comments' || comments || commentsLoading || commentsError) return;
    setCommentsLoading(true); setCommentsError(undefined);
    void window.wizestream.backend.invoke<StreamComments>('stream.comments', {
      serviceId: details.serviceId, url: details.url,
    }).then(setComments).catch((reason: unknown) => setCommentsError(errorMessage(reason)))
      .finally(() => setCommentsLoading(false));
  }, [comments, commentsError, commentsLoading, details.serviceId, details.url, tab]);

  const published = details.publishedAt != null
    ? `Published on ${new Date(details.publishedAt).toLocaleDateString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
    })}` : details.textualUploadDate;
  const description = plainDescription(details.description, details.descriptionType);

  return <Card variant="outlined" sx={{ mt: 3, overflow: 'hidden' }}>
    <CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
      <Typography variant="h4">{details.name}</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} sx={{ mt: 3, gap: 2, alignItems: { md: 'center' } }}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0, flexGrow: 1 }}>
          <Avatar src={details.uploaderAvatarUrl} alt="" sx={{ width: 52, height: 52 }}>
            <SubscriptionsRounded />
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h6" className="two-lines">{details.uploaderName || 'Unknown channel'}</Typography>
            {subscriberCountLabel(details.uploaderSubscriberCount)
              && <Typography color="text.secondary">{subscriberCountLabel(details.uploaderSubscriberCount)}</Typography>}
          </Box>
        </Stack>
        <Stack direction="row" sx={{ gap: 2.5, alignItems: 'center', flexWrap: 'wrap' }}>
          {viewCountLabel(details.viewCount) && <Typography color="text.secondary">
            {viewCountLabel(details.viewCount)}
          </Typography>}
          {compactMetric(details.likeCount) && <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <ThumbUpRounded fontSize="small" /><Typography>{compactMetric(details.likeCount)}</Typography>
          </Stack>}
          {compactMetric(details.dislikeCount) && <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <ThumbDownRounded fontSize="small" /><Typography>{compactMetric(details.dislikeCount)}</Typography>
          </Stack>}
        </Stack>
      </Stack>
      <Stack direction="row" sx={{ mt: 3, gap: 1, flexWrap: 'wrap' }}>
        <Button startIcon={<PlaylistAddRounded />} variant="outlined" onClick={onAddToPlaylist}>Add to playlist</Button>
        <Button startIcon={<QueuePlayNextRounded />} variant="outlined" onClick={onPlayNext}>Play next</Button>
        <Button startIcon={<QueueMusicRounded />} variant="outlined" onClick={onAddToQueue}>Add to queue</Button>
        <Button startIcon={<DownloadRounded />} variant="outlined" onClick={onDownload}>Download</Button>
        {onAddNote && <Button startIcon={<NoteAddRounded />} variant="outlined" onClick={onAddNote}>Add note</Button>}
      </Stack>
    </CardContent>
    <Divider />
    <Tabs value={tab} onChange={(_event, value: VideoInfoTab) => setTab(value)} variant="fullWidth"
      aria-label="Video information">
      <Tab value="comments" icon={<ChatBubbleOutlineRounded />} iconPosition="start" label="Comments" />
      <Tab value="related" icon={<QueuePlayNextRounded />} iconPosition="start" label="Related items" />
      <Tab value="description" icon={<DescriptionRounded />} iconPosition="start" label="Description" />
    </Tabs>
    <Divider />
    <Box sx={{ p: { xs: 2.5, md: 4 } }}>
      {tab === 'related' && (details.relatedItems.length === 0
        ? <LibraryEmpty text="No related videos are available." />
        : <Box className="feed-video-grid">{details.relatedItems.map((item) => <FeedVideoCard
          key={`${item.serviceId}:${item.url}`} item={item} onOpen={() => void onOpen(item.url)} />)}</Box>)}
      {tab === 'description' && <Stack spacing={2}>
        {published && <Typography variant="h6">{published}</Typography>}
        {description ? <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{description}</Typography>
          : <Typography color="text.secondary">No description is available.</Typography>}
      </Stack>}
      {tab === 'comments' && <>
        {commentsLoading && <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}><CircularProgress /></Box>}
        {commentsError && <Alert severity="error" action={<Button color="inherit"
          onClick={() => setCommentsError(undefined)}>Retry</Button>}>{commentsError}</Alert>}
        {!commentsLoading && comments?.disabled && <Alert severity="info">Comments are disabled for this video.</Alert>}
        {!commentsLoading && comments && !comments.disabled && comments.items.length === 0
          && <Typography color="text.secondary">No comments are available.</Typography>}
        {!commentsLoading && comments && comments.items.length > 0 && <Stack spacing={1.5}>
          {comments.items.map((comment, index) => <CommentCard key={comment.id || `${comment.uploaderName}:${index}`}
            comment={comment} serviceId={details.serviceId} streamUrl={details.url} />)}
        </Stack>}
      </>}
    </Box>
  </Card>;
}

function CommentCard({ comment, serviceId, streamUrl }: {
  comment: CommentItem; serviceId: number; streamUrl: string;
}) {
  const age = publishedAgeLabel({
    type: 'COMMENT', serviceId, url: streamUrl, name: comment.uploaderName || 'Comment',
    publishedAt: comment.publishedAt, textualUploadDate: comment.textualUploadDate,
  });
  return <Card variant="outlined"><CardContent>
    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
      <Avatar src={comment.uploaderAvatarUrl} alt="" sx={{ width: 44, height: 44 }} />
      <Box sx={{ minWidth: 0, flexGrow: 1 }}>
        <Stack direction="row" sx={{ gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography sx={{ fontWeight: 650 }}>{comment.uploaderName || 'Unknown user'}</Typography>
          {comment.uploaderVerified && <Chip size="small" label="Verified" />}
          {age && <Typography color="text.secondary" variant="body2">· {age}</Typography>}
          {comment.pinned && <Chip size="small" label="Pinned" />}
        </Stack>
        {comment.text && <Typography sx={{ mt: 1, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
          {comment.text}
        </Typography>}
        <Stack direction="row" sx={{ mt: 1.5, gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
          {(comment.textualLikeCount || compactMetric(comment.likeCount)) && <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <ThumbUpRounded fontSize="small" />
            <Typography variant="body2">{comment.textualLikeCount || compactMetric(comment.likeCount)}</Typography>
          </Stack>}
          {comment.replyCount != null && <Typography color="primary" variant="body2" sx={{ fontWeight: 650 }}>
            {comment.replyCount} {comment.replyCount === 1 ? 'reply' : 'replies'}
          </Typography>}
          {comment.streamPosition != null && <Chip size="small" label={formatTimestamp(comment.streamPosition)} />}
          {comment.heartedByUploader && <Chip color="primary" size="small" label="Loved by channel" />}
        </Stack>
      </Box>
    </Stack>
  </CardContent></Card>;
}

function SubscriptionsPanel({ services, onOpen }: {
  services: ServiceSummary[];
  onOpen(url: string): Promise<void>;
}) {
  const [items, setItems] = useState<SubscriptionItem[]>([]);
  const [selectedChannel, setSelectedChannel] = useState<SubscriptionItem>();
  const [channelDetails, setChannelDetails] = useState<ChannelDetails>();
  const [channelLoading, setChannelLoading] = useState(false);
  const [editing, setEditing] = useState<SubscriptionItem>();
  const [open, setOpen] = useState(false);
  const [serviceId, setServiceId] = useState(0);
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [error, setError] = useState<string>();
  const [refreshingDetails, setRefreshingDetails] = useState(false);
  const refreshRunning = useRef(false);
  const load = useCallback(() => window.wizestream.backend.invoke<SubscriptionItem[]>('library.subscriptions.list').then(setItems).catch((reason: unknown) => setError(errorMessage(reason))), []);
  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (refreshRunning.current) return;
    const missing = items.filter((item) => (!item.avatarUrl || item.subscriberCount == null
      || item.subscriberCount < 0) && !attemptedSubscriptionMetadata.has(`${item.serviceId}:${item.url}`));
    if (missing.length === 0) return;
    refreshRunning.current = true;
    setRefreshingDetails(true);
    void (async () => {
      for (const item of missing) {
        const key = `${item.serviceId}:${item.url}`;
        attemptedSubscriptionMetadata.add(key);
        try {
          const refreshed = await window.wizestream.backend.invoke<Pick<SubscriptionItem, 'serviceId' | 'url' | 'avatarUrl' | 'subscriberCount'>>(
            'library.subscriptions.refresh-metadata', { serviceId: item.serviceId, url: item.url },
          );
          setItems((current) => current.map((candidate) => candidate.serviceId === refreshed.serviceId
            && candidate.url === refreshed.url ? {
              ...candidate,
              avatarUrl: refreshed.avatarUrl ?? candidate.avatarUrl,
              subscriberCount: refreshed.subscriberCount ?? candidate.subscriberCount,
            } : candidate));
        } catch {
          // Keep the standard channel placeholder when a service cannot provide an image.
        }
      }
      refreshRunning.current = false;
      setRefreshingDetails(false);
    })();
  }, [items]);

  function retryMissingDetails() {
    for (const item of items) {
      if (!item.avatarUrl || item.subscriberCount == null || item.subscriberCount < 0) {
        attemptedSubscriptionMetadata.delete(`${item.serviceId}:${item.url}`);
      }
    }
    setItems((current) => [...current]);
  }

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

  async function openChannel(item: SubscriptionItem) {
    setSelectedChannel(item); setChannelDetails(undefined); setChannelLoading(true); setError(undefined);
    try {
      setChannelDetails(await window.wizestream.backend.invoke<ChannelDetails>('channel.resolve', {
        serviceId: item.serviceId, url: item.url,
      }));
    } catch (reason) { setError(errorMessage(reason)); } finally { setChannelLoading(false); }
  }

  function closeChannel() {
    setSelectedChannel(undefined); setChannelDetails(undefined); setChannelLoading(false); setError(undefined);
  }

  if (selectedChannel) return <Stack spacing={3}>
    <Button startIcon={<ArrowBackRounded />} onClick={closeChannel} sx={{ alignSelf: 'flex-start' }}>
      Back to subscriptions
    </Button>
    {error && <Alert severity="error">{error}</Alert>}
    {channelLoading && <Stack direction="row" spacing={2} sx={{ alignItems: 'center', py: 6, justifyContent: 'center' }}>
      <CircularProgress /><Typography color="text.secondary">Loading channel…</Typography>
    </Stack>}
    {!channelLoading && channelDetails && <>
      <Card variant="outlined" sx={{ overflow: 'hidden' }}>
        {channelDetails.bannerUrl && <Box component="img" src={channelDetails.bannerUrl} alt="" className="channel-banner" />}
        <CardContent sx={{ p: 3 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ alignItems: { xs: 'center', sm: 'flex-start' } }}>
            <Avatar src={channelDetails.avatarUrl || selectedChannel.avatarUrl} alt=""
              sx={{ width: 112, height: 112, flexShrink: 0 }}><SubscriptionsRounded sx={{ fontSize: 48 }} /></Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h4">{channelDetails.name || selectedChannel.name}</Typography>
              {subscriberCountLabel(channelDetails.subscriberCount ?? selectedChannel.subscriberCount)
                && <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                  {subscriberCountLabel(channelDetails.subscriberCount ?? selectedChannel.subscriberCount)}
                </Typography>}
              {channelDetails.description && <Typography sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>
                {channelDetails.description}
              </Typography>}
            </Box>
          </Stack>
        </CardContent>
      </Card>
      <Typography variant="h5">Recent videos</Typography>
      {channelDetails.streams.length === 0 ? <LibraryEmpty text="No recent videos are available for this channel." />
        : <Box className="feed-video-grid">{channelDetails.streams.map((stream) => <FeedVideoCard key={stream.url}
          item={stream} onOpen={() => void onOpen(stream.url)} />)}</Box>}
    </>}
  </Stack>;

  return <Stack spacing={3}><PanelHeader title="Subscriptions" description="Manage channels synchronized with your Android devices." action={<Stack direction="row" spacing={1}>
    <Button startIcon={refreshingDetails ? <CircularProgress size={18} /> : <ReplayRounded />} variant="outlined"
      disabled={refreshingDetails || items.length === 0 || items.every((item) => item.avatarUrl
        && item.subscriberCount != null && item.subscriberCount >= 0)}
      onClick={retryMissingDetails}>{refreshingDetails ? 'Loading channel details…' : 'Refresh channel details'}</Button>
    <Button startIcon={<AddRounded />} variant="contained" onClick={() => showEditor()}>Add channel</Button>
  </Stack>} />
    {error && <Alert severity="error">{error}</Alert>}
    {items.length === 0 ? <LibraryEmpty text="No subscriptions yet." /> : <Box className="subscription-grid">{items.map((item) => <Card key={`${item.serviceId}:${item.url}`} variant="outlined" sx={{ display: 'flex', flexDirection: 'column' }}>
      <CardActionArea onClick={() => void openChannel(item)} aria-label={`Open ${item.name}`} sx={{ flexGrow: 1 }}>
        <CardContent sx={{ p: 3, textAlign: 'center', height: '100%', boxSizing: 'border-box' }}>
          <Avatar src={item.avatarUrl} alt="" sx={{ width: 88, height: 88, mx: 'auto', mb: 2 }}><SubscriptionsRounded sx={{ fontSize: 38 }} /></Avatar>
          <Typography variant="h6" className="two-lines" sx={{ minHeight: '3em' }}>{item.name}</Typography>
          {subscriberCountLabel(item.subscriberCount) && <Typography color="text.secondary" sx={{ mt: 0.5 }}>{subscriberCountLabel(item.subscriberCount)}</Typography>}
          <Typography color="text.secondary" variant="body2" className="two-lines" sx={{ mt: 1, overflowWrap: 'anywhere' }}>{item.url}</Typography>
        </CardContent>
      </CardActionArea>
      <Box sx={{ borderTop: 1, borderColor: 'divider' }}>
        <Stack direction="row" sx={{ justifyContent: 'center', py: 0.5 }}>
          <Tooltip title="Edit"><IconButton aria-label="Edit subscription" onClick={() => showEditor(item)}><EditRounded /></IconButton></Tooltip>
          <Tooltip title="Remove"><IconButton aria-label="Delete subscription" onClick={() => void remove(item)}><DeleteOutlineRounded /></IconButton></Tooltip>
        </Stack>
      </Box>
    </Card>)}</Box>}
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm"><DialogTitle>{editing ? 'Edit subscription' : 'Add subscription'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField select label="Service" value={serviceId} disabled={Boolean(editing)} onChange={(event) => setServiceId(Number(event.target.value))}>{services.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}</TextField><TextField label="Channel name" value={name} onChange={(event) => setName(event.target.value)} /><TextField label="Channel URL" value={url} disabled={Boolean(editing)} onChange={(event) => setUrl(event.target.value)} /><TextField label="Avatar URL (optional)" value={avatarUrl} onChange={(event) => setAvatarUrl(event.target.value)} />{error && <Alert severity="error">{error}</Alert>}</Stack></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" disabled={!name.trim() || !url.trim()} onClick={() => void save()}>Save</Button></DialogActions></Dialog>
  </Stack>;
}

function PlaylistsPanel({ currentStream, onOpen }: { currentStream?: LibraryStream; onOpen(url: string): Promise<void> }) {
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [items, setItems] = useState<PlaylistItem[]>([]);
  const [dialog, setDialog] = useState<'create' | 'rename'>();
  const [editingId, setEditingId] = useState<string>();
  const [name, setName] = useState('');
  const [error, setError] = useState<string>();
  const loadPlaylists = useCallback(async () => {
    try {
      const value = await window.wizestream.backend.invoke<PlaylistSummary[]>('library.playlists.list');
      setPlaylists(value);
      setSelectedId((current) => current && value.some((item) => item.id === current) ? current : undefined);
    } catch (reason) { setError(errorMessage(reason)); }
  }, []);
  const loadItems = useCallback(async (id?: string) => {
    if (!id) { setItems([]); return; }
    setItems([]);
    try { setItems(await window.wizestream.backend.invoke<PlaylistItem[]>('library.playlists.items', { playlistId: id })); }
    catch (reason) { setError(errorMessage(reason)); }
  }, []);
  useEffect(() => { void loadPlaylists(); }, [loadPlaylists]);
  useEffect(() => { void loadItems(selectedId); }, [loadItems, selectedId]);
  const selectedPlaylist = playlists.find((item) => item.id === selectedId);

  async function savePlaylist() {
    try {
      if (dialog === 'create') await window.wizestream.backend.invoke('library.playlists.create', { name });
      else if (editingId) await window.wizestream.backend.invoke('library.playlists.rename', { id: editingId, name });
      setDialog(undefined); setEditingId(undefined); setName(''); await loadPlaylists();
    } catch (reason) { setError(errorMessage(reason)); }
  }
  async function deletePlaylist(playlist: PlaylistSummary) {
    if (!window.confirm(`Delete ${playlist.name} and all of its items?`)) return;
    try {
      await window.wizestream.backend.invoke('library.playlists.delete', { id: playlist.id });
      if (selectedId === playlist.id) setSelectedId(undefined);
      await loadPlaylists();
    }
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
  function showEditor(mode: 'create' | 'rename', playlist?: PlaylistSummary) {
    setEditingId(playlist?.id);
    setName(playlist?.name ?? '');
    setDialog(mode);
  }
  return <Stack spacing={3}><PanelHeader title="Playlists" description="Create local playlists and edit their synchronized items." action={<Button startIcon={<AddRounded />} variant="contained" onClick={() => showEditor('create')}>New playlist</Button>} />
    {error && <Alert severity="error">{error}</Alert>}
    {!selectedPlaylist
      ? playlists.length === 0 ? <LibraryEmpty text="No playlists yet. Create one to start collecting videos." />
        : <Box className="playlist-grid">{playlists.map((playlist) => <PlaylistCard key={playlist.id}
          playlist={playlist} onOpen={() => setSelectedId(playlist.id)} onRename={() => showEditor('rename', playlist)}
          onDelete={() => void deletePlaylist(playlist)} />)}</Box>
      : <Stack spacing={3}>
        <Button startIcon={<ArrowBackRounded />} onClick={() => setSelectedId(undefined)} sx={{ alignSelf: 'flex-start' }}>Back to playlists</Button>
        <Card variant="outlined"><CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} sx={{ alignItems: { xs: 'stretch', md: 'center' } }}>
            <PlaylistCover playlist={selectedPlaylist} compact />
            <Box sx={{ flexGrow: 1, minWidth: 0 }}>
              <Typography variant="h4" className="two-lines">{selectedPlaylist.name}</Typography>
              <Typography color="text.secondary" sx={{ mt: 0.5 }}>{playlistItemCountLabel(selectedPlaylist.itemCount)}</Typography>
              {currentStream && <Typography color="text.secondary" variant="body2" className="two-lines" sx={{ mt: 1.5 }}>Ready to add: {currentStream.title}</Typography>}
            </Box>
            <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap', justifyContent: { xs: 'flex-start', md: 'flex-end' } }}>
              <Tooltip title="Rename"><IconButton aria-label="Rename playlist" onClick={() => showEditor('rename', selectedPlaylist)}><EditRounded /></IconButton></Tooltip>
              <Tooltip title="Delete playlist"><IconButton aria-label="Delete playlist" onClick={() => void deletePlaylist(selectedPlaylist)}><DeleteOutlineRounded /></IconButton></Tooltip>
              <Button startIcon={<PlaylistAddRounded />} variant="contained" disabled={!currentStream} onClick={() => void addCurrent()}>Add current</Button>
            </Stack>
          </Stack>
        </CardContent></Card>
        {items.length === 0 ? <LibraryEmpty text="This playlist is empty. Select a stream in What's New, then return here to add it." />
          : <Box className="playlist-item-grid">{items.map((item) => <PlaylistItemCard key={item.itemId} item={item}
            onOpen={() => void onOpen(item.url)} onRemove={() => void removeItem(item.itemId)} />)}</Box>}
      </Stack>}
    <Dialog open={Boolean(dialog)} onClose={() => { setDialog(undefined); setEditingId(undefined); }} fullWidth maxWidth="xs"><DialogTitle>{dialog === 'rename' ? 'Rename playlist' : 'New playlist'}</DialogTitle><DialogContent><TextField autoFocus fullWidth label="Name" value={name} onChange={(event) => setName(event.target.value)} sx={{ mt: 1 }} /></DialogContent><DialogActions><Button onClick={() => { setDialog(undefined); setEditingId(undefined); }}>Cancel</Button><Button variant="contained" disabled={!name.trim()} onClick={() => void savePlaylist()}>Save</Button></DialogActions></Dialog>
  </Stack>;
}

function PlaylistCard({ playlist, onOpen, onRename, onDelete }: {
  playlist: PlaylistSummary; onOpen(): void; onRename(): void; onDelete(): void;
}) {
  return <Card variant="outlined" className="playlist-card">
    <CardActionArea onClick={onOpen} aria-label={`Open ${playlist.name}`}>
      <PlaylistCover playlist={playlist} />
      <CardContent>
        <Typography variant="h6" className="two-lines">{playlist.name}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>{playlistItemCountLabel(playlist.itemCount)}</Typography>
      </CardContent>
    </CardActionArea>
    <Divider />
    <Stack direction="row" sx={{ p: 1, alignItems: 'center' }}>
      <Button startIcon={<PlaylistPlayRounded />} onClick={onOpen}>Open</Button>
      <Box sx={{ flexGrow: 1 }} />
      <Tooltip title="Rename"><IconButton aria-label={`Rename ${playlist.name}`} onClick={onRename}><EditRounded /></IconButton></Tooltip>
      <Tooltip title="Delete"><IconButton aria-label={`Delete ${playlist.name}`} onClick={onDelete}><DeleteOutlineRounded /></IconButton></Tooltip>
    </Stack>
  </Card>;
}

function PlaylistCover({ playlist, compact = false }: { playlist: PlaylistSummary; compact?: boolean }) {
  return <Box className={`playlist-cover${compact ? ' playlist-cover-compact' : ''}`}>
    {playlist.thumbnailUrl
      ? <Box component="img" src={playlist.thumbnailUrl} alt="" className="playlist-cover-image" />
      : <PlaylistPlayRounded className="playlist-cover-placeholder" />}
    <Chip size="small" icon={<PlaylistPlayRounded />} label={playlistItemCountLabel(playlist.itemCount)} className="playlist-count-badge" />
  </Box>;
}

function PlaylistItemCard({ item, onOpen, onRemove }: { item: PlaylistItem; onOpen(): void; onRemove(): void }) {
  return <Card variant="outlined" className="playlist-item-card">
    <CardActionArea onClick={onOpen} aria-label={`Play ${item.title}`}>
      <Box className="feed-video-cover">
        {item.thumbnailUrl ? <Box component="img" src={item.thumbnailUrl} alt="" className="result-thumbnail" />
          : <Box className="result-thumbnail playlist-item-placeholder"><PlayArrowRounded /></Box>}
        {item.duration > 0 && <Chip size="small" label={formatTimestamp(item.duration)} className="feed-video-badge" />}
      </Box>
      <CardContent>
        <Typography variant="h6" className="two-lines">{item.title}</Typography>
        <Typography color="text.secondary" className="two-lines" sx={{ mt: 0.5 }}>{item.uploader || 'Unknown channel'}</Typography>
      </CardContent>
    </CardActionArea>
    <Divider />
    <Stack direction="row" sx={{ p: 1, alignItems: 'center' }}>
      <Button startIcon={<PlayArrowRounded />} onClick={onOpen}>Play</Button>
      <Box sx={{ flexGrow: 1 }} />
      <Tooltip title="Remove from playlist"><IconButton aria-label="Remove playlist item" onClick={onRemove}><DeleteOutlineRounded /></IconButton></Tooltip>
    </Stack>
  </Card>;
}

function playlistItemCountLabel(count: number) {
  return `${count} item${count === 1 ? '' : 's'}`;
}

function HistoryPanel({ onOpen }: { onOpen(url: string, startPosition?: number): Promise<void> }) {
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
    <Stack spacing={2}><PanelHeader title="Watch history" description="Playback records are added when mpv starts." action={<Button startIcon={<DeleteOutlineRounded />} variant="outlined" color="error" disabled={items.length === 0} onClick={() => void clear()}>Clear watch history</Button>} />{items.length === 0 ? <LibraryEmpty text="No watch history yet. Playing a stream adds it here." /> : <Box className="history-grid">{items.map((item) => <HistoryVideoCard key={item.id} item={item} onOpen={() => void onOpen(item.url, historyResumePosition(item.positionSeconds, item.duration))} onDelete={() => void remove(item.id)} />)}</Box>}</Stack>
    <Stack spacing={2}><PanelHeader title="Search history" description="Successful extractor searches are retained locally." action={<Button startIcon={<DeleteOutlineRounded />} variant="outlined" color="error" disabled={searches.length === 0} onClick={() => void clearSearches()}>Clear searches</Button>} />{searches.length === 0 ? <LibraryEmpty text="No search history yet." /> : <Card variant="outlined"><List disablePadding>{searches.map((item, index) => <Box key={item.id}>{index > 0 && <Divider />}<ListItem secondaryAction={<IconButton aria-label="Delete search entry" onClick={() => void removeSearch(item.id)}><DeleteOutlineRounded /></IconButton>}><ListItemAvatar><Avatar><SearchRounded /></Avatar></ListItemAvatar><ListItemText primary={item.query} secondary={new Date(item.searchedAt).toLocaleString()} /></ListItem></Box>)}</List></Card>}</Stack>
  </Stack>;
}

function HistoryVideoCard({ item, onOpen, onDelete }: {
  item: HistoryItem; onOpen(): void; onDelete(): void;
}) {
  const position = Math.max(0, item.positionSeconds);
  const resumeAt = historyResumePosition(item.positionSeconds, item.duration);
  const progress = item.duration > 0 ? Math.min(100, position / item.duration * 100) : 0;
  return <Card variant="outlined" className="history-video-card">
    <CardActionArea onClick={onOpen} aria-label={`Open ${item.title}`}>
      <Box className="feed-video-cover">
        {item.thumbnailUrl
          ? <Box component="img" src={item.thumbnailUrl} alt="" className="result-thumbnail" />
          : <Box className="result-thumbnail" />}
        {item.duration > 0 && <Chip size="small" label={formatTimestamp(item.duration)} className="feed-video-badge" />}
        {progress > 0 && <LinearProgress variant="determinate" value={progress} className="history-progress" />}
      </Box>
      <CardContent>
        <Typography variant="h6" className="two-lines">{item.title}</Typography>
        <Typography color="text.secondary" className="two-lines" sx={{ mt: 0.5 }}>
          {item.uploader || 'Unknown channel'}
        </Typography>
        <Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>
          Watched {new Date(item.watchedAt).toLocaleString()}
        </Typography>
        <Stack direction="row" sx={{ mt: 1.5, alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
          <Typography className="mono history-time" variant="body2">{formatTimestamp(position)}</Typography>
          <Typography color="text.secondary" variant="body2">of</Typography>
          <Typography className="mono history-time" variant="body2">{formatTimestamp(item.duration)}</Typography>
        </Stack>
      </CardContent>
    </CardActionArea>
    <Divider />
    <Stack direction="row" sx={{ p: 1, alignItems: 'center' }}>
      <Button startIcon={resumeAt > 5 ? <ReplayRounded /> : <PlayArrowRounded />} onClick={onOpen}>
        {resumeAt > 5 ? 'Resume' : position > 5 ? 'Play again' : 'Play'}
      </Button>
      <Box sx={{ flexGrow: 1 }} />
      <Tooltip title="Delete from watch history"><IconButton aria-label="Delete history entry" onClick={onDelete}>
        <DeleteOutlineRounded />
      </IconButton></Tooltip>
    </Stack>
  </Card>;
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
  return <Stack spacing={3}><PanelHeader title="Learning notes" description={currentStream ? `Current stream: ${currentStream.title}` : "Select a stream in What's New to create a timestamped note."} action={<Button startIcon={<NoteAddRounded />} variant="contained" disabled={!currentStream} onClick={() => showEditor()}>Add note</Button>} />{error && <Alert severity="error">{error}</Alert>}{notes.length === 0 ? <LibraryEmpty text="No Learning Mode notes yet." /> : <Box className="note-grid">{notes.map((item) => <Card key={item.id} variant="outlined"><CardActionArea onClick={() => void onOpen(item.url)}><CardContent><Stack direction="row" sx={{ alignItems: 'start', gap: 1 }}><Box sx={{ flexGrow: 1 }}><Typography variant="overline">{formatTimestamp(item.positionSeconds)}</Typography><Typography variant="h6" className="two-lines">{item.title}</Typography></Box><IconButton aria-label="Edit note" onClick={(event) => { event.stopPropagation(); showEditor(item); }}><EditRounded /></IconButton><IconButton aria-label="Delete note" onClick={(event) => { event.stopPropagation(); void remove(item.id); }}><DeleteOutlineRounded /></IconButton></Stack><Typography sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>{item.note}</Typography><Typography color="text.secondary" variant="caption" sx={{ display: 'block', mt: 2 }}>Updated {new Date(item.updatedAt).toLocaleString()}</Typography></CardContent></CardActionArea></Card>)}</Box>}
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
      {!currentStream ? <Typography color="text.secondary" sx={{ mt: 1 }}>Select a stream in What&apos;s New first.</Typography>
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
    <Card variant="outlined"><CardContent sx={{ p: 4 }}><Typography variant="h6">Pair a device</Typography><Stack spacing={2} sx={{ mt: 2 }}><Button variant="outlined" disabled={busy} onClick={() => void createInvitation()}>Show pairing code</Button><TextField label="Code from another WizeStream device" value={pairingCode} multiline minRows={3} onChange={(event) => setPairingCode(event.target.value)} /><Button variant="contained" disabled={busy || !pairingCode.trim()} onClick={() => void pairDevice()}>Pair device</Button></Stack></CardContent></Card>
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
    <Dialog open={Boolean(invitation)} onClose={() => setInvitation('')} fullWidth maxWidth="sm">
      <DialogTitle>Show pairing code</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ alignItems: 'center', pt: 1 }}>
          {invitation && <Box className="pairing-qr-surface">
            <QRCodeSVG value={invitation} size={360} level="M" marginSize={4} bgColor="#FFFFFF" fgColor="#000000"
              title="One-time device pairing QR code" className="pairing-qr-code" />
          </Box>}
          <Typography>PeerID: <Box component="span" className="mono">{abbreviatePeerId(sync.peerId)}</Box></Typography>
          <Typography color="text.secondary" variant="body2" sx={{ textAlign: 'center', maxWidth: 480 }}>
            Keep this window open while the other device scans. This code expires after five minutes and can be used only once.
          </Typography>
          <TextField fullWidth label="One-time pairing code for manual copy" value={invitation} multiline minRows={3}
            slotProps={{ input: { readOnly: true } }} />
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={() => setInvitation('')}>Close</Button></DialogActions>
    </Dialog>
  </Stack>;
}

function abbreviatePeerId(peerId: string) {
  if (peerId.length <= 16) return peerId;
  return `${peerId.slice(0, 8)}…${peerId.slice(-8)}`;
}

function PanelHeader({ title, description, action }: { title: string; description: string; action: React.ReactNode }) {
  return <Stack direction="row" sx={{ alignItems: 'center', gap: 2 }}><Box sx={{ flexGrow: 1 }}><Typography variant="h4">{title}</Typography><Typography color="text.secondary">{description}</Typography></Box>{action}</Stack>;
}

function LibraryEmpty({ text }: { text: string }) {
  return <Card variant="outlined"><CardContent sx={{ p: 6, textAlign: 'center' }}><Typography color="text.secondary">{text}</Typography></CardContent></Card>;
}

function detailsToLibraryStream(value: StreamDetails): LibraryStream {
  return { serviceId: value.serviceId, url: value.url, title: value.name, duration: value.duration,
    streamType: value.streamType, uploader: value.uploaderName, uploaderUrl: value.uploaderUrl,
    thumbnailUrl: value.thumbnailUrl };
}

function isLiveStream(value: Pick<StreamDetails, 'streamType'>) {
  return value.streamType === 'LIVE_STREAM' || value.streamType === 'AUDIO_LIVE_STREAM';
}

function compactMetric(value?: number | null): string | undefined {
  if (value == null || !Number.isSafeInteger(value) || value < 0) return undefined;
  return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })
    .format(value).replace('K', 'k').replace('M', 'm').replace('B', 'b');
}

function plainDescription(value?: string, type?: number): string {
  if (!value) return '';
  if (type !== 1) return value;
  const document = new DOMParser().parseFromString(value.replace(/<br\s*\/?>/gi, '\n'), 'text/html');
  return document.body.textContent?.trim() ?? '';
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
