package org.schabi.newpipe.player;

import static androidx.media3.common.Player.DiscontinuityReason;
import static androidx.media3.common.Player.Listener;
import static androidx.media3.common.Player.RepeatMode;
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
import androidx.preference.PreferenceManager;

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
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.equalizer.EqualizerState;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.CustomRenderersFactory;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.SleepTimer;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.player.notification.NotificationPlayerUi;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.player.ui.BackgroundPlayerUi;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.PlayerUiList;
import org.schabi.newpipe.player.ui.PopupPlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.player.visualizer.VisualizerAudioProcessor;
import org.schabi.newpipe.util.ListHelper;

import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

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
    private PlayQueueItem currentItem;
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
    private final PopupPlayerReturnState popupPlayerReturnState =
            new PopupPlayerReturnState();

    @NonNull
    private final SleepTimerPlaybackController sleepTimerController;
    @NonNull
    private final PlayerQueueModeController queueModeController;
    // audio only mode does not mean that player type is background, but that the player was
    // minimized to background but will resume automatically to the original player type

    /*//////////////////////////////////////////////////////////////////////////
    // UIs, listeners and disposables
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressWarnings({"MemberName", "java:S116"}) // keep the unusual member name
    private final PlayerUiList UIs;

    @NonNull
    private final PlayerBroadcastController broadcastController;
    @NonNull
    private final PlayerCaptionController captionController;
    @NonNull
    private final PlayerEventDispatcher eventDispatcher;
    @NonNull
    private final PlayerErrorController errorController;
    @NonNull
    private final PlayerIntentController intentController;
    @NonNull
    private final PlayerThumbnailController thumbnailController;
    @NonNull
    private final PlayerLocalMetadataController localMetadataController;
    @NonNull
    private final PlayerLifecycleController lifecycleController;
    @NonNull
    private final PlayerMedia3ListenerController media3ListenerController;
    @NonNull
    private final PlayerMetadataController metadataController;
    @NonNull
    private final PlayerProgressController progressController;
    @NonNull
    private final PlayerPresentationController presentationController;
    @NonNull
    private final PlayerQueueSynchronizer queueSynchronizer;
    @NonNull
    private final PlayerSeekController seekController;
    @NonNull
    private final PlayerStateController stateController;
    @NonNull
    private final PlayerStreamController streamController;
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
        stateController = new PlayerStateController(this, historyController,
                sleepTimerController, sponsorBlockController, progressController, eventDispatcher);
        seekController = new PlayerSeekController(this);
        transportController = new PlayerTransportController(this, sleepTimerController);
        thumbnailController = new PlayerThumbnailController(this);
        queueSynchronizer = new PlayerQueueSynchronizer(this, historyController,
                playbackParametersController, thumbnailController);
        localMetadataController = new PlayerLocalMetadataController(this);
        metadataController = new PlayerMetadataController(this, context, historyController,
                playbackParametersController, sponsorBlockController, thumbnailController,
                localMetadataController);
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
        captionController = new PlayerCaptionController(this, context, prefs, trackSelector);
        streamController = new PlayerStreamController(this, context, audioResolver, videoResolver,
                dataSource, loadController);
        presentationController = new PlayerPresentationController(this, videoResolver,
                trackSelector, visualizerAudioProcessor);
        intentController = new PlayerIntentController(this, context, historyController,
                presentationController, popupPlayerReturnState, videoResolver,
                streamItemDisposable);
        errorController = new PlayerErrorController(this, eventDispatcher, videoResolver);
        media3ListenerController = new PlayerMedia3ListenerController(this, audioController,
                metadataController, playbackParametersController, sponsorBlockController,
                errorController, sleepTimerController);

        // The UIs added here should always be present. They will be initialized when the player
        // reaches the initialization step. Make sure the media session ui is before the
        // notification ui in the UIs list, since the notification depends on the media session in
        // PlayerUi#initPlayer(), and UIs.call() guarantees UI order is preserved.
        UIs = new PlayerUiList(
                new MediaSessionPlayerUi(this, mediaSession, browserPlayer),
                new NotificationPlayerUi(this)
        );
        lifecycleController = new PlayerLifecycleController(this, context, service, renderFactory,
                trackSelector, loadController, audioController, broadcastController,
                errorController, historyController, thumbnailController, localMetadataController,
                sleepTimerController, progressController, playbackParametersController,
                streamItemDisposable);
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

    public void handleIntent(@NonNull final Intent intent) {
        intentController.handle(intent);
    }


    public void handleIntentPost(final PlayerType oldPlayerType) {
        intentController.handlePost(oldPlayerType);
    }

    void initUIsForCurrentPlayerType() {
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

    void initPlayback(@NonNull final PlayQueue queue, final boolean playOnReady) {
        lifecycleController.initPlayback(queue, playOnReady);
    }

    void setPlayQueueForLifecycle(@NonNull final PlayQueue queue) {
        playQueue = queue;
    }

    void setExoPlayerForLifecycle(@NonNull final ExoPlayer exoPlayer) {
        simpleExoPlayer = exoPlayer;
    }

    void setAudioReactorForLifecycle(@NonNull final AudioReactor reactor) {
        audioReactor = reactor;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Destroy and recovery
    //////////////////////////////////////////////////////////////////////////*/
    //region Destroy and recovery

    public void destroy() {
        lifecycleController.destroy();
    }

    public void setRecovery() {
        lifecycleController.setRecovery();
    }

    public void reloadPlayQueueManager() {
        lifecycleController.reloadPlayQueueManager();
    }

    @Override // own playback listener
    public void onPlaybackShutdown() {
        lifecycleController.shutdown();
    }

    public void smoothStopForImmediateReusing() {
        lifecycleController.smoothStopForImmediateReusing();
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

    void stopProgressLoop() {
        progressController.stop();
    }

    public boolean isProgressLoopRunning() {
        return progressController.isRunning();
    }

    public void triggerProgressUpdate() {
        progressController.trigger();
    }

    boolean isPreparedForProgressUpdates() {
        return stateController.isPrepared();
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states
    @Override
    public void onPlayWhenReadyChanged(final boolean playWhenReady, final int reason) {
        stateController.onPlayWhenReadyChanged(playWhenReady, reason);
    }

    @Override
    public void onPlaybackStateChanged(final int playbackState) {
        stateController.onPlaybackStateChanged(playbackState);
    }

    @Override // exoplayer listener
    public void onIsLoadingChanged(final boolean isLoading) {
        stateController.onIsLoadingChanged(isLoading);
    }

    @Override // own playback listener
    public void onPlaybackBlock() {
        stateController.block();
    }

    @Override // own playback listener
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        stateController.unblock(mediaSource);
    }

    public void changeState(final int state) {
        stateController.changeState(state);
    }

    void onBuffering() {
        stateController.onBuffering();
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
                && !getPlaybackPresentationMode().allowsVisualizer();
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
        media3ListenerController.onAudioSessionIdChanged(audioSessionId);
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
        media3ListenerController.onEvents(player);
    }

    @Override
    public void onTimelineChanged(@NonNull final Timeline timeline, final int reason) {
        media3ListenerController.onTimelineChanged(timeline, reason);
    }

    @Override
    public void onTracksChanged(@NonNull final Tracks tracks) {
        media3ListenerController.onTracksChanged(tracks);
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        media3ListenerController.onPlaybackParametersChanged(playbackParameters);
    }

    @Override
    public void onPositionDiscontinuity(@NonNull final PositionInfo oldPosition,
                                        @NonNull final PositionInfo newPosition,
                                        @DiscontinuityReason final int discontinuityReason) {
        media3ListenerController.onPositionDiscontinuity(
                oldPosition, newPosition, discontinuityReason);
    }

    @Override
    public void onRenderedFirstFrame() {
        media3ListenerController.onRenderedFirstFrame();
    }

    @Override
    public void onCues(@NonNull final CueGroup cueGroup) {
        media3ListenerController.onCues(cueGroup);
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
        queueSynchronizer.synchronize(item, wasBlocked);
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

    void registerStreamViewed() {
        historyController.registerViewed();
    }

    private void saveStreamProgressState(final long progressMillis) {
        historyController.saveProgress(progressMillis);
    }

    public void saveStreamProgressState() {
        if (exoPlayerIsNull() || getCurrentMetadata() == null || playQueue == null
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

    @NonNull
    public String getVideoUrl() {
        return metadataController.videoUrl();
    }

    @NonNull
    public String getVideoUrlAtCurrentTime() {
        return metadataController.videoUrlAtCurrentTime();
    }

    @NonNull
    public String getVideoTitle() {
        return metadataController.videoTitle();
    }

    @NonNull
    public String getUploaderName() {
        return metadataController.uploaderName();
    }

    @Nullable
    public Bitmap getThumbnail() {
        return metadataController.thumbnail();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    //////////////////////////////////////////////////////////////////////////*/
    //region Play queue, segments and streams

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
        return streamController.sourceOf(info);
    }

    @Override
    @Nullable
    public MediaSource sourceOfLocal(final PlayQueueItem item) {
        return streamController.sourceOfLocal(item);
    }

    public void disablePreloadingOfCurrentTrack() {
        streamController.disablePreloadingOfCurrentTrack();
    }

    public Optional<VideoStream> getSelectedVideoStream() {
        return metadataController.selectedVideoStream();
    }

    public Optional<AudioStream> getSelectedAudioStream() {
        return metadataController.selectedAudioStream();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region Captions (text tracks)

    public int getCaptionRendererIndex() {
        return captionController.rendererIndex();
    }

    @Nullable
    public String getCaptionPreference() {
        return captionController.preference();
    }

    public void setCaptionPreference(@Nullable final String language) {
        captionController.setPreference(language);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Video size
    //////////////////////////////////////////////////////////////////////////*/
    //region Video size
    @Override // exoplayer listener
    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
        media3ListenerController.onVideoSizeChanged(videoSize);
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

    void notifyQueueUpdateToListeners() {
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
        presentationController.useVideoAndSubtitles(videoAndSubtitlesEnabled);
    }

    public void setPlaybackPresentationMode(
            @NonNull final PlaybackPresentationMode newMode) {
        presentationController.setMode(newMode);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    public Optional<StreamInfo> getCurrentStreamInfo() {
        return metadataController.currentStreamInfo();
    }

    public int getCurrentState() {
        return stateController.getCurrentState();
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

    boolean isLive() {
        return seekController.isLive();
    }

    public void setPlaybackQuality(@Nullable final String quality) {
        streamController.setPlaybackQuality(quality);
    }

    public void setAudioTrack(@Nullable final String audioTrackId) {
        streamController.setAudioTrack(audioTrackId);
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

    void setPlayerTypeForIntent(@NonNull final PlayerType newPlayerType) {
        playerType = newPlayerType;
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
        return presentationController.isAudioOnly();
    }

    @NonNull
    public PlaybackPresentationMode getPlaybackPresentationMode() {
        return presentationController.getMode();
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
        return metadataController.getCurrentMetadata();
    }

    @Nullable
    public PlayQueueItem getCurrentItem() {
        return currentItem;
    }

    void setCurrentItemForPlaybackSynchronization(@NonNull final PlayQueueItem item) {
        currentItem = item;
    }

    void clearCurrentPlaybackForBlock() {
        currentItem = null;
        metadataController.clear();
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

    //endregion

    /**
     * @return whether the device screen is turned on.
     */
    public boolean isScreenOn() {
        return broadcastController.isScreenOn();
    }
}
