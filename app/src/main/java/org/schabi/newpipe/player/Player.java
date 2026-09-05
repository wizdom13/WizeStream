package org.schabi.newpipe.player;

import static androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_INTERNAL;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static androidx.media3.common.Player.DISCONTINUITY_REASON_SKIP;
import static androidx.media3.common.Player.DiscontinuityReason;
import static androidx.media3.common.Player.Listener;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;
import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static androidx.media3.common.Player.RepeatMode;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import androidx.core.math.MathUtils;
import androidx.preference.PreferenceManager;

import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.equalizer.EqualizerState;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager;
import org.schabi.newpipe.player.helper.CustomRenderersFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.SleepTimer;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType;
import org.schabi.newpipe.player.ui.BackgroundPlayerUi;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.PlayerUiList;
import org.schabi.newpipe.player.ui.PopupPlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class Player implements PlaybackListener, Listener {
    public static final boolean DEBUG = MainActivity.DEBUG;
    public static final String TAG = Player.class.getSimpleName();

    /*//////////////////////////////////////////////////////////////////////////
    // States
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_PREFLIGHT = -1;
    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String PLAYBACK_QUALITY = "playback_quality";
    public static final String PLAY_QUEUE_KEY = "play_queue_key";
    public static final String RESUME_PLAYBACK = "resume_playback";
    public static final String PLAY_WHEN_READY = "play_when_ready";
    public static final String PLAYER_TYPE = "player_type";
    public static final String PLAYBACK_PRESENTATION_MODE = "playback_presentation_mode";
    public static final String PLAYER_INTENT_TYPE = "player_intent_type";
    public static final String PLAYER_INTENT_DATA = "player_intent_data";

    /*//////////////////////////////////////////////////////////////////////////
    // Time constants
    //////////////////////////////////////////////////////////////////////////*/

    public static final int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    public static final int PROGRESS_LOOP_INTERVAL_MILLIS = 1000; // 1 second

    /*//////////////////////////////////////////////////////////////////////////
    // Other constants
    //////////////////////////////////////////////////////////////////////////*/

    public static final int RENDERER_UNAVAILABLE = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    // play queue might be null e.g. while player is starting
    @Nullable
    private PlayQueue playQueue;

    @Nullable
    private MediaSourceManager playQueueManager;

    @Nullable
    private PlayQueueItem currentItem;
    @Nullable
    private MediaItemTag currentMetadata;
    @NonNull
    private final SponsorBlockPlaybackController sponsorBlockController;
    @NonNull
    private final PlaybackParametersController playbackParametersController;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    private ExoPlayer simpleExoPlayer;
    private AudioReactor audioReactor;
    @NonNull
    private final PlayerAudioController audioController;

    @NonNull
    private final DefaultTrackSelector trackSelector;
    @NonNull
    private final LoadController loadController;
    @NonNull
    private final DefaultRenderersFactory renderFactory;
    @NonNull
    private final VisualizerAudioProcessor visualizerAudioProcessor;

    @NonNull
    private final VideoPlaybackResolver videoResolver;
    @NonNull
    private final AudioPlaybackResolver audioResolver;

    private final PlayerService service; //TODO try to remove and replace everything with context

    /*//////////////////////////////////////////////////////////////////////////
    // Player states
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerType playerType = PlayerType.MAIN;
    private int currentState = STATE_PREFLIGHT;
    private final PopupPlayerReturnState popupPlayerReturnState =
            new PopupPlayerReturnState();

    @NonNull
    private final SleepTimerPlaybackController sleepTimerController;
    @NonNull
    private final PlayerQueueModeController queueModeController;
    // audio only mode does not mean that player type is background, but that the player was
    // minimized to background but will resume automatically to the original player type
    private boolean isAudioOnly = false;
    @NonNull
    private PlaybackPresentationMode playbackPresentationMode = PlaybackPresentationMode.VIDEO;
    private boolean isPrepared = false;

    /*//////////////////////////////////////////////////////////////////////////
    // UIs, listeners and disposables
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressWarnings({"MemberName", "java:S116"}) // keep the unusual member name
    private final PlayerUiList UIs;

    @NonNull
    private final PlayerBroadcastController broadcastController;
    @NonNull
    private final PlayerEventDispatcher eventDispatcher;
    @NonNull
    private final PlayerErrorController errorController;
    @NonNull
    private final PlayerThumbnailController thumbnailController;
    @NonNull
    private final PlayerLocalMetadataController localMetadataController;
    @NonNull
    private final PlayerProgressController progressController;
    @NonNull
    private final PlayerSeekController seekController;
    @NonNull
    private final PlayerTransportController transportController;
    @NonNull
    private final CompositeDisposable streamItemDisposable = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    private final Context context;
    @NonNull
    private final SharedPreferences prefs;
    @NonNull
    private final PlayerHistoryController historyController;
    @NonNull
    private final PlayerDataSource dataSource;

    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor

    /**
     * @param service the service this player resides in
     * @param mediaSession used to build the {@link MediaSessionPlayerUi}, lives in the service and
     *                     could possibly be reused with multiple player instances
     * @param browserPlayer lightweight player used by the media library while no playback exists
     */
    public Player(@NonNull final PlayerService service,
                  @NonNull final MediaLibrarySession mediaSession,
                  @NonNull final androidx.media3.common.Player browserPlayer) {
        this.service = service;
        context = service;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        audioController = new PlayerAudioController(this);
        historyController = new PlayerHistoryController(this);
        sponsorBlockController = new SponsorBlockPlaybackController(this);
        playbackParametersController = new PlaybackParametersController(this);
        sleepTimerController = new SleepTimerPlaybackController(this);
        queueModeController = new PlayerQueueModeController(this, sleepTimerController);
        eventDispatcher = new PlayerEventDispatcher(this);
        progressController = new PlayerProgressController(this, historyController,
                sponsorBlockController, eventDispatcher);
        seekController = new PlayerSeekController(this);
        transportController = new PlayerTransportController(this, sleepTimerController);
        thumbnailController = new PlayerThumbnailController(this);
        localMetadataController = new PlayerLocalMetadataController(this);
        broadcastController = new PlayerBroadcastController(this);

        trackSelector = new DefaultTrackSelector(context, PlayerHelper.getQualitySelector());
        dataSource = new PlayerDataSource(context,
                new DefaultBandwidthMeter.Builder(context).build());
        loadController = new LoadController();

        visualizerAudioProcessor = new VisualizerAudioProcessor();
        final boolean useCustomVideoRenderer = prefs.getBoolean(
                context.getString(
                        R.string.always_use_exoplayer_set_output_surface_workaround_key), false);
        renderFactory = new CustomRenderersFactory(context, useCustomVideoRenderer,
                visualizerAudioProcessor);

        renderFactory.setEnableDecoderFallback(
                prefs.getBoolean(
                        context.getString(
                                R.string.use_exoplayer_decoder_fallback_key), false));

        videoResolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
        audioResolver = new AudioPlaybackResolver(context, dataSource);
        errorController = new PlayerErrorController(this, eventDispatcher, videoResolver);

        // The UIs added here should always be present. They will be initialized when the player
        // reaches the initialization step. Make sure the media session ui is before the
        // notification ui in the UIs list, since the notification depends on the media session in
        // PlayerUi#initPlayer(), and UIs.call() guarantees UI order is preserved.
        UIs = new PlayerUiList(
                new MediaSessionPlayerUi(this, mediaSession, browserPlayer),
                new NotificationPlayerUi(this)
        );
    }

    private VideoPlaybackResolver.QualityResolver getQualityResolver() {
        return new VideoPlaybackResolver.QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
                return videoPlayerSelected()
                        ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                  final String playbackQuality) {
                return videoPlayerSelected()
                        ? getResolutionIndex(context, sortedVideos, playbackQuality)
                        : getPopupResolutionIndex(context, sortedVideos, playbackQuality);
            }
        };
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback initialization via intent
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback initialization via intent

    @SuppressWarnings("MethodLength")
    public void handleIntent(@NonNull final Intent intent) {
        final var playerIntentType = IntentCompat.getSerializableExtra(intent, PLAYER_INTENT_TYPE,
                PlayerIntentType.class);
        if (playerIntentType == null) {
            return;
        }
        // TODO: this should be in the second switch below, but I’m not sure whether I
        // can move the initUIs stuff without breaking the setup for edge cases somehow.
        // when playing from a timestamp, keep the current player as-is.
        if (playerIntentType != PlayerIntentType.TimestampChange) {
            historyController.updateLearningSession();
            @Nullable final PlayerType requestedPlayerType = IntentCompat.getSerializableExtra(
                    intent, PLAYER_TYPE, PlayerType.class);
            if (playerType == PlayerType.MAIN && requestedPlayerType == PlayerType.POPUP
                    && !popupPlayerReturnState.isRemembered()) {
                popupPlayerReturnState.remember(UIs.get(MainPlayerUi.class)
                        .map(MainPlayerUi::isFullscreen)
                        .orElse(false));
            }
            playerType = requestedPlayerType;
            historyController.updateLearningSession();
        }
        initUIsForCurrentPlayerType();
        isAudioOnly = audioPlayerSelected();
        if (playerIntentType != PlayerIntentType.TimestampChange) {
            final PlaybackPresentationMode requestedMode = IntentCompat.getSerializableExtra(
                    intent, PLAYBACK_PRESENTATION_MODE, PlaybackPresentationMode.class);
            playbackPresentationMode = requestedMode != null
                    ? requestedMode
                    : audioPlayerSelected()
                            ? PlaybackPresentationMode.AUDIO_BACKGROUND
                            : PlaybackPresentationMode.VIDEO;
            visualizerAudioProcessor.setEnabled(playbackPresentationMode.allowsVisualizer());
        }

        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            videoResolver.setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
        }

        final boolean playWhenReady = intent.getBooleanExtra(PLAY_WHEN_READY, true);

        switch (playerIntentType) {
            case Enqueue -> {
                if (playQueue != null) {
                    final PlayQueue newQueue = getPlayQueueFromCache(intent);
                    if (newQueue == null) {
                        return;
                    }
                    playQueue.append(newQueue.getStreams());
                    return;
                }

                // TODO: This falls through to the old logic, there was no playQueue
                // yet so we should start the player and add the new video
                break;
            }
            case EnqueueNext -> {
                if (playQueue != null) {
                    final PlayQueue newQueue = getPlayQueueFromCache(intent);
                    if (newQueue == null) {
                        return;
                    }
                    final PlayQueueItem newItem = newQueue.getStreams().get(0);
                    playQueue.enqueueNext(newItem, false);
                    return;
                }

                // TODO: This falls through to the old logic, there was no playQueue
                // yet so we should start the player and add the new video
                break;
            }
            case TimestampChange -> {
                final var data = Objects.requireNonNull(IntentCompat.getParcelableExtra(intent,
                        PLAYER_INTENT_DATA, TimestampChangeData.class));
                final Single<StreamInfo> single =
                        ExtractorHelper.getStreamInfo(data.getServiceId(), data.getUrl(), false);
                streamItemDisposable.add(single.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(info -> {
                            final @Nullable PlayQueue oldPlayQueue = playQueue;
                            info.setStartPosition(data.getSeconds());
                            final PlayQueueItem playQueueItem = new PlayQueueItem(info);

                            // If the stream is already playing,
                            // we can just seek to the appropriate timestamp
                            if (oldPlayQueue != null
                                    && playQueueItem.isSameItem(oldPlayQueue.getItem())) {
                                // Player can have state = IDLE when playback is stopped or failed
                                // and we should retry in this case
                                if (simpleExoPlayer.getPlaybackState()
                                        == androidx.media3.common.Player.STATE_IDLE) {
                                    simpleExoPlayer.prepare();
                                }
                                simpleExoPlayer.seekTo(oldPlayQueue.getIndex(),
                                        data.getSeconds() * 1000L);
                                simpleExoPlayer.setPlayWhenReady(playWhenReady);

                            } else {
                                final PlayQueue newPlayQueue;

                                // If there is no queue yet, just add our item
                                if (oldPlayQueue == null) {
                                    newPlayQueue = new SinglePlayQueue(playQueueItem);

                                // else we add the timestamped stream behind the current video
                                // and start playing it.
                                } else {
                                    oldPlayQueue.enqueueNext(playQueueItem, true);
                                    oldPlayQueue.offsetIndex(1);
                                    newPlayQueue = oldPlayQueue;
                                }
                                initPlayback(newPlayQueue, playWhenReady);
                            }

                        }, throwable -> {
                            // This will only show a snackbar if the passed context has a root view:
                            // otherwise it will resort to showing a notification, so we are safe
                            // here.
                            final var info = new ErrorInfo(throwable, UserAction.PLAY_ON_POPUP,
                                    data.getUrl(), null, data.getUrl());
                            ErrorUtil.createNotification(context, info);
                        }));
                return;
            }
            case AllOthers -> {
                // fallthrough; TODO: put other intent data in separate cases
            }
        }

        final PlayQueue newQueue = getPlayQueueFromCache(intent);
        if (newQueue == null) {
            return;
        }

        // branching parameters for below
        final boolean samePlayQueue = playQueue != null && playQueue.equalStreamsAndIndex(newQueue);

        /*
         * TODO As seen in #7427 this does not work:
         * There are 3 situations when playback shouldn't be started from scratch (zero timestamp):
         * 1. User pressed on a timestamp link and the same video should be rewound to the timestamp
         * 2. User changed a player from, for example. main to popup, or from audio to main, etc
         * 3. User chose to resume a video based on a saved timestamp from history of played videos
         * In those cases time will be saved because re-init of the play queue is a not an instant
         *  task and requires network calls
         * */
        // seek to timestamp if stream is already playing
        if (!exoPlayerIsNull()
                && newQueue.size() == 1 && newQueue.getItem() != null
                && playQueue != null && playQueue.size() == 1 && playQueue.getItem() != null
                && newQueue.getItem().isSameItem(playQueue.getItem())
                && newQueue.getItem().getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if (simpleExoPlayer.getPlaybackState()
                    == androidx.media3.common.Player.STATE_IDLE) {
                simpleExoPlayer.prepare();
            }
            simpleExoPlayer.seekTo(playQueue.getIndex(), newQueue.getItem().getRecoveryPosition());
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (!exoPlayerIsNull()
                && samePlayQueue
                && playQueue != null
                && !playQueue.isDisposed()) {
            // Do not re-init the same PlayQueue. Save time
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry in this case
            if (simpleExoPlayer.getPlaybackState()
                    == androidx.media3.common.Player.STATE_IDLE) {
                simpleExoPlayer.prepare();
            }
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (intent.getBooleanExtra(RESUME_PLAYBACK, false)
                && DependentPreferenceHelper.getResumePlaybackEnabled(context)
                // !samePlayQueue
                && (playQueue == null || !playQueue.equalStreamsAndIndex(newQueue))
                && !newQueue.isEmpty()
                && newQueue.getItem() != null
                && newQueue.getItem().getRecoveryPosition() == PlayQueueItem.RECOVERY_UNSET) {
            historyController.restoreStreamState(newQueue.getItem(),
                    state -> {
                        if (!state.isFinished(newQueue.getItem().getDuration())) {
                            // resume playback only if the stream was not played to the end
                            newQueue.setRecovery(newQueue.getIndex(), state.getProgressMillis());
                        }
                        initPlayback(newQueue, playWhenReady);
                    },
                    error -> {
                        if (DEBUG) {
                            Log.w(TAG, "Failed to start playback", error);
                        }
                        // In case any error we can start playback without history
                        initPlayback(newQueue, playWhenReady);
                    },
                    () -> {
                        // Completed but not found in history
                        initPlayback(newQueue, playWhenReady);
                    });
        } else {
            // Good to go...
            // In a case of equal PlayQueues we can re-init old one but only when it is disposed
            initPlayback(samePlayQueue ? playQueue : newQueue, playWhenReady);
        }

    }


    public void handleIntentPost(final PlayerType oldPlayerType) {
        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            reloadPlayQueueManager();
        }

        UIs.call(PlayerUi::setupAfterIntent);
        NavigationHelper.sendPlayerStartedEvent(context);
    }

    @Nullable
    private static PlayQueue getPlayQueueFromCache(@NonNull final Intent intent) {
        final String queueCache = intent.getStringExtra(PLAY_QUEUE_KEY);
        if (queueCache == null) {
            return null;
        }
        return SerializedCache.getInstance().take(queueCache, PlayQueue.class);
    }

    private void initUIsForCurrentPlayerType() {
        if ((UIs.get(MainPlayerUi.class).isPresent() && playerType == PlayerType.MAIN)
                || (UIs.get(BackgroundPlayerUi.class).isPresent() && playerType == PlayerType.AUDIO)
                || (UIs.get(PopupPlayerUi.class).isPresent() && playerType == PlayerType.POPUP)) {
            // correct UI already in place
            return;
        }

        // try to reuse binding if possible
        final PlayerBinding binding = UIs.get(VideoPlayerUi.class).map(VideoPlayerUi::getBinding)
                .orElseGet(() -> {
                    if (playerType == PlayerType.AUDIO) {
                        return null;
                    } else {
                        return PlayerBinding.inflate(LayoutInflater.from(context));
                    }
                });

        switch (playerType) {
            case MAIN:
                UIs.destroyAll(PopupPlayerUi.class);
                UIs.destroyAll(BackgroundPlayerUi.class);
                UIs.addAndPrepare(new MainPlayerUi(this, binding));
                break;
            case POPUP:
                UIs.destroyAll(MainPlayerUi.class);
                UIs.destroyAll(BackgroundPlayerUi.class);
                UIs.addAndPrepare(new PopupPlayerUi(this, binding));
                break;
            case AUDIO:
                UIs.destroyAll(VideoPlayerUi.class); // destroys both MainPlayerUi and PopupPlayerUi
                UIs.addAndPrepare(new BackgroundPlayerUi(this));
                break;
        }
    }

    private void initPlayback(@NonNull final PlayQueue queue,
                              final boolean playOnReady) {
        destroyPlayer();
        initPlayer(playOnReady);
        final boolean playbackSkipSilence = getPrefs().getBoolean(getContext().getString(
                R.string.playback_skip_silence_key), getPlaybackSkipSilence());
        final PlaybackParameters savedParameters =
                PlayerHelper.retrievePlaybackParametersFromPrefs(this);
        playbackParametersController.applyParameters(
                savedParameters.speed, savedParameters.pitch, playbackSkipSilence);

        playQueue = queue;
        playQueue.init();
        simpleExoPlayer.setShuffleModeEnabled(playQueue.isShuffled());
        sleepTimerController.onQueueReplaced();
        reloadPlayQueueManager();

        UIs.call(PlayerUi::initPlayback);

        applyPlayerVolume();
        notifyQueueUpdateToListeners();
        notifySleepTimerUpdateToListeners();
    }

    private void initPlayer(final boolean playOnReady) {
        if (DEBUG) {
            Log.d(TAG, "initPlayer() called with: playOnReady = [" + playOnReady + "]");
        }

        simpleExoPlayer = new ExoPlayer.Builder(context, renderFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadController)
                .setUsePlatformDiagnostics(false)
                .build();
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(playOnReady);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
        simpleExoPlayer.setHandleAudioBecomingNoisy(true);
        audioController.attachAudioSession(simpleExoPlayer.getAudioSessionId());

        audioReactor = new AudioReactor(context, simpleExoPlayer);

        broadcastController.register();

        // Setup UIs
        UIs.call(PlayerUi::initPlayer);

        updateAudioTunneling();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Destroy and recovery
    //////////////////////////////////////////////////////////////////////////*/
    //region Destroy and recovery

    private void destroyPlayer() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayer() called");
        }
        errorController.resetRecovery();
        historyController.stopLearningSession();
        UIs.call(PlayerUi::destroyPlayer);
        audioController.releaseAudioSession();

        if (!exoPlayerIsNull()) {
            simpleExoPlayer.removeListener(this);
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
        if (playQueue != null) {
            playQueue.dispose();
        }
        if (audioReactor != null) {
            audioReactor.dispose();
        }
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }

        thumbnailController.cancel();
        localMetadataController.cancel();
        sleepTimerController.clear();
        saveStreamProgressState();
        setRecovery();
        stopActivityBinding();

        destroyPlayer();
        broadcastController.unregister();

        historyController.clear();
        progressController.clear();
        streamItemDisposable.clear();

        UIs.destroyAll(Object.class); // destroy every UI: obviously every UI extends Object
    }

    public void setRecovery() {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int queuePos = playQueue.getIndex();
        final long windowPos = simpleExoPlayer.getCurrentPosition();
        final long duration = simpleExoPlayer.getDuration();

        // No checks due to https://github.com/TeamNewPipe/NewPipe/pull/7195#issuecomment-962624380
        setRecovery(queuePos, MathUtils.clamp(windowPos, 0, duration));
    }

    private void setRecovery(final int queuePos, final long windowPos) {
        if (playQueue == null || playQueue.size() <= queuePos) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        }
        playQueue.setRecovery(queuePos, windowPos);
    }

    public void reloadPlayQueueManager() {
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }

        if (playQueue != null) {
            playQueueManager = new MediaSourceManager(this, playQueue);
        }
    }

    @Override // own playback listener
    public void onPlaybackShutdown() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackShutdown() called");
        }
        // destroys the service, which in turn will destroy the player
        service.destroyPlayerAndStopService();
    }

    public void smoothStopForImmediateReusing() {
        // Pausing would make transition from one stream to a new stream not smooth, so only stop
        simpleExoPlayer.stop();
        setRecovery();
        UIs.call(PlayerUi::smoothStopForImmediateReusing);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback parameters
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback parameters

    public float getPlaybackSpeed() {
        return playbackParametersController.getSpeed();
    }

    public void setPlaybackSpeed(final float speed) {
        playbackParametersController.setSpeed(speed);
    }

    public void setPlaybackSpeedTemporarily(final float speed) {
        playbackParametersController.setSpeedTemporarily(speed);
    }

    public float getPlaybackPitch() {
        return playbackParametersController.getPitch();
    }

    public boolean getPlaybackSkipSilence() {
        return playbackParametersController.getSkipSilence();
    }

    public PlaybackParameters getPlaybackParameters() {
        return playbackParametersController.getParameters();
    }

    /**
     * Sets the playback parameters of the player, and also saves them to shared preferences.
     * Speed and pitch are rounded up to 2 decimal places before being used or saved.
     *
     * @param speed       the playback speed, will be rounded to up to 2 decimal places
     * @param pitch       the playback pitch, will be rounded to up to 2 decimal places
     * @param skipSilence skip silence during playback
     */
    public void setPlaybackParameters(final float speed, final float pitch,
                                      final boolean skipSilence) {
        playbackParametersController.setParameters(speed, pitch, skipSilence);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    public void startProgressLoop() {
        progressController.start();
    }

    private void stopProgressLoop() {
        progressController.stop();
    }

    public boolean isProgressLoopRunning() {
        return progressController.isRunning();
    }

    public void triggerProgressUpdate() {
        progressController.trigger();
    }

    boolean isPreparedForProgressUpdates() {
        return isPrepared;
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states
    @Override
    public void onPlayWhenReadyChanged(final boolean playWhenReady, final int reason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlayWhenReadyChanged() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "reason = [" + reason + "]");
        }
        final int playbackState = exoPlayerIsNull()
                ? androidx.media3.common.Player.STATE_IDLE
                : simpleExoPlayer.getPlaybackState();
        updatePlaybackState(playWhenReady, playbackState);
    }

    @Override
    public void onPlaybackStateChanged(final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlaybackStateChanged() called with: "
                    + "playbackState = [" + playbackState + "]");
        }
        updatePlaybackState(getPlayWhenReady(), playbackState);
    }

    private void updatePlaybackState(final boolean playWhenReady, final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - updatePlaybackState() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "playbackState = [" + playbackState + "]");
        }

        if (currentState == STATE_PAUSED_SEEK) {
            if (DEBUG) {
                Log.d(TAG, "updatePlaybackState() is currently blocked");
            }
            return;
        }

        switch (playbackState) {
            case androidx.media3.common.Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case androidx.media3.common.Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case androidx.media3.common.Player.STATE_READY: //3
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case androidx.media3.common.Player.STATE_ENDED: // 4
                sleepTimerController.onItemEnded(
                        playQueue == null ? null : playQueue.getItem(), false);
                changeState(STATE_COMPLETED);
                saveStreamProgressStateCompleted();
                isPrepared = false;
                break;
        }
    }

    @Override // exoplayer listener
    public void onIsLoadingChanged(final boolean isLoading) {
        if (!isLoading && currentState == STATE_PAUSED && isProgressLoopRunning()) {
            stopProgressLoop();
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    @Override // own playback listener
    public void onPlaybackBlock() {
        if (exoPlayerIsNull()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackBlock() called");
        }

        currentItem = null;
        currentMetadata = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override // own playback listener
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackUnblock() called");
        }

        if (exoPlayerIsNull()) {
            return;
        }
        if (currentState == STATE_BLOCKED) {
            changeState(STATE_BUFFERING);
        }
        simpleExoPlayer.setMediaSource(mediaSource, false);
        simpleExoPlayer.prepare();
    }

    public void changeState(final int state) {
        if (DEBUG) {
            Log.d(TAG, "changeState() called with: state = [" + state + "]");
        }
        currentState = state;
        historyController.updateLearningSession();
        switch (state) {
            case STATE_BLOCKED:
                onBlocked();
                break;
            case STATE_PLAYING:
                onPlaying();
                break;
            case STATE_BUFFERING:
                onBuffering();
                break;
            case STATE_PAUSED:
                onPaused();
                break;
            case STATE_PAUSED_SEEK:
                onPausedSeek();
                break;
            case STATE_COMPLETED:
                onCompleted();
                break;
        }
        notifyPlaybackUpdateToListeners();
    }

    private void onPrepared(final boolean playWhenReady) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        }

        UIs.call(PlayerUi::onPrepared);

        if (playWhenReady && !isMuted()) {
            audioReactor.requestAudioFocus();
        }
    }

    private void onBlocked() {
        if (DEBUG) {
            Log.d(TAG, "onBlocked() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        sponsorBlockController.hideManualSkipButton();
        UIs.call(PlayerUi::onBlocked);
    }

    private void onPlaying() {
        if (DEBUG) {
            Log.d(TAG, "onPlaying() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        UIs.call(PlayerUi::onPlaying);
    }

    void onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called");
        }

        UIs.call(PlayerUi::onBuffering);
    }

    private void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }

        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        sponsorBlockController.hideManualSkipButton();
        UIs.call(PlayerUi::onPaused);
    }

    private void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }
        UIs.call(PlayerUi::onPausedSeek);
    }

    private void onCompleted() {
        if (DEBUG) {
            Log.d(TAG, "onCompleted() called" + (playQueue == null ? ". playQueue is null" : ""));
        }
        if (playQueue == null) {
            return;
        }

        sponsorBlockController.hideManualSkipButton();
        UIs.call(PlayerUi::onCompleted);

        if (playQueue.getIndex() < playQueue.size() - 1) {
            playQueue.offsetIndex(+1);
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    //////////////////////////////////////////////////////////////////////////*/
    //region Repeat and shuffle

    @RepeatMode
    public int getRepeatMode() {
        return queueModeController.getRepeatMode();
    }

    public void cycleNextRepeatMode() {
        queueModeController.cycleNextRepeatMode();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        queueModeController.onRepeatModeChanged(repeatMode);
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        queueModeController.onShuffleModeEnabledChanged(shuffleModeEnabled);
    }

    public void toggleShuffleModeEnabled() {
        queueModeController.toggleShuffleModeEnabled();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Mute / Unmute
    //////////////////////////////////////////////////////////////////////////*/
    //region Mute / Unmute

    public void toggleMute() {
        audioController.toggleMute();
    }

    public boolean isMuted() {
        return audioController.isMuted();
    }

    @NonNull
    public EqualizerState getEqualizerState() {
        return audioController.getEqualizerState();
    }

    public boolean isEqualizerAvailable() {
        return audioController.isEqualizerAvailable();
    }

    public boolean isEqualizerOperational() {
        return audioController.isEqualizerOperational();
    }

    public void previewEqualizerState(@NonNull final EqualizerState state) {
        audioController.previewEqualizerState(state);
    }

    public void updateEqualizerState(@NonNull final EqualizerState state) {
        audioController.updateEqualizerState(state);
    }

    void updateAudioTunneling() {
        final boolean tunnelingEnabled = !prefs.getBoolean(
                context.getString(R.string.disable_media_tunneling_key), false)
                && !audioController.getEqualizerState().isEnabled()
                && !playbackPresentationMode.allowsVisualizer();
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTunnelingEnabled(tunnelingEnabled));
    }

    void applyPlayerVolume() {
        if (!exoPlayerIsNull()) {
            final float equalizerHeadroom = audioController.getEqualizerHeadroomMultiplier();
            simpleExoPlayer.setVolume(audioController.isMuted()
                    ? 0.0f : sleepTimerController.getVolumeMultiplier() * equalizerHeadroom);
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Sleep timer
    //////////////////////////////////////////////////////////////////////////*/
    //region Sleep timer

    public void startSleepTimer(final long durationMillis, final boolean fadeOut) {
        sleepTimerController.startDuration(durationMillis, fadeOut);
    }

    public boolean startSleepTimerAtEndOfCurrent(final boolean fadeOut) {
        return sleepTimerController.startAtEndOfCurrent(fadeOut);
    }

    public boolean startSleepTimerAtEndOfQueue(final boolean fadeOut) {
        return sleepTimerController.startAtEndOfQueue(fadeOut);
    }

    public void cancelSleepTimer() {
        sleepTimerController.cancel();
    }

    public long getSleepTimerRemainingMillis() {
        return sleepTimerController.remainingMillis();
    }

    public boolean isSleepTimerActive() {
        return sleepTimerController.isActive();
    }

    @NonNull
    public SleepTimer.Mode getSleepTimerMode() {
        return sleepTimerController.getMode();
    }

    public boolean isSleepTimerFadeOutEnabled() {
        return sleepTimerController.isFadeOutEnabled();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer listeners (that didn't fit in other categories)
    //////////////////////////////////////////////////////////////////////////*/
    //region ExoPlayer listeners (that didn't fit in other categories)

    @Override
    public void onAudioSessionIdChanged(final int audioSessionId) {
        audioController.onAudioSessionChanged(audioSessionId);
    }

    /**
     * <p>Listens for event or state changes on ExoPlayer. When any event happens, we check for
     * changes in the currently-playing metadata and update the encapsulating
     * {@link Player}. Downstream listeners are also informed.</p>
     *
     * <p>When the renewed metadata contains any error, it is reported as a notification.
     * This is done because not all source resolution errors are {@link PlaybackException}, which
     * are also captured by {@link ExoPlayer} and stops the playback.</p>
     *
     * @param player The {@link androidx.media3.common.Player} whose state changed.
     * @param events The {@link androidx.media3.common.Player.Events} that has triggered
     *               the player state changes.
     **/
    @Override
    public void onEvents(@NonNull final androidx.media3.common.Player player,
                         @NonNull final androidx.media3.common.Player.Events events) {
        Listener.super.onEvents(player, events);
        MediaItemTag.from(player.getCurrentMediaItem()).ifPresent(tag -> {
            if (tag == currentMetadata) {
                return; // we still have the same metadata, no need to do anything
            }
            final StreamInfo previousInfo = Optional.ofNullable(currentMetadata)
                    .flatMap(MediaItemTag::getMaybeStreamInfo).orElse(null);
            final MediaItemTag.AudioTrack previousAudioTrack =
                    Optional.ofNullable(currentMetadata)
                            .flatMap(MediaItemTag::getMaybeAudioTrack).orElse(null);
            currentMetadata = tag;

            if (!currentMetadata.getErrors().isEmpty()) {
                // new errors might have been added even if previousInfo == tag.getMaybeStreamInfo()
                final ErrorInfo errorInfo = new ErrorInfo(
                        currentMetadata.getErrors(),
                        UserAction.PLAY_STREAM,
                        "Loading failed for [" + currentMetadata.getTitle()
                                + "]: " + currentMetadata.getStreamUrl(),
                        currentMetadata.getServiceId(),
                        currentMetadata.getStreamUrl());
                ErrorUtil.createNotification(context, errorInfo);
            }

            currentMetadata.getMaybeStreamInfo().ifPresent(info -> {
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onEvents() update stream info: " + info.getName());
                }
                if (previousInfo == null || !previousInfo.getUrl().equals(info.getUrl())) {
                    // only update with the new stream info if it has actually changed
                    updateMetadataWith(info);
                } else if (previousAudioTrack == null
                        || tag.getMaybeAudioTrack()
                        .map(t -> t.getSelectedAudioStreamIndex()
                                != previousAudioTrack.getSelectedAudioStreamIndex())
                        .orElse(false)) {
                    notifyAudioTrackUpdateToListeners();
                }
            });
            if (currentMetadata instanceof LocalMediaItemTag) {
                updateMetadataForLocalMedia(((LocalMediaItemTag) currentMetadata).getItem());
            }
        });
    }

    @Override
    public void onTimelineChanged(@NonNull final Timeline timeline, final int reason) {
        if (currentItem != null && isLive()) {
            // A live timeline can reset ExoPlayer's playback parameters while it is prepared or
            // refreshed. Restore the active channel profile (or the global playback speed) after
            // the dynamic timeline is available.
            playbackParametersController.applySpeedProfile(currentItem);
        }
    }

    @Override
    public void onTracksChanged(@NonNull final Tracks tracks) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onTracksChanged(), "
                    + "track group size = " + tracks.getGroups().size());
        }
        UIs.call(playerUi -> playerUi.onTextTracksChanged(tracks));
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - playbackParameters(), speed = [" + playbackParameters.speed
                    + "], pitch = [" + playbackParameters.pitch + "]");
        }
        UIs.call(playerUi -> playerUi.onPlaybackParametersChanged(playbackParameters));
    }

    @Override
    public void onPositionDiscontinuity(@NonNull final PositionInfo oldPosition,
                                        @NonNull final PositionInfo newPosition,
                                        @DiscontinuityReason final int discontinuityReason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with "
                    + "oldPositionIndex = [" + oldPosition.mediaItemIndex + "], "
                    + "oldPositionMs = [" + oldPosition.positionMs + "], "
                    + "newPositionIndex = [" + newPosition.mediaItemIndex + "], "
                    + "newPositionMs = [" + newPosition.positionMs + "], "
                    + "discontinuityReason = [" + discontinuityReason + "]");
        }
        if (playQueue == null) {
            return;
        }

        sponsorBlockController.onPositionDiscontinuity(
                discontinuityReason == DISCONTINUITY_REASON_SEEK, newPosition.positionMs);

        // Refresh the playback if there is a transition to the next video
        final int newIndex = newPosition.mediaItemIndex;
        if (newIndex != oldPosition.mediaItemIndex) {
            UIs.call(PlayerUi::onMediaItemTransition);
            errorController.resetRecovery();
        }
        if (discontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
            sleepTimerController.onItemEnded(
                    playQueue.getItem(oldPosition.mediaItemIndex), true);
        }
        switch (discontinuityReason) {
            case DISCONTINUITY_REASON_AUTO_TRANSITION:
            case DISCONTINUITY_REASON_REMOVE:
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (getRepeatMode() == REPEAT_MODE_ONE && newIndex == playQueue.getIndex()) {
                    registerStreamViewed();
                    break;
                }
            case DISCONTINUITY_REASON_SEEK:
                if (DEBUG) {
                    Log.d(TAG, "ExoPlayer - onSeekProcessed() called");
                }
                if (isPrepared) {
                    saveStreamProgressState();
                }
            case DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
            case DISCONTINUITY_REASON_INTERNAL:
                // Player index may be invalid when playback is blocked
                if (getCurrentState() != STATE_BLOCKED && newIndex != playQueue.getIndex()) {
                    saveStreamProgressStateCompleted(); // current stream has ended
                    playQueue.setIndex(newIndex);
                }
                break;
            case DISCONTINUITY_REASON_SKIP:
                break; // only makes Android Studio linter happy, as there are no ads
        }

        if (discontinuityReason != DISCONTINUITY_REASON_AUTO_TRANSITION) {
            sleepTimerController.onPositionDiscontinuity(playQueue.getItem(newIndex));
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        UIs.call(PlayerUi::onRenderedFirstFrame);
    }

    @Override
    public void onCues(@NonNull final CueGroup cueGroup) {
        UIs.call(playerUi -> playerUi.onCues(cueGroup.cues));
    }

    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Errors
    //////////////////////////////////////////////////////////////////////////*/
    //region Errors

    /**
     * Process exceptions produced by {@link androidx.media3.exoplayer.ExoPlayer ExoPlayer}.
     * <p>There are multiple types of errors:</p>
     * <ul>
     * <li>{@link PlaybackException#ERROR_CODE_BEHIND_LIVE_WINDOW BEHIND_LIVE_WINDOW}:
     * If the playback on livestreams are lagged too far behind the current playable
     * window. Then we seek to the latest timestamp and restart the playback.
     * This error is <b>catchable</b>.
     * </li>
     * <li>From {@link PlaybackException#ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE BAD_IO} to
     * {@link PlaybackException#ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED UNSUPPORTED_FORMATS}:
     * If the stream source is validated by the extractor but not recognized by the player,
     * then we can try to recover playback by signalling an error on the {@link PlayQueue}.</li>
     * <li>For {@link PlaybackException#ERROR_CODE_TIMEOUT PLAYER_TIMEOUT},
     * {@link PlaybackException#ERROR_CODE_IO_UNSPECIFIED MEDIA_SOURCE_RESOLVER_TIMEOUT} and
     * {@link PlaybackException#ERROR_CODE_IO_NETWORK_CONNECTION_FAILED NO_NETWORK}:
     * We can keep set the recovery record and keep to player at the current state until
     * it is ready to play by restarting the {@link MediaSourceManager}.</li>
     * <li>On any ExoPlayer specific issue internal to its device interaction, such as
     * {@link PlaybackException#ERROR_CODE_DECODER_INIT_FAILED DECODER_ERROR}:
     * We terminate the playback.</li>
     * <li>For any other unspecified issue internal: We set a recovery and try to restart
     * the playback.</li>
     * For any error above that is <b>not</b> explicitly <b>catchable</b>, the player will
     * create a notification so users are aware.
     * </ul>
     *
     * @see androidx.media3.common.Player.Listener#onPlayerError(PlaybackException)
     */
    // Any error code not explicitly covered here is either unrelated to the WizeStream use case
    // (e.g. DRM) or not recoverable (e.g. Decoder error). In both cases, the player should
    // shutdown.
    @Override
    public void onPlayerError(@NonNull final PlaybackException error) {
        errorController.onPlayerError(error);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback position and seek
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback position and seek

    @Override // own playback listener (this is a getter)
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        return seekController.isApproachingPlaybackEdge(timeToEndMillis);
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge.
     *
     * @return whether the livestream is playing at or beyond the edge
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLiveEdge() {
        return seekController.isLiveEdge();
    }

    @Override // own playback listener
    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item, final boolean wasBlocked) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackSynchronize(was blocked: " + wasBlocked
                    + ") called with item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");
        }
        if (exoPlayerIsNull() || playQueue == null || currentItem == item) {
            return; // nothing to synchronize
        }

        final int playQueueIndex = playQueue.indexOf(item);
        final int playlistIndex = simpleExoPlayer.getCurrentMediaItemIndex();
        final int playlistSize = simpleExoPlayer.getCurrentTimeline().getWindowCount();
        final boolean removeThumbnailBeforeSync = currentItem == null
                || currentItem.getServiceId() != item.getServiceId()
                || !currentItem.getUrl().equals(item.getUrl());

        historyController.stopLearningSession();
        currentItem = item;
        historyController.updateLearningSession();
        playbackParametersController.applySpeedProfile(item);

        if (playQueueIndex != playQueue.getIndex()) {
            // wrong window (this should be impossible, as this method is called with
            // `item=playQueue.getItem()`, so the index of that item must be equal to `getIndex()`)
            Log.e(TAG, "Playback - Play Queue may be not in sync: item index=["
                    + playQueueIndex + "], " + "queue index=[" + playQueue.getIndex() + "]");

        } else if ((playlistSize > 0 && playQueueIndex >= playlistSize) || playQueueIndex < 0) {
            // the queue and the player's timeline are not in sync, since the play queue index
            // points outside of the timeline
            Log.e(TAG, "Playback - Trying to seek to invalid index=[" + playQueueIndex
                    + "] with playlist length=[" + playlistSize + "]");

        } else if (wasBlocked || playlistIndex != playQueueIndex || !isPlaying()) {
            // either the player needs to be unblocked, or the play queue index has just been
            // changed and needs to be synchronized, or the player is not playing
            if (DEBUG) {
                Log.d(TAG, "Playback - Rewinding to correct index=[" + playQueueIndex + "], "
                        + "from=[" + playlistIndex + "], size=[" + playlistSize + "].");
            }

            if (removeThumbnailBeforeSync) {
                // unset the current (now outdated) thumbnail to ensure it is not used during sync
                thumbnailController.clear();
            }

            // sync the player index with the queue index, and seek to the correct position
            if (item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
                simpleExoPlayer.seekTo(playQueueIndex, item.getRecoveryPosition());
                playQueue.unsetRecovery(playQueueIndex);
            } else {
                simpleExoPlayer.seekToDefaultPosition(playQueueIndex);
            }
        }
    }

    public void seekTo(final long positionMillis) {
        seekController.seekTo(positionMillis);
    }

    public void seekToDefault() {
        seekController.seekToDefault();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player actions (play, pause, previous, fast-forward, ...)
    //////////////////////////////////////////////////////////////////////////*/
    //region Player actions (play, pause, previous, fast-forward, ...)

    public void play() {
        transportController.play();
    }

    public void pause() {
        transportController.pause();
    }

    public void playPause() {
        transportController.playPause();
    }

    public void playPrevious() {
        transportController.playPrevious();
    }

    public void playNext() {
        transportController.playNext();
    }

    public void fastForward() {
        seekController.fastForward();
    }

    public void fastRewind() {
        seekController.fastRewind();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // StreamInfo history: views and progress
    //////////////////////////////////////////////////////////////////////////*/
    //region StreamInfo history: views and progress

    private void registerStreamViewed() {
        historyController.registerViewed();
    }

    private void saveStreamProgressState(final long progressMillis) {
        historyController.saveProgress(progressMillis);
    }

    public void saveStreamProgressState() {
        if (exoPlayerIsNull() || currentMetadata == null || playQueue == null
                || playQueue.getIndex() != simpleExoPlayer.getCurrentMediaItemIndex()) {
            // Make sure play queue and current window index are equal, to prevent saving state for
            // the wrong stream on discontinuity (e.g. when the stream just changed but the
            // playQueue index and currentMetadata still haven't updated)
            return;
        }
        // Save current position. It will help to restore this position once a user
        // wants to play prev or next stream from the queue
        playQueue.setRecovery(playQueue.getIndex(), simpleExoPlayer.getContentPosition());
        saveStreamProgressState(simpleExoPlayer.getCurrentPosition());
    }

    public void saveStreamProgressStateCompleted() {
        // current stream has ended, so the progress is its duration (+1 to overcome rounding)
        if (currentItem != null && currentItem.isLocalMedia()) {
            saveStreamProgressState((currentItem.getDuration() + 1) * 1000);
        } else {
            getCurrentStreamInfo().ifPresent(info ->
                    saveStreamProgressState((info.getDuration() + 1) * 1000));
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Metadata
    //////////////////////////////////////////////////////////////////////////*/
    //region Metadata

    private void updateMetadataWith(@NonNull final StreamInfo info) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.getName());
        }
        if (exoPlayerIsNull()) {
            return;
        }

        localMetadataController.cancel();

        playbackParametersController.applySpeedProfile(info);
        sponsorBlockController.updateSegments(info);
        maybeAutoQueueNextStream(info);

        thumbnailController.load(ExtractorImageCompat.thumbnailImages(info));
        registerStreamViewed();

        notifyMetadataUpdateToListeners();
        notifyAudioTrackUpdateToListeners();
        UIs.call(playerUi -> playerUi.onMetadataChanged(info));
    }

    private void updateMetadataForLocalMedia(@NonNull final PlayQueueItem item) {
        sponsorBlockController.reset();
        thumbnailController.loadLocal(item);
        localMetadataController.load(item);
        historyController.registerViewed(item);
        notifyMetadataUpdateToListeners();
        notifyAudioTrackUpdateToListeners();
        UIs.call(playerUi -> playerUi.onMetadataChanged(currentMetadata));
    }

    @NonNull
    public String getVideoUrl() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getStreamUrl();
    }

    @NonNull
    public String getVideoUrlAtCurrentTime() {
        final long timeSeconds = simpleExoPlayer.getCurrentPosition() / 1000;
        String videoUrl = getVideoUrl();
        if (!isLive() && timeSeconds >= 0 && currentMetadata != null
                && currentMetadata.getServiceId() == YouTube.getServiceId()) {
            // Timestamp doesn't make sense in a live stream so drop it
            videoUrl += ("&t=" + timeSeconds);
        }
        return videoUrl;
    }

    @NonNull
    public String getVideoTitle() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getTitle();
    }

    @NonNull
    public String getUploaderName() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getUploaderName();
    }

    @Nullable
    public Bitmap getThumbnail() {
        return thumbnailController.getCurrentThumbnail();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    //////////////////////////////////////////////////////////////////////////*/
    //region Play queue, segments and streams

    private void maybeAutoQueueNextStream(@NonNull final StreamInfo info) {
        if (playQueue == null || playQueue.getIndex() != playQueue.size() - 1
                || getRepeatMode() != REPEAT_MODE_OFF
                || !PlayerHelper.isAutoQueueEnabled(context)) {
            return;
        }
        // auto queue when starting playback on the last item when not repeating
        final PlayQueueItem currentQueueItem = playQueue.getItem();
        final boolean preferShortFormContent = info.isShortFormContent()
                || (currentQueueItem != null && currentQueueItem.isShortFormContent());
        final PlayQueue autoQueue = PlayerHelper.autoQueueOf(info,
                playQueue.getStreams(), preferShortFormContent);
        if (autoQueue != null) {
            playQueue.append(autoQueue.getStreams());
        }
    }

    public void selectQueueItem(final PlayQueueItem item) {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int index = playQueue.indexOf(item);
        if (index == -1) {
            return;
        }

        if (playQueue.getIndex() == index && simpleExoPlayer.getCurrentMediaItemIndex() == index) {
            seekToDefault();
        } else {
            saveStreamProgressState();
        }
        playQueue.setIndex(index);
        sleepTimerController.onQueueItemSelected(item);
    }

    @Override
    public void onPlayQueueEdited() {
        sleepTimerController.onQueueEdited();
        notifyPlaybackUpdateToListeners();
        notifySleepTimerUpdateToListeners();
        UIs.call(PlayerUi::onPlayQueueEdited);
    }

    @Override // own playback listener
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        if (audioPlayerSelected()) {
            return audioResolver.resolve(info);
        }

        if (isAudioOnly && videoResolver.getStreamSourceType().orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY)
                == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY) {
            // If the current info has only video streams with audio and if the stream is played as
            // audio, we need to use the audio resolver, otherwise the video stream will be played
            // in background.
            return audioResolver.resolve(info);
        }

        // Even if the stream is played in background, we need to use the video resolver if the
        // info played is separated video-only and audio-only streams; otherwise, if the audio
        // resolver was called when the app was in background, the app will only stream audio when
        // the user come back to the app and will never fetch the video stream.
        // Note that the video is not fetched when the app is in background because the video
        // renderer is fully disabled (see useVideoAndSubtitles method), except for HLS streams
        // (see https://github.com/google/ExoPlayer/issues/9282).
        if (ChannelPlaybackProfileManager.isAvailable(context, info)) {
            return videoResolver.resolve(
                    info, ChannelPlaybackProfileManager.getQuality(context, info));
        }
        return videoResolver.resolve(info);
    }

    @Override
    @Nullable
    public MediaSource sourceOfLocal(final PlayQueueItem item) {
        if (!item.isLocalMedia()) {
            return null;
        }
        return dataSource.getProgressiveMediaSourceFactory()
                .createMediaSource(LocalMediaItemTag.of(item).asMediaItem());
    }

    public void disablePreloadingOfCurrentTrack() {
        loadController.disablePreloadingOfCurrentTrack();
    }

    public Optional<VideoStream> getSelectedVideoStream() {
        return Optional.ofNullable(currentMetadata)
                .flatMap(MediaItemTag::getMaybeQuality)
                .filter(quality -> {
                    final int selectedStreamIndex = quality.getSelectedVideoStreamIndex();
                    return selectedStreamIndex >= 0
                            && selectedStreamIndex < quality.getSortedVideoStreams().size();
                })
                .map(quality -> quality.getSortedVideoStreams()
                        .get(quality.getSelectedVideoStreamIndex()));
    }

    public Optional<AudioStream> getSelectedAudioStream() {
        return Optional.ofNullable(currentMetadata)
                .flatMap(MediaItemTag::getMaybeAudioTrack)
                .map(MediaItemTag.AudioTrack::getSelectedAudioStream);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    public int getCaptionRendererIndex() {
        if (exoPlayerIsNull()) {
            return RENDERER_UNAVAILABLE;
        }

        for (int t = 0; t < simpleExoPlayer.getRendererCount(); t++) {
            if (simpleExoPlayer.getRendererType(t) == C.TRACK_TYPE_TEXT) {
                return t;
            }
        }

        return RENDERER_UNAVAILABLE;
    }

    @Nullable
    public String getCaptionPreference() {
        final StreamInfo currentInfo = getCurrentStreamInfo().orElse(null);
        if (currentInfo != null
                && ChannelPlaybackProfileManager.hasCaptionPreference(context, currentInfo)) {
            return ChannelPlaybackProfileManager.getCaptionPreference(context, currentInfo);
        }
        return prefs.getString(context.getString(R.string.caption_user_set_key), null);
    }

    public void setCaptionPreference(@Nullable final String language) {
        final int textRendererIndex = getCaptionRendererIndex();
        if (textRendererIndex != RENDERER_UNAVAILABLE) {
            if (language == null) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true));
            } else {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredTextLanguages(
                                language, PlayerHelper.captionLanguageStemOf(language))
                        .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                        .setRendererDisabled(textRendererIndex, false));
            }
        }

        final StreamInfo currentInfo = getCurrentStreamInfo().orElse(null);
        if (!ChannelPlaybackProfileManager.saveCaptionPreference(
                context, currentInfo, currentItem, language)) {
            final SharedPreferences.Editor editor = prefs.edit();
            if (language == null) {
                editor.remove(context.getString(R.string.caption_user_set_key));
            } else {
                editor.putString(context.getString(R.string.caption_user_set_key), language);
            }
            editor.apply();
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size
    @Override // exoplayer listener
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: "
                    + "width / height = [" + videoSize.width + " / " + videoSize.height
                    + " = " + (((float) videoSize.width) / videoSize.height) + "], "
                    + "unappliedRotationDegrees = [" + videoSize.unappliedRotationDegrees + "], "
                    + "pixelWidthHeightRatio = [" + videoSize.pixelWidthHeightRatio + "]");
        }

        UIs.call(playerUi -> playerUi.onVideoSizeChanged(videoSize));
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Activity / fragment binding
    //////////////////////////////////////////////////////////////////////////*/
    //region Activity / fragment binding

    public void setFragmentListener(final PlayerServiceEventListener listener) {
        eventDispatcher.setFragmentListener(listener);
    }

    public void removeFragmentListener(final PlayerServiceEventListener listener) {
        eventDispatcher.removeFragmentListener(listener);
    }

    void setActivityListener(final PlayerEventListener listener) {
        eventDispatcher.setActivityListener(listener);
    }

    void removeActivityListener(final PlayerEventListener listener) {
        eventDispatcher.removeActivityListener(listener);
    }

    void stopActivityBinding() {
        eventDispatcher.stopBindings();
    }

    private void notifyQueueUpdateToListeners() {
        eventDispatcher.notifyQueueUpdate();
    }

    void notifyMetadataUpdateToListeners() {
        eventDispatcher.notifyMetadataUpdate();
    }

    void notifyPlaybackUpdateToListeners() {
        eventDispatcher.notifyPlaybackUpdate();
    }

    void notifyAudioTrackUpdateToListeners() {
        eventDispatcher.notifyAudioTrackUpdate();
    }

    void notifySleepTimerUpdateToListeners() {
        eventDispatcher.notifySleepTimerUpdate();
    }

    public void useVideoAndSubtitles(final boolean videoAndSubtitlesEnabled) {
        if (playQueue == null) {
            return;
        }

        isAudioOnly = !videoAndSubtitlesEnabled;

        final var item = playQueue.getItem();
        final boolean hasPendingRecovery =
                item != null && item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET;
        final boolean hasTimeline =
                !exoPlayerIsNull() && !simpleExoPlayer.getCurrentTimeline().isEmpty();


        getCurrentStreamInfo().ifPresentOrElse(info -> {
            // In case we don't know the source type, fall back to either video-with-audio, or
            // audio-only source type
            final SourceType sourceType = videoResolver.getStreamSourceType()
                    .orElse(SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY);

            if (hasTimeline || !hasPendingRecovery) {
                // making sure to save playback position before reloadPlayQueueManager()
                setRecovery();
            }

            if (playQueueManagerReloadingNeeded(sourceType, info, getVideoRendererIndex())) {
                reloadPlayQueueManager();
            }
        }, () -> {
            /*
            The current metadata may be null sometimes (for e.g. when using an unstable connection
            in livestreams) so we will be not able to execute the block above

            Reload the play queue manager in this case, which is the behavior when we don't know the
            index of the video renderer or playQueueManagerReloadingNeeded returns true
            */
            if (hasTimeline || !hasPendingRecovery) {
                // making sure to save playback position before reloadPlayQueueManager()
                setRecovery();
            }
            reloadPlayQueueManager();
        });

        // Disable or enable video and subtitles renderers depending of the
        // videoAndSubtitlesEnabled value
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !videoAndSubtitlesEnabled)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoAndSubtitlesEnabled));
    }

    public void setPlaybackPresentationMode(
            @NonNull final PlaybackPresentationMode newMode) {
        if (playbackPresentationMode == newMode) {
            return;
        }
        playbackPresentationMode = newMode;
        visualizerAudioProcessor.setEnabled(newMode.allowsVisualizer());
        useVideoAndSubtitles(newMode.rendersVideo());
        updateAudioTunneling();
        UIs.call(playerUi -> playerUi.onPlaybackPresentationModeChanged(newMode));
    }

    /**
     * Return whether the play queue manager needs to be reloaded when switching player type.
     *
     * <p>
     * The play queue manager needs to be reloaded if the video renderer index is not known and if
     * the content is not an audio content, but also if none of the following cases is met:
     *
     * <ul>
     *     <li>the content is an {@link StreamType#AUDIO_STREAM audio stream}, an
     *     {@link StreamType#AUDIO_LIVE_STREAM audio live stream}, or a
     *     {@link StreamType#POST_LIVE_AUDIO_STREAM ended audio live stream};</li>
     *     <li>the content is a {@link StreamType#LIVE_STREAM live stream} and the source type is a
     *     {@link SourceType#LIVE_STREAM live source};</li>
     *     <li>the content's source is {@link SourceType#VIDEO_WITH_SEPARATED_AUDIO a video stream
     *     with a separated audio source} or has no audio-only streams available <b>and</b> is a
     *     {@link StreamType#VIDEO_STREAM video stream}, an
     *     {@link StreamType#POST_LIVE_STREAM ended live stream}, or a
     *     {@link StreamType#LIVE_STREAM live stream}.
     *     </li>
     * </ul>
     * </p>
     *
     * @param sourceType         the {@link SourceType} of the stream
     * @param streamInfo         the {@link StreamInfo} of the stream
     * @param videoRendererIndex the video renderer index of the video source, if that's a video
     *                           source (or {@link #RENDERER_UNAVAILABLE})
     * @return whether the play queue manager needs to be reloaded
     */
    private boolean playQueueManagerReloadingNeeded(final SourceType sourceType,
                                                    @NonNull final StreamInfo streamInfo,
                                                    final int videoRendererIndex) {
        final StreamType streamType = streamInfo.getStreamType();
        final boolean isStreamTypeAudio = StreamTypeUtil.isAudio(streamType);

        if (videoRendererIndex == RENDERER_UNAVAILABLE && !isStreamTypeAudio) {
            return true;
        }

        // The content is an audio stream, an audio live stream, or a live stream with a live
        // source: it's not needed to reload the play queue manager because the stream source will
        // be the same
        if (isStreamTypeAudio || (streamType == StreamType.LIVE_STREAM
                && sourceType == SourceType.LIVE_STREAM)) {
            return false;
        }

        // The content's source is a video with separated audio or a video with audio -> the video
        // and its fetch may be disabled
        // The content's source is a video with embedded audio and the content has no separated
        // audio stream available: it's probably not needed to reload the play queue manager
        // because the stream source will be probably the same as the current played
        if (sourceType == SourceType.VIDEO_WITH_SEPARATED_AUDIO
                || (sourceType == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
                && isNullOrEmpty(streamInfo.getAudioStreams()))) {
            // It's not needed to reload the play queue manager only if the content's stream type
            // is a video stream, a live stream or an ended live stream
            return !StreamTypeUtil.isVideo(streamType);
        }

        // Other cases: the play queue manager reload is needed
        return true;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    public Optional<StreamInfo> getCurrentStreamInfo() {
        return Optional.ofNullable(currentMetadata).flatMap(MediaItemTag::getMaybeStreamInfo);
    }

    public int getCurrentState() {
        return currentState;
    }

    public boolean exoPlayerIsNull() {
        return simpleExoPlayer == null;
    }

    public ExoPlayer getExoPlayer() {
        return simpleExoPlayer;
    }

    public boolean isStopped() {
        return exoPlayerIsNull() || simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_IDLE;
    }

    public boolean isPlaying() {
        return !exoPlayerIsNull() && simpleExoPlayer.isPlaying();
    }

    public boolean getPlayWhenReady() {
        return !exoPlayerIsNull() && simpleExoPlayer.getPlayWhenReady();
    }

    public boolean isLoading() {
        return !exoPlayerIsNull() && simpleExoPlayer.isLoading();
    }

    private boolean isLive() {
        return seekController.isLive();
    }

    public void setPlaybackQuality(@Nullable final String quality) {
        saveStreamProgressState();
        setRecovery();
        if (quality != null) {
            ChannelPlaybackProfileManager.saveQuality(
                    context, getCurrentStreamInfo().orElse(null), currentItem, quality);
        }
        videoResolver.setPlaybackQuality(quality);
        reloadPlayQueueManager();
    }

    public void setAudioTrack(@Nullable final String audioTrackId) {
        saveStreamProgressState();
        setRecovery();
        videoResolver.setAudioTrack(audioTrackId);
        audioResolver.setAudioTrack(audioTrackId);
        reloadPlayQueueManager();
    }


    @NonNull
    public Context getContext() {
        return context;
    }

    @NonNull
    public SharedPreferences getPrefs() {
        return prefs;
    }


    public PlayerType getPlayerType() {
        return playerType;
    }

    public void rememberMainPlayerFullscreenBeforePopup(final boolean fullscreen) {
        popupPlayerReturnState.remember(fullscreen);
    }

    public boolean consumeMainPlayerFullscreenBeforePopup(final boolean fallback) {
        return popupPlayerReturnState.consume(fallback);
    }

    public boolean audioPlayerSelected() {
        return playerType == PlayerType.AUDIO;
    }

    public boolean videoPlayerSelected() {
        return playerType == PlayerType.MAIN;
    }

    public boolean popupPlayerSelected() {
        return playerType == PlayerType.POPUP;
    }


    @Nullable
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public AudioReactor getAudioReactor() {
        return audioReactor;
    }

    public PlayerService getService() {
        return service;
    }

    public boolean isAudioOnly() {
        return isAudioOnly;
    }

    @NonNull
    public PlaybackPresentationMode getPlaybackPresentationMode() {
        return playbackPresentationMode;
    }

    @NonNull
    public VisualizerAudioProcessor getVisualizerAudioProcessor() {
        return visualizerAudioProcessor;
    }

    @NonNull
    public DefaultTrackSelector getTrackSelector() {
        return trackSelector;
    }

    @Nullable
    public MediaItemTag getCurrentMetadata() {
        return currentMetadata;
    }

    @Nullable
    public PlayQueueItem getCurrentItem() {
        return currentItem;
    }

    public Optional<PlayerServiceEventListener> getFragmentListener() {
        return eventDispatcher.getFragmentListener();
    }

    /**
     * @return the user interfaces connected with the player
     */
    @SuppressWarnings("MethodName") // keep the unusual method name
    public PlayerUiList UIs() {
        return UIs;
    }

    /**
     * Get the video renderer index of the current playing stream.
     * <p>
     * This method returns the video renderer index of the current
     * {@link MappingTrackSelector.MappedTrackInfo} or {@link #RENDERER_UNAVAILABLE} if the current
     * {@link MappingTrackSelector.MappedTrackInfo} is null or if there is no video renderer index.
     *
     * @return the video renderer index or {@link #RENDERER_UNAVAILABLE} if it cannot be get
     */
    private int getVideoRendererIndex() {
        final MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector
                .getCurrentMappedTrackInfo();

        if (mappedTrackInfo == null) {
            return RENDERER_UNAVAILABLE;
        }

        // Check every renderer
        return IntStream.range(0, mappedTrackInfo.getRendererCount())
                // Check the renderer is a video renderer and has at least one track
                .filter(i -> !mappedTrackInfo.getTrackGroups(i).isEmpty()
                        && simpleExoPlayer.getRendererType(i) == C.TRACK_TYPE_VIDEO)
                // Return the first index found (there is at most one renderer per renderer type)
                .findFirst()
                // No video renderer index with at least one track found: return unavailable index
                .orElse(RENDERER_UNAVAILABLE);
    }
    //endregion

    /**
     * @return whether the device screen is turned on.
     */
    public boolean isScreenOn() {
        return broadcastController.isScreenOn();
    }
}
