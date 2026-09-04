package org.schabi.newpipe.player;

import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NO_PERMISSION;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_TIMEOUT;
import static com.google.android.exoplayer2.PlaybackException.ERROR_CODE_UNSPECIFIED;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SKIP;
import static com.google.android.exoplayer2.Player.DiscontinuityReason;
import static com.google.android.exoplayer2.Player.Listener;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.Player.RepeatMode;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlaybackParametersFromPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrieveSeekDurationFromPreferences;
import static org.schabi.newpipe.player.helper.PlayerHelper.savePlaybackParametersToPrefs;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_REPEAT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_SHUFFLE;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static coil3.Image_androidKt.toBitmap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.math.MathUtils;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.video.VideoSize;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockBehavior;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockCategoryRepository;
import org.schabi.newpipe.settings.sponsorblock.SponsorBlockPlaybackDecision;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.learning.LearningSessionTracker;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.equalizer.EqualizerController;
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
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import coil3.target.Target;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
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
    private static final int SLEEP_TIMER_UPDATE_INTERVAL_MILLIS = 1000; // 1 second

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
    @Nullable
    private Bitmap currentThumbnail;
    @Nullable
    private coil3.request.Disposable thumbnailDisposable;
    @NonNull
    private final PlayerHttpErrorRecovery.RecoveryGuard mediaUrlRecoveryGuard =
        new PlayerHttpErrorRecovery.RecoveryGuard();
    @NonNull
    private final Handler mediaUrlRecoveryHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingMediaUrlRecovery;

    @NonNull
    private List<SponsorBlockSegment> sponsorBlockSegments = Collections.emptyList();
    @NonNull
    private final Set<String> skippedSponsorBlockSegments = new HashSet<>();
    @Nullable
    private String ignoredSponsorBlockSegment;
    @Nullable
    private String displayedSponsorBlockManualSkipSegment;
    private boolean sponsorBlockSkipInProgress = false;


    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    private ExoPlayer simpleExoPlayer;
    private AudioReactor audioReactor;
    @NonNull
    private final EqualizerController equalizerController;

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
    private final SleepTimer sleepTimer = new SleepTimer();
    @NonNull
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    @NonNull
    private final Runnable sleepTimerTick = this::onSleepTimerTick;
    @Nullable
    private PlayQueueItem sleepTimerCurrentTarget;
    @Nullable
    private PlayQueueItem sleepTimerQueueTarget;
    private boolean sleepTimerQueueTargetFollowsLoading;
    private float sleepTimerVolumeMultiplier = 1.0f;
    private boolean muted;

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

    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    @Nullable
    private PlayerServiceEventListener fragmentListener = null;
    @Nullable
    private PlayerEventListener activityListener = null;

    @NonNull
    private final SerialDisposable progressUpdateDisposable = new SerialDisposable();
    @NonNull
    private final CompositeDisposable databaseUpdateDisposable = new CompositeDisposable();
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
    private final HistoryRecordManager recordManager;
    @NonNull
    private final LearningSessionTracker learningSessionTracker;
    @NonNull
    private final PlayerDataSource dataSource;

    private boolean screenOn = true;

    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor

    /**
     * @param service the service this player resides in
     * @param mediaSession used to build the {@link MediaSessionPlayerUi}, lives in the service and
     *                     could possibly be reused with multiple player instances
     * @param sessionConnector used to build the {@link MediaSessionPlayerUi}, lives in the service
     *                         and could possibly be reused with multiple player instances
     */
    public Player(@NonNull final PlayerService service,
                  @NonNull final MediaSessionCompat mediaSession,
                  @NonNull final MediaSessionConnector sessionConnector) {
        this.service = service;
        context = service;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        equalizerController = new EqualizerController(context);
        recordManager = new HistoryRecordManager(context);
        learningSessionTracker = new LearningSessionTracker(context);

        setupBroadcastReceiver();

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

        // The UIs added here should always be present. They will be initialized when the player
        // reaches the initialization step. Make sure the media session ui is before the
        // notification ui in the UIs list, since the notification depends on the media session in
        // PlayerUi#initPlayer(), and UIs.call() guarantees UI order is preserved.
        UIs = new PlayerUiList(
                new MediaSessionPlayerUi(this, mediaSession, sessionConnector),
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
            learningSessionTracker.update(currentItem, currentState == STATE_PLAYING,
                    audioPlayerSelected());
            @Nullable final PlayerType requestedPlayerType = IntentCompat.getSerializableExtra(
                    intent, PLAYER_TYPE, PlayerType.class);
            if (playerType == PlayerType.MAIN && requestedPlayerType == PlayerType.POPUP
                    && !popupPlayerReturnState.isRemembered()) {
                popupPlayerReturnState.remember(UIs.get(MainPlayerUi.class)
                        .map(MainPlayerUi::isFullscreen)
                        .orElse(false));
            }
            playerType = requestedPlayerType;
            learningSessionTracker.update(currentItem, currentState == STATE_PLAYING,
                    audioPlayerSelected());
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
                                        == com.google.android.exoplayer2.Player.STATE_IDLE) {
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
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
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
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
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
            databaseUpdateDisposable.add(recordManager.loadStreamState(newQueue.getItem())
                    .observeOn(AndroidSchedulers.mainThread())
                    // Do not place initPlayback() in doFinally() because
                    // it restarts playback after destroy()
                    //.doFinally()
                    .subscribe(
                            state -> {
                                if (!state.isFinished(newQueue.getItem().getDuration())) {
                                    // resume playback only if the stream was not played to the end
                                    newQueue.setRecovery(newQueue.getIndex(),
                                            state.getProgressMillis());
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
                            }
                    ));
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
        final PlaybackParameters savedParameters = retrievePlaybackParametersFromPrefs(this);
        applyPlaybackParameters(savedParameters.speed, savedParameters.pitch, playbackSkipSilence);

        playQueue = queue;
        playQueue.init();
        simpleExoPlayer.setShuffleModeEnabled(playQueue.isShuffled());
        retargetSleepTimerForNewQueue();
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
        equalizerController.attachAudioSession(simpleExoPlayer.getAudioSessionId());

        audioReactor = new AudioReactor(context, simpleExoPlayer);

        registerBroadcastReceiver();

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
        cancelPendingMediaUrlRecovery();
        mediaUrlRecoveryGuard.reset();
        learningSessionTracker.stop();
        UIs.call(PlayerUi::destroyPlayer);
        equalizerController.releaseAudioSession();

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

        clearSleepTimer();
        saveStreamProgressState();
        setRecovery();
        stopActivityBinding();

        destroyPlayer();
        unregisterBroadcastReceiver();

        databaseUpdateDisposable.clear();
        progressUpdateDisposable.set(null);
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
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    /**
     * This function prepares the broadcast receiver and is called only in the constructor.
     * Therefore if you want any PlayerUi to receive a broadcast action, you should add it here,
     * even if that player ui might never be added to the player. In that case the received
     * broadcast would not do anything.
     */
    private void setupBroadcastReceiver() {
        if (DEBUG) {
            Log.d(TAG, "setupBroadcastReceiver() called");
        }

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context ctx, final Intent intent) {
                onBroadcastReceived(intent);
            }
        };
        intentFilter = new IntentFilter();

        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_PLAY_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_NEXT);
        intentFilter.addAction(ACTION_FAST_REWIND);
        intentFilter.addAction(ACTION_FAST_FORWARD);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(ACTION_SHUFFLE);
        intentFilter.addAction(ACTION_RECREATE_NOTIFICATION);

        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
    }

    private void onBroadcastReceived(final Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
        }

        switch (intent.getAction()) {
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                pause();
                break;
            case ACTION_CLOSE:
                service.destroyPlayerAndStopService();
                break;
            case ACTION_PLAY_PAUSE:
                playPause();
                break;
            case ACTION_PLAY_PREVIOUS:
                playPrevious();
                break;
            case ACTION_PLAY_NEXT:
                playNext();
                break;
            case ACTION_FAST_REWIND:
                fastRewind();
                break;
            case ACTION_FAST_FORWARD:
                fastForward();
                break;
            case ACTION_REPEAT:
                cycleNextRepeatMode();
                break;
            case ACTION_SHUFFLE:
                toggleShuffleModeEnabled();
                break;
            case Intent.ACTION_SCREEN_OFF:
                screenOn = false;
                break;
            case Intent.ACTION_SCREEN_ON:
                screenOn = true;
                break;
            case Intent.ACTION_CONFIGURATION_CHANGED:
                if (DEBUG) {
                    Log.d(TAG, "ACTION_CONFIGURATION_CHANGED received");
                }
                break;
        }

        UIs.call(playerUi -> playerUi.onBroadcastReceived(intent));
    }

    private void registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver();
        ContextCompat.registerReceiver(context, broadcastReceiver, intentFilter,
                ContextCompat.RECEIVER_EXPORTED);
    }

    private void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (final IllegalArgumentException unregisteredException) {
            Log.w(TAG, "Broadcast receiver already unregistered: "
                    + unregisteredException.getMessage());
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail loading
    //////////////////////////////////////////////////////////////////////////*/
    //region Thumbnail loading

    private void loadCurrentThumbnail(final List<Image> thumbnails) {
        if (DEBUG) {
            Log.d(TAG, "Thumbnail - loadCurrentThumbnail() called with thumbnails = ["
                    + thumbnails.size() + "]");
        }

        // Cancel any ongoing image loading
        if (thumbnailDisposable != null) {
            thumbnailDisposable.dispose();
        }

        // Unset currentThumbnail, since it is now outdated. This ensures it is not used in media
        // session metadata while the new thumbnail is being loaded by Coil.
        onThumbnailLoaded(null);
        if (thumbnails.isEmpty()) {
            return;
        }

        // scale down the notification thumbnail for performance
        final var thumbnailTarget = new Target() {
            @Override
            public void onError(@Nullable final coil3.Image error) {
                Log.e(TAG, "Thumbnail - onError() called");
                // there is a new thumbnail, so e.g. the end screen thumbnail needs to change, too.
                onThumbnailLoaded(null);
            }

            @Override
            public void onStart(@Nullable final coil3.Image placeholder) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onStart() called");
                }
            }

            @Override
            public void onSuccess(@NonNull final coil3.Image result) {
                if (DEBUG) {
                    Log.d(TAG, "Thumbnail - onSuccess() called with: drawable = [" + result + "]");
                }
                // there is a new thumbnail, so e.g. the end screen thumbnail needs to change, too.
                onThumbnailLoaded(toBitmap(result));
            }
        };
        thumbnailDisposable = CoilHelper.INSTANCE
                .loadScaledDownThumbnail(context, thumbnails, thumbnailTarget);
    }


    private void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        // Avoid useless thumbnail updates, if the thumbnail has not actually changed. Based on the
        // thumbnail loading code, this if would be skipped only when both bitmaps are `null`, since
        // onThumbnailLoaded won't be called twice with the same nonnull bitmap by Coil's target.
        if (currentThumbnail != bitmap) {
            currentThumbnail = bitmap;
            UIs.call(playerUi -> playerUi.onThumbnailLoaded(bitmap));
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback parameters
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback parameters

    public float getPlaybackSpeed() {
        return getPlaybackParameters().speed;
    }

    public void setPlaybackSpeed(final float speed) {
        setPlaybackParameters(speed, getPlaybackPitch(), getPlaybackSkipSilence());
    }

    public void setPlaybackSpeedTemporarily(final float speed) {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.setPlaybackParameters(
                    new PlaybackParameters(speed, getPlaybackPitch()));
        }
    }

    public float getPlaybackPitch() {
        return getPlaybackParameters().pitch;
    }

    public boolean getPlaybackSkipSilence() {
        return !exoPlayerIsNull() && simpleExoPlayer.getSkipSilenceEnabled();
    }

    public PlaybackParameters getPlaybackParameters() {
        if (exoPlayerIsNull()) {
            return PlaybackParameters.DEFAULT;
        }
        return simpleExoPlayer.getPlaybackParameters();
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
        final float roundedSpeed = Math.round(speed * 100.0f) / 100.0f;
        final float roundedPitch = Math.round(pitch * 100.0f) / 100.0f;

        final StreamInfo currentInfo = getCurrentStreamInfo().orElse(null);
        if (ChannelPlaybackProfileManager.saveSpeed(
                context, currentInfo, currentItem, roundedSpeed)) {
            prefs.edit()
                    .putFloat(context.getString(R.string.playback_pitch_key), roundedPitch)
                    .putBoolean(context.getString(R.string.playback_skip_silence_key), skipSilence)
                    .apply();
        } else {
            savePlaybackParametersToPrefs(this, roundedSpeed, roundedPitch, skipSilence);
        }
        applyPlaybackParameters(roundedSpeed, roundedPitch, skipSilence);
    }

    private void applyPlaybackParameters(final float speed, final float pitch,
                                         final boolean skipSilence) {
        simpleExoPlayer.setPlaybackParameters(
                new PlaybackParameters(speed, pitch));
        simpleExoPlayer.setSkipSilenceEnabled(skipSilence);
    }

    private void applyPlaybackSpeedProfile(@NonNull final PlayQueueItem item) {
        if (ChannelPlaybackProfileManager.isAvailable(context, item)) {
            applyPlaybackSpeedProfile(ChannelPlaybackProfileManager.getSpeed(context, item));
        }
    }

    private void applyPlaybackSpeedProfile(@NonNull final StreamInfo info) {
        if (ChannelPlaybackProfileManager.isAvailable(context, info)) {
            applyPlaybackSpeedProfile(ChannelPlaybackProfileManager.getSpeed(context, info));
        }
    }

    private void applyPlaybackSpeedProfile(@Nullable final Float profileSpeed) {
        final float speed = profileSpeed != null
                ? profileSpeed : retrievePlaybackParametersFromPrefs(this).speed;
        simpleExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, getPlaybackPitch()));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    private void onUpdateProgress(final int currentProgress,
                                  final int duration,
                                  final int bufferPercent) {
        if (isPrepared) {
            UIs.call(ui -> ui.onUpdateProgress(currentProgress, duration, bufferPercent));
            notifyProgressUpdateToListeners(currentProgress, duration, bufferPercent);
        }
    }

    public void startProgressLoop() {
        progressUpdateDisposable.set(getProgressUpdateDisposable());
    }

    private void stopProgressLoop() {
        progressUpdateDisposable.set(null);
    }

    public boolean isProgressLoopRunning() {
        return progressUpdateDisposable.get() != null;
    }

    public void triggerProgressUpdate() {
        if (exoPlayerIsNull()) {
            return;
        }

        learningSessionTracker.update(currentItem, currentState == STATE_PLAYING,
                audioPlayerSelected());
        maybeSkipSponsorBlockSegment();
        onUpdateProgress(Math.max((int) simpleExoPlayer.getCurrentPosition(), 0),
                (int) simpleExoPlayer.getDuration(), simpleExoPlayer.getBufferedPercentage());
        updateSponsorBlockSeekBarMarkers();
    }

    private void maybeSkipSponsorBlockSegment() {
        if (!isSponsorBlockEnabled() || sponsorBlockSegments.isEmpty() || !isPlaying()
                || simpleExoPlayer.getPlaybackState()
                        != com.google.android.exoplayer2.Player.STATE_READY) {
            hideSponsorBlockManualSkipButton();
            return;
        }

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        ignoredSponsorBlockSegment = getUpdatedIgnoredSponsorBlockSegment(currentPositionMillis);
        final SponsorBlockSegment activeSegment =
                getActiveSponsorBlockPlaybackSegment(currentPositionMillis);

        if (activeSegment == null) {
            hideSponsorBlockManualSkipButton();
            return;
        }

        final String segmentKey = getSegmentKey(activeSegment);
        final SponsorBlockBehavior behavior = getSponsorBlockBehavior(activeSegment);
        final long targetPositionMillis = getSponsorBlockSegmentEndMillis(activeSegment);
        if (targetPositionMillis <= currentPositionMillis) {
            skippedSponsorBlockSegments.add(segmentKey);
            hideSponsorBlockManualSkipButton();
            return;
        }

        if (behavior == SponsorBlockBehavior.MANUAL) {
            showSponsorBlockManualSkipButton(activeSegment);
            return;
        }
        hideSponsorBlockManualSkipButton();
        skipSponsorBlockSegment(activeSegment);
    }

    private void skipSponsorBlockSegment(@NonNull final SponsorBlockSegment segment) {
        final String segmentKey = getSegmentKey(segment);
        final long targetPositionMillis = getSponsorBlockSegmentEndMillis(segment);
        sponsorBlockSkipInProgress = true;
        skippedSponsorBlockSegments.add(segmentKey);
        hideSponsorBlockManualSkipButton();
        simpleExoPlayer.seekTo(targetPositionMillis);
        if (prefs.getBoolean(context.getString(R.string.sponsor_block_notifications_key), true)) {
            Toast.makeText(context,
                    context.getString(R.string.sponsor_block_skipped_segment,
                            getSegmentCategoryName(segment)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private long getSponsorBlockSegmentEndMillis(@NonNull final SponsorBlockSegment segment) {
        final long segmentEndMillis = getSponsorBlockSegmentEndTimeMillis(segment);
        final long durationMillis = simpleExoPlayer.getDuration();
        return durationMillis > 0 && durationMillis != C.TIME_UNSET
                ? MathUtils.clamp(segmentEndMillis, 0, durationMillis)
                : Math.max(segmentEndMillis, 0);
    }

    private void showSponsorBlockManualSkipButton(@NonNull final SponsorBlockSegment segment) {
        if (!isCurrentStreamEligibleForSponsorBlockUi()) {
            hideSponsorBlockManualSkipButton();
            return;
        }

        final String segmentKey = getSegmentKey(segment);
        if (segmentKey.equals(displayedSponsorBlockManualSkipSegment)) {
            return;
        }

        final String label = getSponsorBlockSegmentCategory(segment) == SponsorBlockCategory.SPONSOR
                ? context.getString(R.string.sponsor_block_skip_sponsor)
                : context.getString(R.string.sponsor_block_skip_segment);
        displayedSponsorBlockManualSkipSegment = segmentKey;
        UIs.call(ui -> ui.showSponsorBlockSkipButton(label, () -> {
            if (!exoPlayerIsNull() && !skippedSponsorBlockSegments.contains(segmentKey)) {
                skipSponsorBlockSegment(segment);
            }
        }));
    }

    private void hideSponsorBlockManualSkipButton() {
        if (displayedSponsorBlockManualSkipSegment == null) {
            return;
        }
        displayedSponsorBlockManualSkipSegment = null;
        UIs.call(PlayerUi::hideSponsorBlockSkipButton);
    }

    @Nullable
    private SponsorBlockSegment getActiveSponsorBlockPlaybackSegment(final long positionMillis) {
        return SponsorBlockPlaybackDecision.findFirstRunnableSegment(
                sponsorBlockSegments,
                positionMillis,
                new SponsorBlockSegmentProvider(),
                new SponsorBlockCategoryStateProvider(),
                (segment, behavior) -> isRunnableSponsorBlockSegment(segment, behavior));
    }

    private boolean isRunnableSponsorBlockSegment(
            @NonNull final SponsorBlockSegment segment,
            @NonNull final SponsorBlockBehavior behavior) {
        final String segmentKey = getSegmentKey(segment);
        return !skippedSponsorBlockSegments.contains(segmentKey)
                && (behavior != SponsorBlockBehavior.SKIP
                || !segmentKey.equals(ignoredSponsorBlockSegment));
    }

    @Nullable
    private SponsorBlockSegment getActiveSponsorBlockActionableSegment(final long positionMillis) {
        return SponsorBlockPlaybackDecision.findFirstActionableSegment(
                sponsorBlockSegments,
                positionMillis,
                new SponsorBlockSegmentProvider(),
                new SponsorBlockCategoryStateProvider());
    }

    @Nullable
    private String getUpdatedIgnoredSponsorBlockSegment(final long positionMillis) {
        return SponsorBlockPlaybackDecision.resolveIgnoredSegmentForProgress(
                ignoredSponsorBlockSegment,
                sponsorBlockSegments,
                positionMillis,
                new SponsorBlockSegmentProvider(),
                this::getSegmentKey,
                new SponsorBlockCategoryStateProvider());
    }

    private boolean isSponsorBlockEnabled() {
        return prefs.getBoolean(context.getString(R.string.sponsor_block_enable_key), false);
    }


    private boolean isValidSponsorBlockSegment(@NonNull final SponsorBlockSegment segment) {
        return getSponsorBlockSegmentStartTimeMillis(segment) >= 0
                && getSponsorBlockSegmentEndTimeMillis(segment)
                > getSponsorBlockSegmentStartTimeMillis(segment)
                && getSponsorBlockSegmentCategory(segment) != null
                && getSponsorBlockSegmentAction(segment) != null;
    }

    private long getSponsorBlockSegmentStartTimeMillis(@NonNull final SponsorBlockSegment segment) {
        return Math.round(segment.startTime);
    }

    private long getSponsorBlockSegmentEndTimeMillis(@NonNull final SponsorBlockSegment segment) {
        return Math.round(segment.endTime);
    }

    @Nullable
    private SponsorBlockCategory getSponsorBlockSegmentCategory(
            @NonNull final SponsorBlockSegment segment) {
        return segment.category;
    }

    @Nullable
    private SponsorBlockAction getSponsorBlockSegmentAction(
            @NonNull final SponsorBlockSegment segment) {
        return segment.action;
    }

    @NonNull
    private SponsorBlockBehavior getSponsorBlockBehavior(
            @NonNull final SponsorBlockSegment segment) {
        return SponsorBlockCategoryRepository.getBehavior(
                context, getSponsorBlockSegmentCategory(segment));
    }

    private final class SponsorBlockCategoryStateProvider
            implements SponsorBlockPlaybackDecision.CategoryStateProvider {
        @Override
        public boolean isEnabled(@NonNull final SponsorBlockCategory category) {
            return isCategoryEnabled(category);
        }

        @NonNull
        @Override
        public SponsorBlockBehavior getBehavior(
                @NonNull final SponsorBlockCategory category) {
            return SponsorBlockCategoryRepository.getBehavior(context, category);
        }
    }

    private final class SponsorBlockSegmentProvider
            implements SponsorBlockPlaybackDecision.SegmentProvider<SponsorBlockSegment> {
        @Override
        public long getStartMillis(@NonNull final SponsorBlockSegment segment) {
            return getSponsorBlockSegmentStartTimeMillis(segment);
        }

        @Override
        public long getEndMillis(@NonNull final SponsorBlockSegment segment) {
            return getSponsorBlockSegmentEndTimeMillis(segment);
        }

        @Nullable
        @Override
        public SponsorBlockCategory getCategory(
                @NonNull final SponsorBlockSegment segment) {
            return getSponsorBlockSegmentCategory(segment);
        }

        @Nullable
        @Override
        public SponsorBlockAction getAction(
                @NonNull final SponsorBlockSegment segment) {
            return getSponsorBlockSegmentAction(segment);
        }
    }

    private boolean isCurrentStreamEligibleForSponsorBlockUi() {
        if (exoPlayerIsNull()) {
            return false;
        }
        final long durationMillis = simpleExoPlayer.getDuration();
        if (durationMillis <= 0 || durationMillis == C.TIME_UNSET) {
            return false;
        }

        return getCurrentStreamInfo()
                .map(StreamInfo::getStreamType)
                .map(streamType -> streamType == StreamType.VIDEO_STREAM
                        || streamType == StreamType.POST_LIVE_STREAM)
                .orElse(false);
    }

    private boolean isCategoryEnabled(@Nullable final SponsorBlockCategory category) {
        return category != null
                && SponsorBlockCategoryRepository.isApiCategoryEnabled(context, category);
    }

    @NonNull
    private String getSegmentKey(@NonNull final SponsorBlockSegment segment) {
        if (!isNullOrEmpty(segment.uuid)) {
            return segment.uuid;
        }
        return getSponsorBlockSegmentCategory(segment) + ":"
                + getSponsorBlockSegmentAction(segment) + ":"
                + getSponsorBlockSegmentStartTimeMillis(segment) + ":"
                + getSponsorBlockSegmentEndTimeMillis(segment);
    }

    @NonNull
    private String getSegmentCategoryName(@NonNull final SponsorBlockSegment segment) {
        final SponsorBlockCategory category = getSponsorBlockSegmentCategory(segment);
        if (category == null) {
            return context.getString(R.string.sponsor_block_skipped_segment_fallback);
        }
        switch (category) {
            case SPONSOR:
                return context.getString(R.string.sponsor_block_category_sponsor_title);
            case INTRO:
                return context.getString(R.string.sponsor_block_category_intro_title);
            case OUTRO:
                return context.getString(R.string.sponsor_block_category_outro_title);
            case INTERACTION:
                return context.getString(R.string.sponsor_block_category_interaction_title);
            case HIGHLIGHT:
                return context.getString(R.string.sponsor_block_category_highlight_title);
            case SELF_PROMO:
                return context.getString(R.string.sponsor_block_category_self_promo_title);
            case NON_MUSIC:
                return context.getString(R.string.sponsor_block_category_non_music_title);
            case PREVIEW:
                return context.getString(R.string.sponsor_block_category_preview_title);
            case FILLER:
                return context.getString(R.string.sponsor_block_category_filler_title);
            default:
                return context.getString(R.string.sponsor_block_skipped_segment_fallback);
        }
    }

    private Disposable getProgressUpdateDisposable() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                        AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
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
                ? com.google.android.exoplayer2.Player.STATE_IDLE
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
            case com.google.android.exoplayer2.Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case com.google.android.exoplayer2.Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case com.google.android.exoplayer2.Player.STATE_READY: //3
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case com.google.android.exoplayer2.Player.STATE_ENDED: // 4
                maybeFinishSleepTimerAtEndOfItem(currentQueueItem(), false);
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
        learningSessionTracker.update(currentItem, state == STATE_PLAYING,
                audioPlayerSelected());
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

        hideSponsorBlockManualSkipButton();
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

    private void onBuffering() {
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

        hideSponsorBlockManualSkipButton();
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

        hideSponsorBlockManualSkipButton();
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
        return exoPlayerIsNull() ? REPEAT_MODE_OFF : simpleExoPlayer.getRepeatMode();
    }

    public void cycleNextRepeatMode() {
        if (!exoPlayerIsNull()) {
            @RepeatMode final int repeatMode;
            switch (simpleExoPlayer.getRepeatMode()) {
                case REPEAT_MODE_OFF:
                    repeatMode = REPEAT_MODE_ONE;
                    break;
                case REPEAT_MODE_ONE:
                    repeatMode = REPEAT_MODE_ALL;
                    break;
                case REPEAT_MODE_ALL:
                default:
                    repeatMode = REPEAT_MODE_OFF;
                    break;
            }
            simpleExoPlayer.setRepeatMode(repeatMode);
        }
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: "
                    + "repeatMode = [" + repeatMode + "]");
        }
        UIs.call(playerUi -> playerUi.onRepeatModeChanged(repeatMode));
        notifyPlaybackUpdateToListeners();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: "
                    + "mode = [" + shuffleModeEnabled + "]");
        }

        if (playQueue != null) {
            if (shuffleModeEnabled && !playQueue.isShuffled()) {
                playQueue.shuffle();
            } else if (!shuffleModeEnabled && playQueue.isShuffled()) {
                playQueue.unshuffle();
            }
            if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE) {
                sleepTimerQueueTarget = lastQueueItem();
                sleepTimerQueueTargetFollowsLoading = !playQueue.isComplete();
                notifySleepTimerUpdateToListeners();
            }
        }

        UIs.call(playerUi -> playerUi.onShuffleModeEnabledChanged(shuffleModeEnabled));
        notifyPlaybackUpdateToListeners();
    }

    public void toggleShuffleModeEnabled() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.setShuffleModeEnabled(!simpleExoPlayer.getShuffleModeEnabled());
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Mute / Unmute
    //////////////////////////////////////////////////////////////////////////*/
    //region Mute / Unmute

    public void toggleMute() {
        if (exoPlayerIsNull() || audioReactor == null) {
            return;
        }
        muted = !muted;
        applyPlayerVolume();
        if (!muted) {
            audioReactor.requestAudioFocus();
        } else {
            audioReactor.abandonAudioFocus();
        }
        UIs.call(playerUi -> playerUi.onMuteUnmuteChanged(muted));
        notifyPlaybackUpdateToListeners();
    }

    public boolean isMuted() {
        return muted;
    }

    @NonNull
    public EqualizerState getEqualizerState() {
        return equalizerController.getState();
    }

    public boolean isEqualizerAvailable() {
        return equalizerController.isAvailable();
    }

    public boolean isEqualizerOperational() {
        return equalizerController.isOperational();
    }

    public void previewEqualizerState(@NonNull final EqualizerState state) {
        applyEqualizerState(state, false);
    }

    public void updateEqualizerState(@NonNull final EqualizerState state) {
        applyEqualizerState(state, true);
    }

    private void applyEqualizerState(@NonNull final EqualizerState state,
                                     final boolean persist) {
        final boolean enabledChanged =
                equalizerController.getState().isEnabled() != state.isEnabled();
        if (persist) {
            equalizerController.updateState(state);
        } else {
            equalizerController.previewState(state);
        }
        applyPlayerVolume();
        if (enabledChanged) {
            updateAudioTunneling();
        }
        UIs.call(playerUi -> playerUi.onEqualizerStateChanged(
                state, equalizerController.isOperational()));
    }

    private void updateAudioTunneling() {
        final boolean tunnelingEnabled = !prefs.getBoolean(
                context.getString(R.string.disable_media_tunneling_key), false)
                && !equalizerController.getState().isEnabled()
                && !playbackPresentationMode.allowsVisualizer();
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setTunnelingEnabled(tunnelingEnabled));
    }

    private void applyPlayerVolume() {
        if (!exoPlayerIsNull()) {
            final float equalizerHeadroom = equalizerController.getHeadroomMultiplier();
            simpleExoPlayer.setVolume(muted
                    ? 0.0f : sleepTimerVolumeMultiplier * equalizerHeadroom);
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Sleep timer
    //////////////////////////////////////////////////////////////////////////*/
    //region Sleep timer

    public void startSleepTimer(final long durationMillis, final boolean fadeOut) {
        prepareSleepTimerStart();
        sleepTimer.startDuration(durationMillis, fadeOut);
        startSleepTimerUpdates();
    }

    public boolean startSleepTimerAtEndOfCurrent(final boolean fadeOut) {
        final PlayQueueItem item = currentQueueItem();
        if (item == null) {
            return false;
        }

        prepareSleepTimerStart();
        sleepTimerCurrentTarget = item;
        sleepTimer.startEndOfCurrent(fadeOut);
        startSleepTimerUpdates();
        return true;
    }

    public boolean startSleepTimerAtEndOfQueue(final boolean fadeOut) {
        final PlayQueueItem item = lastQueueItem();
        if (item == null || playQueue == null) {
            return false;
        }

        prepareSleepTimerStart();
        sleepTimerQueueTarget = item;
        sleepTimerQueueTargetFollowsLoading = !playQueue.isComplete();
        sleepTimer.startEndOfQueue(fadeOut);
        startSleepTimerUpdates();
        return true;
    }

    private void prepareSleepTimerStart() {
        resetSleepTimerState();
        setSleepTimerVolumeMultiplier(1.0f);
    }

    public void cancelSleepTimer() {
        if (!sleepTimer.isActive()) {
            return;
        }
        resetSleepTimerState();
        setSleepTimerVolumeMultiplier(1.0f);
        notifySleepTimerUpdateToListeners();
    }

    private void clearSleepTimer() {
        resetSleepTimerState();
        setSleepTimerVolumeMultiplier(1.0f);
    }

    private void resetSleepTimerState() {
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        sleepTimer.cancel();
        sleepTimerCurrentTarget = null;
        sleepTimerQueueTarget = null;
        sleepTimerQueueTargetFollowsLoading = false;
    }

    private void startSleepTimerUpdates() {
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        sleepTimerHandler.post(sleepTimerTick);
    }

    private void onSleepTimerTick() {
        if (!sleepTimer.isActive()) {
            return;
        }
        if (sleepTimer.hasDurationExpired()) {
            finishSleepTimer(true);
            return;
        }

        updateSleepTimerFadeOut();
        notifySleepTimerUpdateToListeners();
        sleepTimerHandler.postDelayed(sleepTimerTick, SLEEP_TIMER_UPDATE_INTERVAL_MILLIS);
    }

    private void updateSleepTimerFadeOut() {
        final float volumeMultiplier = sleepTimer.getFadeOutVolumeMultiplier(
                getSleepTimerFadeOutRemainingMillis());
        setSleepTimerVolumeMultiplier(volumeMultiplier);
    }

    private void setSleepTimerVolumeMultiplier(final float volumeMultiplier) {
        final float clampedMultiplier = Math.max(0.0f, Math.min(1.0f, volumeMultiplier));
        if (Math.abs(sleepTimerVolumeMultiplier - clampedMultiplier) < 0.001f) {
            return;
        }
        sleepTimerVolumeMultiplier = clampedMultiplier;
        applyPlayerVolume();
    }

    private void maybeFinishSleepTimerAtEndOfItem(@Nullable final PlayQueueItem endedItem,
                                                   final boolean pausePlayback) {
        if (endedItem == null || !sleepTimer.isActive()) {
            return;
        }

        final boolean targetReached = (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT
                && isSameQueueItem(endedItem, sleepTimerCurrentTarget))
                || (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE
                && isSameQueueItem(endedItem, sleepTimerQueueTarget));
        if (targetReached) {
            finishSleepTimer(pausePlayback);
        }
    }

    private void finishSleepTimer(final boolean pausePlayback) {
        if (!sleepTimer.isActive()) {
            return;
        }

        resetSleepTimerState();
        if (pausePlayback && !exoPlayerIsNull() && getPlayWhenReady()) {
            pause();
        }
        setSleepTimerVolumeMultiplier(1.0f);
        notifySleepTimerUpdateToListeners();
        Toast.makeText(context, R.string.sleep_timer_finished, Toast.LENGTH_SHORT).show();
    }

    private void retargetSleepTimerForNewQueue() {
        if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT) {
            sleepTimerCurrentTarget = currentQueueItem();
            if (sleepTimerCurrentTarget == null) {
                clearSleepTimer();
            }
        } else if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE && playQueue != null) {
            sleepTimerQueueTarget = lastQueueItem();
            sleepTimerQueueTargetFollowsLoading = !playQueue.isComplete();
            if (sleepTimerQueueTarget == null) {
                clearSleepTimer();
            }
        }
    }

    private void retargetEndOfCurrentSleepTimer() {
        if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT) {
            sleepTimerCurrentTarget = currentQueueItem();
            notifySleepTimerUpdateToListeners();
        }
    }

    private void validateSleepTimerTargetsAfterQueueEdit() {
        if (playQueue == null) {
            return;
        }
        if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT
                && findQueueItemIndex(sleepTimerCurrentTarget) < 0) {
            sleepTimerCurrentTarget = currentQueueItem();
            if (sleepTimerCurrentTarget == null) {
                clearSleepTimer();
            }
        } else if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE
                && (sleepTimerQueueTargetFollowsLoading
                || findQueueItemIndex(sleepTimerQueueTarget) < 0)) {
            sleepTimerQueueTarget = lastQueueItem();
            sleepTimerQueueTargetFollowsLoading = !playQueue.isComplete();
            if (sleepTimerQueueTarget == null) {
                clearSleepTimer();
            }
        }
    }

    private long getSleepTimerFadeOutRemainingMillis() {
        if (sleepTimer.getMode() == SleepTimer.Mode.DURATION) {
            return sleepTimer.getDurationRemainingMillis();
        }
        if ((sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT
                && isSameQueueItem(currentQueueItem(), sleepTimerCurrentTarget))
                || (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE
                && isSameQueueItem(currentQueueItem(), sleepTimerQueueTarget))) {
            return getCurrentItemRemainingMillis();
        }
        return SleepTimer.REMAINING_TIME_UNSET;
    }

    public long getSleepTimerRemainingMillis() {
        if (sleepTimer.getMode() == SleepTimer.Mode.DURATION) {
            return sleepTimer.getDurationRemainingMillis();
        } else if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT) {
            return isSameQueueItem(currentQueueItem(), sleepTimerCurrentTarget)
                    ? getCurrentItemRemainingMillis() : SleepTimer.REMAINING_TIME_UNSET;
        } else if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_QUEUE) {
            return getQueueTargetRemainingMillis();
        }
        return SleepTimer.REMAINING_TIME_UNSET;
    }

    private long getQueueTargetRemainingMillis() {
        if (playQueue == null || exoPlayerIsNull()) {
            return SleepTimer.REMAINING_TIME_UNSET;
        }
        final int currentIndex = playQueue.getIndex();
        final int targetIndex = findQueueItemIndex(sleepTimerQueueTarget);
        if (targetIndex < currentIndex || targetIndex < 0) {
            return SleepTimer.REMAINING_TIME_UNSET;
        }

        long remainingMillis = getCurrentItemRemainingMillis();
        if (remainingMillis == SleepTimer.REMAINING_TIME_UNSET) {
            return SleepTimer.REMAINING_TIME_UNSET;
        }
        for (int i = currentIndex + 1; i <= targetIndex; i++) {
            final PlayQueueItem item = playQueue.getItem(i);
            if (item == null || item.getDuration() <= 0L) {
                return SleepTimer.REMAINING_TIME_UNSET;
            }
            final long itemDurationMillis = playbackTimeToWallClockMillis(
                    TimeUnit.SECONDS.toMillis(item.getDuration()));
            if (Long.MAX_VALUE - remainingMillis < itemDurationMillis) {
                return SleepTimer.REMAINING_TIME_UNSET;
            }
            remainingMillis += itemDurationMillis;
        }
        return remainingMillis;
    }

    private long getCurrentItemRemainingMillis() {
        if (exoPlayerIsNull()) {
            return SleepTimer.REMAINING_TIME_UNSET;
        }
        long durationMillis = simpleExoPlayer.getDuration();
        if (durationMillis == C.TIME_UNSET || durationMillis <= 0L) {
            final PlayQueueItem item = currentQueueItem();
            if (item == null || item.getDuration() <= 0L) {
                return SleepTimer.REMAINING_TIME_UNSET;
            }
            durationMillis = TimeUnit.SECONDS.toMillis(item.getDuration());
        }
        final long mediaRemainingMillis = Math.max(0L,
                durationMillis - Math.max(0L, simpleExoPlayer.getCurrentPosition()));
        return playbackTimeToWallClockMillis(mediaRemainingMillis);
    }

    private long playbackTimeToWallClockMillis(final long playbackTimeMillis) {
        final float speed = Math.max(0.01f, getPlaybackSpeed());
        return (long) (playbackTimeMillis / speed);
    }

    @Nullable
    private PlayQueueItem currentQueueItem() {
        return playQueue == null ? null : playQueue.getItem();
    }

    @Nullable
    private PlayQueueItem lastQueueItem() {
        return playQueue == null || playQueue.isEmpty()
                ? null : playQueue.getItem(playQueue.size() - 1);
    }

    private int findQueueItemIndex(@Nullable final PlayQueueItem target) {
        if (playQueue == null || target == null) {
            return -1;
        }
        return playQueue.indexOf(target);
    }

    private static boolean isSameQueueItem(@Nullable final PlayQueueItem first,
                                           @Nullable final PlayQueueItem second) {
        return first != null && first == second;
    }

    public boolean isSleepTimerActive() {
        return sleepTimer.isActive();
    }

    @NonNull
    public SleepTimer.Mode getSleepTimerMode() {
        return sleepTimer.getMode();
    }

    public boolean isSleepTimerFadeOutEnabled() {
        return sleepTimer.isFadeOutEnabled();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer listeners (that didn't fit in other categories)
    //////////////////////////////////////////////////////////////////////////*/
    //region ExoPlayer listeners (that didn't fit in other categories)

    @Override
    public void onAudioSessionIdChanged(final int audioSessionId) {
        equalizerController.attachAudioSession(audioSessionId);
        applyPlayerVolume();
        UIs.call(playerUi -> playerUi.onEqualizerStateChanged(
                equalizerController.getState(), equalizerController.isOperational()));
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
     * @param player The {@link com.google.android.exoplayer2.Player} whose state changed.
     * @param events The {@link com.google.android.exoplayer2.Player.Events} that has triggered
     *               the player state changes.
     **/
    @Override
    public void onEvents(@NonNull final com.google.android.exoplayer2.Player player,
                         @NonNull final com.google.android.exoplayer2.Player.Events events) {
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
            applyPlaybackSpeedProfile(currentItem);
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

        if (discontinuityReason == DISCONTINUITY_REASON_SEEK && !sponsorBlockSkipInProgress) {
            final String seekTargetSegmentKey = getActiveSponsorBlockSegmentKey(
                    newPosition.positionMs);
            ignoredSponsorBlockSegment = SponsorBlockPlaybackDecision
                    .resolveIgnoredSegmentAfterManualSeek(
                            seekTargetSegmentKey,
                            prefs.getBoolean(
                                    context.getString(R.string.sponsor_block_graced_rewind_key),
                                    true),
                            skippedSponsorBlockSegments);
        }
        if (sponsorBlockSkipInProgress) {
            sponsorBlockSkipInProgress = false;
        }

        // Refresh the playback if there is a transition to the next video
        final int newIndex = newPosition.mediaItemIndex;
        if (newIndex != oldPosition.mediaItemIndex) {
            UIs.call(PlayerUi::onMediaItemTransition);
            cancelPendingMediaUrlRecovery();
            mediaUrlRecoveryGuard.reset();
        }
        if (discontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
            maybeFinishSleepTimerAtEndOfItem(playQueue.getItem(oldPosition.mediaItemIndex), true);
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

        if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT
                && discontinuityReason != DISCONTINUITY_REASON_AUTO_TRANSITION) {
            sleepTimerCurrentTarget = playQueue.getItem(newIndex);
            notifySleepTimerUpdateToListeners();
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

    /**
     * To be called when the {@code PlaybackPreparer} set in the {@link MediaSessionConnector}
     * receives an {@code onPrepare()} call. This function allows restoring the default behavior
     * that would happen if there was no playback preparer set, i.e. to just call
     * {@code player.prepare()}. You can find the default behavior in `onPlay()` inside the
     * {@link MediaSessionConnector} file.
     */
    public void onPrepare() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.prepare();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Errors
    //////////////////////////////////////////////////////////////////////////*/
    //region Errors

    /**
     * Process exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
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
     * @see com.google.android.exoplayer2.Player.Listener#onPlayerError(PlaybackException)
     */
    // Any error code not explicitly covered here is either unrelated to the WizeStream use case
    // (e.g. DRM) or not recoverable (e.g. Decoder error). In both cases, the player should
    // shutdown.
    @SuppressWarnings("SwitchIntDef")
    @Override
    public void onPlayerError(@NonNull final PlaybackException error) {
        Log.e(TAG, "ExoPlayer - onPlayerError() called with:", error);

        saveStreamProgressState();
        boolean isCatchableException = false;

        if (tryRecoverFromYouTubeMediaUrlFailure(error)) {
            return;
        }

        switch (error.errorCode) {
            case ERROR_CODE_BEHIND_LIVE_WINDOW:
                isCatchableException = true;
                simpleExoPlayer.seekToDefaultPosition();
                simpleExoPlayer.prepare();
                // Inform the user that we are reloading the stream by
                // switching to the buffering state
                onBuffering();
                break;
            case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
            case ERROR_CODE_IO_BAD_HTTP_STATUS:
            case ERROR_CODE_IO_FILE_NOT_FOUND:
            case ERROR_CODE_IO_NO_PERMISSION:
            case ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED:
            case ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
            case ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                // Source errors, signal on playQueue and move on:
                if (!exoPlayerIsNull() && playQueue != null) {
                    playQueue.error();
                }
                break;
            case ERROR_CODE_TIMEOUT:
            case ERROR_CODE_IO_UNSPECIFIED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
            case ERROR_CODE_UNSPECIFIED:
                // Reload playback on unexpected errors:
                setRecovery();
                reloadPlayQueueManager();
                break;
            default:
                // API, remote and renderer errors belong here:
                onPlaybackShutdown();
                break;
        }

        if (!isCatchableException) {
            createErrorNotification(error);
        }

        if (fragmentListener != null) {
            fragmentListener.onPlayerError(error, isCatchableException);
        }
    }


    private boolean tryRecoverFromYouTubeMediaUrlFailure(@NonNull final PlaybackException error) {
        if (playQueue == null) {
            return false;
        }

        final PlayQueueItem item = playQueue.getItem();
        if (!PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(error, item)) {
            return false;
        }

        final String recoveryKey = item.getServiceId() + ":" + item.getUrl();
        final PlayerHttpErrorRecovery.RecoveryAttempt attempt =
                mediaUrlRecoveryGuard.acquireAttempt(recoveryKey);
        if (attempt == null) {
            Log.w(TAG, "YouTube media URL recovery exhausted after "
                    + PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS + " attempts");
            cancelPendingMediaUrlRecovery();
            invalidateYouTubeMediaCaches(item);
            mediaUrlRecoveryGuard.reset();
            if (!exoPlayerIsNull()) {
                simpleExoPlayer.pause();
            }
            changeState(STATE_PAUSED);
            createErrorNotification(error, "recovery=exhausted, attempts="
                    + PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS + "/"
                    + PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS);
            if (fragmentListener != null) {
                fragmentListener.onPlayerError(error, true);
            }
            return true;
        }

        setRecovery();
        onBuffering();
        cancelPendingMediaUrlRecovery();

        final Runnable recovery = () -> {
            pendingMediaUrlRecovery = null;
            if (playQueue == null) {
                return;
            }
            final PlayQueueItem currentQueueItem = playQueue.getItem();
            if (currentQueueItem == null
                    || currentQueueItem.getServiceId() != item.getServiceId()
                    || !currentQueueItem.getUrl().equals(item.getUrl())) {
                return;
            }

            final Integer responseCode = PlayerHttpErrorRecovery.findInvalidResponseCode(error);
            Log.w(TAG, "Refreshing YouTube StreamInfo after recoverable media URL failure"
                    + " (status=" + (responseCode == null ? "network" : responseCode)
                    + ", attempt=" + attempt.getNumber() + "/"
                    + PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS + ")");
            getSelectedVideoStream()
                    .filter(stream -> PlayerHttpErrorRecovery
                            .shouldAvoidAndroidVrAv1HfrStream(error, stream))
                    .ifPresent(stream -> videoResolver.rejectVideoStreamOnce(
                            item.getUrl(), stream.getItag()));
            invalidateYouTubeMediaCaches(item);
            reloadPlayQueueManager();
        };
        pendingMediaUrlRecovery = recovery;
        mediaUrlRecoveryHandler.postDelayed(recovery, attempt.getDelayMillis());
        return true;
    }

    private void cancelPendingMediaUrlRecovery() {
        if (pendingMediaUrlRecovery == null) {
            return;
        }
        mediaUrlRecoveryHandler.removeCallbacks(pendingMediaUrlRecovery);
        pendingMediaUrlRecovery = null;
    }

    private static void invalidateYouTubeMediaCaches(@NonNull final PlayQueueItem item) {
        PlayerDataSource.invalidateYoutubeManifestCaches();
        InfoCache.getInstance().removeInfo(item.getServiceId(), item.getUrl(),
                InfoCache.Type.STREAM);
    }

    private void createErrorNotification(@NonNull final PlaybackException error) {
        createErrorNotification(error, null);
    }

    private void createErrorNotification(@NonNull final PlaybackException error,
                                         @Nullable final String recoveryDiagnostic) {
        final String safeErrorContext = PlayerHttpErrorRecovery.buildSafeErrorContext(error);
        final StringBuilder diagnosticSuffix = new StringBuilder();
        if (safeErrorContext != null) {
            diagnosticSuffix.append(" [").append(safeErrorContext).append(']');
        }
        if (recoveryDiagnostic != null) {
            diagnosticSuffix.append(" [").append(recoveryDiagnostic).append(']');
        }

        final ErrorInfo errorInfo;
        if (currentMetadata == null) {
            errorInfo = new ErrorInfo(error, UserAction.PLAY_STREAM,
                    "Player error[type=" + error.getErrorCodeName()
                            + "] occurred, currentMetadata is null" + diagnosticSuffix);
        } else {
            errorInfo = new ErrorInfo(error, UserAction.PLAY_STREAM,
                    "Player error[type=" + error.getErrorCodeName()
                            + "] occurred while playing " + currentMetadata.getStreamUrl()
                            + diagnosticSuffix,
                    currentMetadata.getServiceId(), currentMetadata.getStreamUrl());
        }
        ErrorUtil.createNotification(context, errorInfo);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback position and seek
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback position and seek

    @Override // own playback listener (this is a getter)
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (exoPlayerIsNull() || isLive() || !isPlaying()) {
            return false;
        }

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        final long currentDurationMillis = simpleExoPlayer.getDuration();
        return currentDurationMillis - currentPositionMillis < timeToEndMillis;
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge.
     *
     * @return whether the livestream is playing at or beyond the edge
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLiveEdge() {
        if (exoPlayerIsNull() || !isLive()) {
            return false;
        }

        final Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
        final int currentWindowIndex = simpleExoPlayer.getCurrentMediaItemIndex();
        if (currentTimeline.isEmpty() || currentWindowIndex < 0
                || currentWindowIndex >= currentTimeline.getWindowCount()) {
            return false;
        }

        final Timeline.Window timelineWindow = new Timeline.Window();
        currentTimeline.getWindow(currentWindowIndex, timelineWindow);
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer.getCurrentPosition();
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

        learningSessionTracker.stop();
        currentItem = item;
        learningSessionTracker.update(currentItem, currentState == STATE_PLAYING,
                audioPlayerSelected());
        applyPlaybackSpeedProfile(item);

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
                onThumbnailLoaded(null);
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
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]");
        }
        if (!exoPlayerIsNull()) {
            // prevent invalid positions when fast-forwarding/-rewinding
            simpleExoPlayer.seekTo(MathUtils.clamp(positionMillis, 0,
                    simpleExoPlayer.getDuration()));
        }
    }

    private void seekBy(final long offsetMillis) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]");
        }
        seekTo(simpleExoPlayer.getCurrentPosition() + offsetMillis);
    }

    public void seekToDefault() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.seekToDefaultPosition();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player actions (play, pause, previous, fast-forward, ...)
    //////////////////////////////////////////////////////////////////////////*/
    //region Player actions (play, pause, previous, fast-forward, ...)

    public void play() {
        if (DEBUG) {
            Log.d(TAG, "play() called");
        }
        if (audioReactor == null || playQueue == null || exoPlayerIsNull()) {
            return;
        }

        if (!isMuted()) {
            audioReactor.requestAudioFocus();
        }

        if (currentState == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        if (isStopped()) {
            // Some phones suspend a paused player after 10 minutes. This causes the player to
            // enter STATE_IDLE, causing playback to fail. So we try to recover from that here.
            setRecovery();
            reloadPlayQueueManager();
        }

        simpleExoPlayer.play();
        saveStreamProgressState();
    }

    public void pause() {
        if (DEBUG) {
            Log.d(TAG, "pause() called");
        }
        if (audioReactor == null || exoPlayerIsNull()) {
            return;
        }

        audioReactor.abandonAudioFocus();
        simpleExoPlayer.pause();
        saveStreamProgressState();
    }

    public void playPause() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPause() called");
        }

        if (getPlayWhenReady()
                // When state is completed (replay button is shown) then (re)play and do not pause
                && currentState != STATE_COMPLETED) {
            pause();
        } else {
            play();
        }
    }

    public void playPrevious() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPrevious() called");
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track. Also restart the track if the current track
         * is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS
                || playQueue.getIndex() == 0) {
            seekToDefault();
            playQueue.offsetIndex(0);
        } else {
            saveStreamProgressState();
            playQueue.offsetIndex(-1);
        }
        retargetEndOfCurrentSleepTimer();
        triggerProgressUpdate();
    }

    public void playNext() {
        if (DEBUG) {
            Log.d(TAG, "onPlayNext() called");
        }
        if (playQueue == null) {
            return;
        }

        saveStreamProgressState();
        playQueue.offsetIndex(+1);
        retargetEndOfCurrentSleepTimer();
        triggerProgressUpdate();
    }

    public void fastForward() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
    }

    public void fastRewind() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(-retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // StreamInfo history: views and progress
    //////////////////////////////////////////////////////////////////////////*/
    //region StreamInfo history: views and progress

    private void registerStreamViewed() {
        if (currentItem != null && currentItem.isLocalMedia()) {
            databaseUpdateDisposable.add(recordManager.onViewed(currentItem)
                    .onErrorComplete().subscribe());
        } else {
            getCurrentStreamInfo().ifPresent(info -> databaseUpdateDisposable
                    .add(recordManager.onViewed(info).onErrorComplete().subscribe()));
        }
    }

    private void saveStreamProgressState(final long progressMillis) {
        if (currentItem != null && currentItem.isLocalMedia()) {
            if (!prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
                return;
            }
            databaseUpdateDisposable.add(recordManager.saveStreamState(currentItem, progressMillis)
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorComplete()
                    .subscribe());
            return;
        }
        getCurrentStreamInfo().ifPresent(info -> {
            if (!prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "saveStreamProgressState() called with: progressMillis=" + progressMillis
                        + ", currentMetadata=[" + info.getName() + "]");
            }

            databaseUpdateDisposable.add(recordManager.saveStreamState(info, progressMillis)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError(e -> {
                        if (DEBUG) {
                            e.printStackTrace();
                        }
                    })
                    .onErrorComplete()
                    .subscribe());
        });
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

        applyPlaybackSpeedProfile(info);
        updateSponsorBlockSegments(info);
        maybeAutoQueueNextStream(info);

        loadCurrentThumbnail(ExtractorImageCompat.thumbnailImages(info));
        registerStreamViewed();

        notifyMetadataUpdateToListeners();
        notifyAudioTrackUpdateToListeners();
        UIs.call(playerUi -> playerUi.onMetadataChanged(info));
    }

    private void updateMetadataForLocalMedia(@NonNull final PlayQueueItem item) {
        skippedSponsorBlockSegments.clear();
        ignoredSponsorBlockSegment = null;
        sponsorBlockSegments = Collections.emptyList();
        hideSponsorBlockManualSkipButton();
        clearSponsorBlockSeekBarMarkers();
        onThumbnailLoaded(null);
        databaseUpdateDisposable.add(recordManager.onViewed(item)
                .onErrorComplete().subscribe());
        notifyMetadataUpdateToListeners();
        notifyAudioTrackUpdateToListeners();
        UIs.call(playerUi -> playerUi.onMetadataChanged(currentMetadata));
    }

    private void updateSponsorBlockSegments(@NonNull final StreamInfo info) {
        skippedSponsorBlockSegments.clear();
        ignoredSponsorBlockSegment = null;
        hideSponsorBlockManualSkipButton();
        sponsorBlockSkipInProgress = false;
        if (!isSponsorBlockEnabled()) {
            sponsorBlockSegments = Collections.emptyList();
            clearSponsorBlockSeekBarMarkers();
            return;
        }
        final SponsorBlockSegment[] segments = info.getSponsorBlockSegments();
        sponsorBlockSegments = segments == null ? Collections.emptyList() : Arrays.asList(segments);
        updateSponsorBlockSeekBarMarkers();
    }

    @Nullable
    private String getActiveSponsorBlockSegmentKey(final long positionMillis) {
        if (!isSponsorBlockEnabled() || sponsorBlockSegments.isEmpty()) {
            return null;
        }
        final SponsorBlockSegment segment = getActiveSponsorBlockActionableSegment(positionMillis);
        return segment == null ? null : getSegmentKey(segment);
    }

    private void updateSponsorBlockSeekBarMarkers() {
        if (!isSponsorBlockEnabled() || sponsorBlockSegments.isEmpty()
                || !isCurrentStreamEligibleForSponsorBlockUi()) {
            clearSponsorBlockSeekBarMarkers();
            return;
        }

        final List<SponsorBlockSegment> markerSegments = new ArrayList<>();
        for (final SponsorBlockSegment segment : sponsorBlockSegments) {
            if (isValidSponsorBlockMarkerSegment(segment)) {
                markerSegments.add(segment);
            }
        }

        if (markerSegments.isEmpty()) {
            clearSponsorBlockSeekBarMarkers();
            return;
        }

        UIs.call(ui -> ui.updateSponsorBlockSeekBarMarkers(markerSegments,
                simpleExoPlayer.getDuration()));
    }

    private boolean isValidSponsorBlockMarkerSegment(@NonNull final SponsorBlockSegment segment) {
        return isValidSponsorBlockSegment(segment)
                && (getSponsorBlockSegmentAction(segment) == SponsorBlockAction.SKIP
                || getSponsorBlockSegmentAction(segment) == SponsorBlockAction.POI)
                && isCategoryEnabled(getSponsorBlockSegmentCategory(segment));
    }

    private void clearSponsorBlockSeekBarMarkers() {
        UIs.call(PlayerUi::clearSponsorBlockSeekBarMarkers);
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
        return currentThumbnail;
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
        if (sleepTimer.getMode() == SleepTimer.Mode.END_OF_CURRENT) {
            sleepTimerCurrentTarget = item;
            notifySleepTimerUpdateToListeners();
        }
    }

    @Override
    public void onPlayQueueEdited() {
        validateSleepTimerTargetsAfterQueueEdit();
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
        fragmentListener = listener;
        UIs.call(PlayerUi::onFragmentListenerSet);
        notifyQueueUpdateToListeners();
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        notifySleepTimerUpdateToListeners();
        triggerProgressUpdate();
    }

    public void removeFragmentListener(final PlayerServiceEventListener listener) {
        if (fragmentListener == listener) {
            fragmentListener = null;
        }
    }

    void setActivityListener(final PlayerEventListener listener) {
        activityListener = listener;
        // TODO why not queue update?
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        notifySleepTimerUpdateToListeners();
        triggerProgressUpdate();
    }

    void removeActivityListener(final PlayerEventListener listener) {
        if (activityListener == listener) {
            activityListener = null;
        }
    }

    void stopActivityBinding() {
        if (fragmentListener != null) {
            fragmentListener.onServiceStopped();
            fragmentListener = null;
        }
        if (activityListener != null) {
            activityListener.onServiceStopped();
            activityListener = null;
        }
    }

    private void notifyQueueUpdateToListeners() {
        if (fragmentListener != null && playQueue != null) {
            fragmentListener.onQueueUpdate(playQueue);
        }
        if (activityListener != null && playQueue != null) {
            activityListener.onQueueUpdate(playQueue);
        }
    }

    private void notifyMetadataUpdateToListeners() {
        final Optional<StreamInfo> streamInfo = getCurrentStreamInfo();
        streamInfo.ifPresent(info -> {
            if (fragmentListener != null) {
                fragmentListener.onMetadataUpdate(info, playQueue);
            }
            if (activityListener != null) {
                activityListener.onMetadataUpdate(info, playQueue);
            }
        });
        if (streamInfo.isEmpty() && currentMetadata != null && playQueue != null) {
            if (fragmentListener != null) {
                fragmentListener.onMetadataUpdate(currentMetadata, playQueue);
            }
            if (activityListener != null) {
                activityListener.onMetadataUpdate(currentMetadata, playQueue);
            }
        }
    }

    private void notifyPlaybackUpdateToListeners() {
        if (fragmentListener != null && !exoPlayerIsNull() && playQueue != null) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && !exoPlayerIsNull() && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void notifyProgressUpdateToListeners(final int currentProgress,
                                                 final int duration,
                                                 final int bufferPercent) {
        if (fragmentListener != null) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    private void notifyAudioTrackUpdateToListeners() {
        if (fragmentListener != null) {
            fragmentListener.onAudioTrackUpdate();
        }
        if (activityListener != null) {
            activityListener.onAudioTrackUpdate();
        }
    }

    private void notifySleepTimerUpdateToListeners() {
        final SleepTimer.Mode mode = sleepTimer.getMode();
        final long remainingMillis = getSleepTimerRemainingMillis();
        final boolean fadeOutEnabled = sleepTimer.isFadeOutEnabled();
        UIs.call(playerUi -> playerUi.onSleepTimerChanged(
                mode, remainingMillis, fadeOutEnabled));
        if (fragmentListener != null) {
            fragmentListener.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled);
        }
        if (activityListener != null) {
            activityListener.onSleepTimerChanged(mode, remainingMillis, fadeOutEnabled);
        }
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
        try {
            return !exoPlayerIsNull() && simpleExoPlayer.isCurrentMediaItemDynamic();
        } catch (final IndexOutOfBoundsException e) {
            // Why would this even happen =(... but lets log it anyway, better safe than sorry
            if (DEBUG) {
                Log.d(TAG, "player.isCurrentWindowDynamic() failed: ", e);
            }
            return false;
        }
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
        return Optional.ofNullable(fragmentListener);
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
        return screenOn;
    }
}
