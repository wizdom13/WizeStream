package org.schabi.newpipe.player.ui;

import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.Player.RENDERER_UNAVAILABLE;
import static org.schabi.newpipe.player.Player.STATE_BUFFERING;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED;
import static org.schabi.newpipe.player.Player.STATE_PAUSED_SEEK;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;
import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextResizeModeAndSaveToPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrieveSeekDurationFromPreferences;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.BitmapCompat;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.player.DanmakuController;
import org.schabi.newpipe.player.LiveQualityController;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.DisplayPortion;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playback.SurfaceHolderCallback;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHelper;
import org.schabi.newpipe.player.seekbarpreview.SeekbarPreviewThumbnailHolder;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.player.PlayerFastSeekOverlay;
import org.schabi.newpipe.util.image.ExtractorImageCompat;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class VideoPlayerUi extends PlayerUi implements SeekBar.OnSeekBarChangeListener,
        PopupMenu.OnMenuItemClickListener, PopupMenu.OnDismissListener {
    private static final String TAG = VideoPlayerUi.class.getSimpleName();

    // time constants
    public static final long DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final long DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final long DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds
    public static final int SEEK_OVERLAY_DURATION = 450; // 450 millis

    // other constants (TODO remove playback speeds and use normal menu for popup, too)
    private static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private enum PlayButtonAction {
        PLAY, PAUSE, REPLAY
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected PlayerBinding binding;
    private final BottomPlayerControls bottomPlayerControls;
    private final PlayerUiTheme playerUiTheme;
    private final DanmakuController danmakuController;
    private final Handler controlsVisibilityHandler = new Handler(Looper.getMainLooper());
    private long lastTvBackPressedAt;
    @Nullable
    private SurfaceHolderCallback surfaceHolderCallback;
    @Nullable
    private Bitmap scaledEndScreenThumbnail;
    boolean surfaceIsSetup = false;


    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/

    private static final int POPUP_MENU_ID_QUALITY = 69;
    private static final int AUTO_QUALITY_MENU_ITEM_ID = Integer.MAX_VALUE;
    private static final int BEST_QUALITY_MENU_ITEM_ID = Integer.MAX_VALUE - 1;
    private static final int POPUP_MENU_ID_AUDIO_TRACK = 70;
    private static final int POPUP_MENU_ID_PLAYBACK_SPEED = 79;
    private static final int POPUP_MENU_ID_CAPTION = 89;

    protected boolean isSomePopupMenuVisible = false;
    private PopupMenu qualityPopupMenu;
    private PopupMenu audioTrackPopupMenu;
    protected PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;


    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/

    private GestureDetector gestureDetector;
    private BasePlayerGestureListener playerGestureListener;
    @Nullable
    private View.OnLayoutChangeListener onLayoutChangeListener = null;
    private int controlsBasePadding;
    private int topControlsBasePadding;

    @NonNull
    private final SeekbarPreviewThumbnailHolder seekbarPreviewThumbnailHolder =
            new SeekbarPreviewThumbnailHolder();


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor, setup, destroy

    protected VideoPlayerUi(@NonNull final Player player,
                            @NonNull final PlayerBinding playerBinding) {
        super(player);
        binding = playerBinding;
        bottomPlayerControls = new BottomPlayerControls(binding);
        playerUiTheme = new PlayerUiTheme(context, binding.playbackSeekBar);
        danmakuController = new DanmakuController(player, binding);
        setupFromView();
        player.getCurrentStreamInfo().ifPresent(danmakuController::onMetadataChanged);
    }

    public void setupFromView() {
        initViews();
        initListeners();
        setupPlayerSeekOverlay();
    }

    private void initViews() {
        setupSubtitleView();

        binding.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));

        applyPlayerSeekBarColor();

        final ContextThemeWrapper themeWrapper = new ContextThemeWrapper(context,
                R.style.PlayerOverlayPopupMenu);

        qualityPopupMenu = new PopupMenu(themeWrapper, binding.qualityTextView);
        audioTrackPopupMenu = new PopupMenu(themeWrapper, binding.audioTrackTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, binding.playbackSpeed);
        captionPopupMenu = new PopupMenu(themeWrapper, binding.captionTextView);

        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

        binding.titleTextView.setSelected(true);
        binding.channelTextView.setSelected(true);

        // Prevent hiding of bottom sheet via swipe inside queue
        binding.itemsList.setNestedScrollingEnabled(false);
    }

    private void applyPlayerSeekBarColor() {
        playerUiTheme.refresh();
    }

    abstract BasePlayerGestureListener buildGestureListener();

    protected void initListeners() {
        binding.qualityTextView.setOnClickListener(makeOnClickListener(this::onQualityClicked));
        binding.audioTrackTextView.setOnClickListener(
                makeOnClickListener(this::onAudioTracksClicked));
        binding.playbackSpeed.setOnClickListener(makeOnClickListener(this::onPlaybackSpeedClicked));

        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.captionTextView.setOnClickListener(makeOnClickListener(this::onCaptionClicked));
        binding.resizeTextView.setOnClickListener(makeOnClickListener(this::onResizeClicked));
        binding.danmakuToggle.setOnClickListener(makeOnClickListener(danmakuController::toggle));
        binding.playbackLiveSync.setOnClickListener(makeOnClickListener(player::seekToDefault));

        playerGestureListener = buildGestureListener();
        gestureDetector = new GestureDetector(context, playerGestureListener);
        binding.getRoot().setOnTouchListener(playerGestureListener);

        binding.repeatButton.setOnClickListener(v -> onRepeatClicked());
        binding.shuffleButton.setOnClickListener(v -> onShuffleClicked());

        binding.playPauseButton.setOnClickListener(makeOnClickListener(player::playPause));
        binding.playPreviousButton.setOnClickListener(makeOnClickListener(player::playPrevious));
        binding.playNextButton.setOnClickListener(makeOnClickListener(player::playNext));

        binding.moreOptionsButton.setOnClickListener(
                makeOnClickListener(this::onMoreOptionsClicked));
        binding.share.setOnClickListener(makeOnClickListener(() -> {
            final PlayQueueItem currentItem = player.getCurrentItem();
            if (currentItem != null) {
                ShareUtils.shareText(context, currentItem.getTitle(),
                        player.getVideoUrlAtCurrentTime(),
                        ExtractorImageCompat.thumbnailImages(currentItem));
            }
        }));
        binding.share.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(context, player.getVideoUrlAtCurrentTime());
            return true;
        });
        binding.fullScreenButton.setOnClickListener(makeOnClickListener(() -> {
            player.setRecovery();
            NavigationHelper.playOnMainPlayer(context,
                    Objects.requireNonNull(player.getPlayQueue()), true);
        }));
        binding.playWithKodi.setOnClickListener(makeOnClickListener(this::onPlayWithKodiClicked));
        binding.openInBrowser.setOnClickListener(makeOnClickListener(this::onOpenInBrowserClicked));
        binding.playerCloseButton.setOnClickListener(makeOnClickListener(() ->
                // set package to this app's package to prevent the intent from being seen outside
                context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER)
                        .setPackage(App.PACKAGE_NAME))
        ));
        binding.switchMute.setOnClickListener(makeOnClickListener(player::toggleMute));

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, (view, windowInsets) -> {
            final Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (!cutout.equals(Insets.NONE)) {
                view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            }
            return windowInsets;
        });

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays and fast_seek_overlay too. Without it they will be off-centered.
        onLayoutChangeListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    updateFullscreenOverlayInsets();
                    binding.playerOverlays.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                            v.getPaddingRight(), v.getPaddingBottom());

                    // If we added padding to the fast seek overlay, too, it would not go under the
                    // system ui. Instead we apply negative margins equal to the window insets of
                    // the opposite side, so that the view covers all of the player (overflowing on
                    // some sides) and its center coincides with the center of other controls.
                    final RelativeLayout.LayoutParams fastSeekParams = (RelativeLayout.LayoutParams)
                            binding.fastSeekOverlay.getLayoutParams();
                    fastSeekParams.leftMargin = -v.getPaddingRight();
                    fastSeekParams.topMargin = -v.getPaddingBottom();
                    fastSeekParams.rightMargin = -v.getPaddingLeft();
                    fastSeekParams.bottomMargin = -v.getPaddingTop();
                };
        binding.playbackControlRoot.addOnLayoutChangeListener(onLayoutChangeListener);
    }

    protected void deinitListeners() {
        binding.qualityTextView.setOnClickListener(null);
        binding.audioTrackTextView.setOnClickListener(null);
        binding.playbackSpeed.setOnClickListener(null);
        binding.playbackSeekBar.setOnSeekBarChangeListener(null);
        binding.captionTextView.setOnClickListener(null);
        binding.resizeTextView.setOnClickListener(null);
        binding.danmakuToggle.setOnClickListener(null);
        binding.playbackLiveSync.setOnClickListener(null);

        binding.getRoot().setOnTouchListener(null);
        playerGestureListener = null;
        gestureDetector = null;

        binding.repeatButton.setOnClickListener(null);
        binding.shuffleButton.setOnClickListener(null);

        binding.playPauseButton.setOnClickListener(null);
        binding.playPreviousButton.setOnClickListener(null);
        binding.playNextButton.setOnClickListener(null);

        binding.moreOptionsButton.setOnClickListener(null);
        binding.moreOptionsButton.setOnLongClickListener(null);
        binding.share.setOnClickListener(null);
        binding.share.setOnLongClickListener(null);
        binding.fullScreenButton.setOnClickListener(null);
        binding.screenRotationButton.setOnClickListener(null);
        binding.playWithKodi.setOnClickListener(null);
        binding.openInBrowser.setOnClickListener(null);
        binding.playerCloseButton.setOnClickListener(null);
        binding.switchMute.setOnClickListener(null);

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, null);

        binding.playbackControlRoot.removeOnLayoutChangeListener(onLayoutChangeListener);
    }

    /**
     * Initializes the Fast-For/Backward overlay.
     */
    private void setupPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(() -> retrieveSeekDurationFromPreferences(player) / 1000)
                .performListener(new PlayerFastSeekOverlay.PerformListener() {

                    @Override
                    public void onDoubleTap() {
                        animate(binding.fastSeekOverlay, true, SEEK_OVERLAY_DURATION);
                    }

                    @Override
                    public void onDoubleTapEnd() {
                        animate(binding.fastSeekOverlay, false, SEEK_OVERLAY_DURATION);
                    }

                    @NonNull
                    @Override
                    public FastSeekDirection getFastSeekDirection(
                            @NonNull final DisplayPortion portion
                    ) {
                        if (player.exoPlayerIsNull()) {
                            // Abort seeking
                            playerGestureListener.endMultiDoubleTap();
                            return FastSeekDirection.NONE;
                        }
                        if (portion == DisplayPortion.LEFT) {
                            // Check if it's possible to rewind
                            // Small puffer to eliminate infinite rewind seeking
                            if (player.getExoPlayer().getCurrentPosition() < 500L) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.BACKWARD;
                        } else if (portion == DisplayPortion.RIGHT) {
                            // Check if it's possible to fast-forward
                            if (player.getCurrentState() == STATE_COMPLETED
                                    || player.getExoPlayer().getCurrentPosition()
                                    >= player.getExoPlayer().getDuration()) {
                                return FastSeekDirection.NONE;
                            }
                            return FastSeekDirection.FORWARD;
                        }
                        /* portion == DisplayPortion.MIDDLE */
                        return FastSeekDirection.NONE;
                    }

                    @Override
                    public void seek(final boolean forward) {
                        playerGestureListener.keepInDoubleTapMode();
                        if (forward) {
                            player.fastForward();
                        } else {
                            player.fastRewind();
                        }
                    }
                });
        playerGestureListener.doubleTapControls(binding.fastSeekOverlay);
    }

    public void deinitPlayerSeekOverlay() {
        binding.fastSeekOverlay
                .seekSecondsSupplier(null)
                .performListener(null);
    }

    @Override
    public void setupAfterIntent() {
        super.setupAfterIntent();
        applyPlayerSeekBarColor();
        setupElementsVisibility();
        setupElementsSize(context.getResources());
        binding.getRoot().setVisibility(View.VISIBLE);
        if (shouldRequestPlaybackButtonFocus()) {
            binding.playPauseButton.requestFocus();
        }
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        setupVideoSurfaceIfNeeded();
    }

    @Override
    public void initPlayback() {
        super.initPlayback();

        // #6825 - Ensure that the shuffle-button is in the correct state on the UI
        setShuffleButton(player.getExoPlayer().getShuffleModeEnabled());

        // Set repeat button to the correct UI state
        setRepeatButton(player.getExoPlayer().getRepeatMode());

    }

    public abstract void removeViewFromParent();

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        clearVideoSurface();
    }

    @Override
    public void destroy() {
        playerUiTheme.close();
        danmakuController.destroy();
        super.destroy();
        clearScaledEndScreenThumbnail();
        deinitPlayerSeekOverlay();
        deinitListeners();
        bottomPlayerControls.restore();
    }

    protected void setupElementsVisibility() {
        setMuteButton(player.isMuted());
        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    protected abstract void setupElementsSize(Resources resources);

    protected void setupElementsSize(final int buttonsMinWidth,
                                     final int playerTopPad,
                                     final int controlsPad,
                                     final int buttonsPad,
                                     final int iconButtonsPad) {
        controlsBasePadding = controlsPad;
        topControlsBasePadding = playerTopPad;
        binding.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0);
        binding.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0);
        binding.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.audioTrackTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setMinimumWidth(buttonsMinWidth);
        binding.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.sleepTimerButton.setPadding(
                iconButtonsPad, iconButtonsPad, iconButtonsPad, iconButtonsPad);
        updateFullscreenOverlayInsets();
    }

    protected final void updateFullscreenOverlayInsets() {
        binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        bottomPlayerControls.applyWindowInsets(isFullscreen(),
                controlsBasePadding, topControlsBasePadding);
    }

    static int calculateTopControlsPadding(final boolean fullscreen,
                                           final int basePadding,
                                           final int statusBarInset,
                                           final int displayCutoutInset) {
        final int safeBasePadding = Math.max(basePadding, 0);
        if (!fullscreen) {
            return safeBasePadding;
        }
        return safeBasePadding + Math.max(Math.max(statusBarInset, displayCutoutInset), 0);
    }

    static int calculateControlsEdgePadding(final boolean fullscreen,
                                            final int basePadding,
                                            final int systemBarInset,
                                            final int displayCutoutInset) {
        final int safeBasePadding = Math.max(basePadding, 0);
        if (!fullscreen) {
            return safeBasePadding;
        }
        return safeBasePadding + Math.max(Math.max(systemBarInset, displayCutoutInset), 0);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            // When the orientation changes, the screen height might be smaller. If the end screen
            // thumbnail is not re-scaled, it can be larger than the current screen height and thus
            // enlarging the whole player. This causes the seekbar to be out of the visible area.
            updateEndScreenThumbnail(player.getThumbnail());
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail
    //////////////////////////////////////////////////////////////////////////*/
    //region Thumbnail

    /**
     * Scale the player audio / end screen thumbnail down if necessary.
     * <p>
     * This is necessary when the thumbnail's height is larger than the device's height
     * and thus is enlarging the player's height
     * causing the bottom playback controls to be out of the visible screen.
     * </p>
     */
    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        updateEndScreenThumbnail(bitmap);
    }

    private void updateEndScreenThumbnail(@Nullable final Bitmap thumbnail) {
        if (thumbnail == null) {
            clearScaledEndScreenThumbnail();
            return;
        }

        final float endScreenHeight = calculateMaxEndScreenThumbnailHeight(thumbnail);
        final Bitmap endScreenBitmap;
        if (needsThumbnailScaling(thumbnail.getHeight(), endScreenHeight)) {
            final int targetHeight = Math.max(1, Math.round(endScreenHeight));
            final int targetWidth = Math.max(
                    1,
                    Math.round(thumbnail.getWidth()
                            * (targetHeight / (float) thumbnail.getHeight()))
            );
            endScreenBitmap = BitmapCompat.createScaledBitmap(
                    thumbnail,
                    targetWidth,
                    targetHeight,
                    null,
                    true);
        } else {
            endScreenBitmap = thumbnail;
        }

        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onThumbnailLoaded() called with: "
                    + "currentThumbnail = [" + thumbnail + "], "
                    + thumbnail.getWidth() + "x" + thumbnail.getHeight()
                    + ", scaled end screen height = " + endScreenHeight
                    + ", scaled end screen width = " + endScreenBitmap.getWidth());
        }

        clearScaledEndScreenThumbnail();
        if (endScreenBitmap != thumbnail) {
            scaledEndScreenThumbnail = endScreenBitmap;
        }
        binding.endScreen.setImageBitmap(endScreenBitmap);
    }

    static boolean needsThumbnailScaling(final int sourceHeight, final float targetHeight) {
        return targetHeight > 0 && targetHeight < sourceHeight;
    }

    private void clearScaledEndScreenThumbnail() {
        binding.endScreen.setImageDrawable(null);
        if (scaledEndScreenThumbnail != null && !scaledEndScreenThumbnail.isRecycled()) {
            scaledEndScreenThumbnail.recycle();
        }
        scaledEndScreenThumbnail = null;
    }

    protected abstract float calculateMaxEndScreenThumbnailHeight(@NonNull Bitmap bitmap);
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region Progress loop and updates

    @Override
    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {

        if (duration != binding.playbackSeekBar.getMax()) {
            setVideoDurationToControls(duration);
        }
        if (player.getCurrentState() != STATE_PAUSED) {
            updatePlayBackElementsCurrentDuration(currentProgress);
        }
        if (player.isLoading() || bufferPercent > 90) {
            binding.playbackSeekBar.setSecondaryProgress(
                    (int) (binding.playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!player.isLiveEdge());
    }

    /**
     * Sets the current duration into the corresponding elements.
     *
     * @param currentProgress the current progress, in milliseconds
     */
    private void updatePlayBackElementsCurrentDuration(final int currentProgress) {
        // Don't set seekbar progress while user is seeking
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            binding.playbackSeekBar.setProgress(currentProgress);
        }
        binding.playbackCurrentTime.setText(getTimeString(currentProgress));
    }

    /**
     * Sets the video duration time into all control components (e.g. seekbar).
     *
     * @param duration the video duration, in milliseconds
     */
    private void setVideoDurationToControls(final int duration) {
        binding.playbackEndTime.setText(getTimeString(duration));

        binding.playbackSeekBar.setMax(duration);
        // This is important for Android TVs otherwise it would apply the default from
        // setMax/Min methods which is (max - min) / 20
        binding.playbackSeekBar.setKeyProgressIncrement(
                PlayerHelper.retrieveSeekDurationFromPreferences(player));
    }

    @Override // seekbar listener
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        // Currently we don't need method execution when fromUser is false
        if (!fromUser) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }

        binding.currentDisplaySeek.setText(getTimeString(progress));

        // Seekbar Preview Thumbnail
        SeekbarPreviewThumbnailHelper
                .tryResizeAndSetSeekbarPreviewThumbnail(
                        player.getContext(),
                        seekbarPreviewThumbnailHolder.getBitmapAt(progress).orElse(null),
                        binding.currentSeekbarPreviewThumbnail,
                        binding.subtitleView::getWidth);

        adjustSeekbarPreviewContainer();
    }


    private void adjustSeekbarPreviewContainer() {
        try {
            // Should only be required when an error occurred before
            // and the layout was positioned in the center
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.NO_GRAVITY);

            // Calculate the current left position of seekbar progress in px
            // More info: https://stackoverflow.com/q/20493577
            final int currentSeekbarLeft =
                    binding.playbackSeekBar.getLeft()
                            + binding.playbackSeekBar.getPaddingLeft()
                            + binding.playbackSeekBar.getThumb().getBounds().left;

            // Calculate the (unchecked) left position of the container
            final int uncheckedContainerLeft =
                    currentSeekbarLeft - (binding.seekbarPreviewContainer.getWidth() / 2);

            // Fix the position so it's within the boundaries
            final int checkedContainerLeft = MathUtils.clamp(uncheckedContainerLeft,
                    0, binding.playbackWindowRoot.getWidth()
                            - binding.seekbarPreviewContainer.getWidth());

            // See also: https://stackoverflow.com/a/23249734
            final LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            binding.seekbarPreviewContainer.getLayoutParams());
            params.setMarginStart(checkedContainerLeft);
            binding.seekbarPreviewContainer.setLayoutParams(params);
        } catch (final Exception ex) {
            Log.e(TAG, "Failed to adjust seekbarPreviewContainer", ex);
            // Fallback - position in the middle
            binding.bottomSeekbarPreviewLayout.setGravity(Gravity.CENTER);
        }
    }

    @Override // seekbar listener
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (player.getCurrentState() != STATE_PAUSED_SEEK) {
            player.changeState(STATE_PAUSED_SEEK);
        }

        showControls(0);
        animate(binding.currentDisplaySeek, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
    }

    @Override // seekbar listener
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }

        player.seekTo(seekBar.getProgress());
        if (player.getExoPlayer().getDuration() == seekBar.getProgress()) {
            player.getExoPlayer().play();
        }

        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        animate(binding.currentSeekbarPreviewThumbnail, false, 200, AnimationType.SCALE_AND_ALPHA);

        if (player.getCurrentState() == STATE_PAUSED_SEEK) {
            player.changeState(STATE_BUFFERING);
        }
        if (!player.isProgressLoopRunning()) {
            player.startProgressLoop();
        }

        showControlsThenHide();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    //////////////////////////////////////////////////////////////////////////*/
    //region Controls showing / hiding

    public boolean isControlsVisible() {
        return binding != null && binding.playbackControlRoot.getVisibility() == View.VISIBLE;
    }

    public void showControlsThenHide() {
        updateFullscreenOverlayInsets();
        applyPlayerSeekBarColor();
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }

        showOrHideButtons();
        showSystemUIPartially();

        final long hideTime = binding.playbackControlRoot.isInTouchMode()
                ? DEFAULT_CONTROLS_HIDE_TIME
                : DPAD_CONTROLS_HIDE_TIME;

        showHideShadow(true, DEFAULT_CONTROLS_DURATION);
        animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.ALPHA, 0, () -> hideControls(DEFAULT_CONTROLS_DURATION, hideTime));
    }

    public void showControls(final long duration) {
        updateFullscreenOverlayInsets();
        applyPlayerSeekBarColor();
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        showOrHideButtons();
        showSystemUIPartially();
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration);
        animate(binding.playbackControlRoot, true, duration);
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]");
        }

        showOrHideButtons();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            showHideShadow(false, duration);
            animate(binding.playbackControlRoot, false, duration, AnimationType.ALPHA,
                    0, this::hideSystemUIIfNeeded);
        }, delay);
    }

    public void showHideShadow(final boolean show, final long duration) {
        animate(binding.playbackControlsShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerTopShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerBottomShadow, show, duration, AnimationType.ALPHA, 0, null);
    }

    protected void showOrHideButtons() {
        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final boolean showPrev = playQueue.getIndex() != 0;
        final boolean showNext = playQueue.getIndex() + 1 != playQueue.getStreams().size();

        binding.playPreviousButton.setVisibility(showPrev ? View.VISIBLE : View.INVISIBLE);
        binding.playPreviousButton.setAlpha(showPrev ? 1.0f : 0.0f);
        binding.playNextButton.setVisibility(showNext ? View.VISIBLE : View.INVISIBLE);
        binding.playNextButton.setAlpha(showNext ? 1.0f : 0.0f);
    }

    protected void showSystemUIPartially() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected void hideSystemUIIfNeeded() {
        // system UI is really changed only by MainPlayerUi, so overridden there
    }

    protected boolean isAnyListViewOpen() {
        // only MainPlayerUi has list views for the queue and for segments, so overridden there
        return false;
    }

    public boolean isFullscreen() {
        // only MainPlayerUi can be in fullscreen, so overridden there
        return false;
    }

    /**
     * Update the play/pause button ({@link R.id.playPauseButton}) to reflect the action
     * that will be performed when the button is clicked..
     * @param action the action that is performed when the play/pause button is clicked
     */
    private void updatePlayPauseButton(final PlayButtonAction action) {
        final AppCompatImageButton button = binding.playPauseButton;
        switch (action) {
            case PLAY:
                button.setContentDescription(context.getString(R.string.play));
                button.setImageResource(R.drawable.ic_play_arrow);
                break;
            case PAUSE:
                button.setContentDescription(context.getString(R.string.pause));
                button.setImageResource(R.drawable.ic_pause);
                break;
            case REPLAY:
                button.setContentDescription(context.getString(R.string.replay));
                button.setImageResource(R.drawable.ic_replay);
                break;
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states

    @Override
    public void onPrepared() {
        super.onPrepared();
        setVideoDurationToControls((int) player.getExoPlayer().getDuration());
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        binding.playbackSeekBar.setPlaybackActive(false);

        binding.surfaceView.clearAspectRatio();

        // if we are e.g. switching players, hide controls
        hideControls(DEFAULT_CONTROLS_DURATION, 0);

        binding.playbackSeekBar.setEnabled(false);
        applyPlayerSeekBarColor();

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animate(binding.loadingPanel, true, 0);
        animate(binding.surfaceForeground, true, 100);

        updatePlayPauseButton(PlayButtonAction.PLAY);
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onMediaItemTransition() {
        super.onMediaItemTransition();
        binding.surfaceView.clearAspectRatio();
        danmakuController.reset();
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        binding.playbackSeekBar.setPlaybackActive(true);

        updateStreamRelatedViews();
        danmakuController.start();

        binding.playbackSeekBar.setEnabled(true);
        applyPlayerSeekBarColor();

        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.PAUSE);
                    animatePlayButtons(true, 200);
                    if (shouldRequestPlaybackButtonFocus()) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.suspendForBuffering();
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        binding.loadingPanel.setVisibility(View.VISIBLE);
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onPaused() {
        super.onPaused();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.pause();

        // Don't let UI elements popup during double tap seeking. This state is entered sometimes
        // during seeking/loading. This if-else check ensures that the controls aren't popping up.
        if (!playerGestureListener.isDoubleTapping()) {
            showControls(400);
            binding.loadingPanel.setVisibility(View.GONE);

            animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                    () -> {
                        updatePlayPauseButton(PlayButtonAction.PLAY);
                        animatePlayButtons(true, 200);
                        if (shouldRequestPlaybackButtonFocus()) {
                        binding.playPauseButton.requestFocus();
                    }
                    });
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.suspendForBuffering();
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(true);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        binding.playbackSeekBar.setPlaybackActive(false);
        danmakuController.pause();
        binding.danmakuOverlay.clearComments();

        animate(binding.playPauseButton, false, 0, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    updatePlayPauseButton(PlayButtonAction.REPLAY);
                    animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                });

        binding.getRoot().setKeepScreenOn(false);

        // When a (short) video ends the elements have to display the correct values - see #6180
        updatePlayBackElementsCurrentDuration(binding.playbackSeekBar.getMax());

        showControls(500);
        binding.screenRotationButton.setVisibility(isFullscreen() ? View.VISIBLE
                : binding.screenRotationButton.getVisibility());
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        binding.loadingPanel.setVisibility(View.GONE);
        animate(binding.surfaceForeground, true, 100);
    }

    static boolean shouldRequestPlaybackButtonFocus(final boolean tv,
                                                    final boolean fullscreen,
                                                    final boolean popup,
                                                    final boolean listOpen,
                                                    final boolean hasFocus) {
        return !listOpen && (!tv || ((fullscreen || popup) && !hasFocus));
    }

    protected boolean shouldRequestPlaybackButtonFocus() {
        return shouldRequestPlaybackButtonFocus(
                DeviceUtils.isTv(context),
                isFullscreen(),
                player.popupPlayerSelected(),
                isAnyListViewOpen(),
                binding.getRoot().getRootView().findFocus() != null);
    }

    private void animatePlayButtons(final boolean show, final long duration) {
        animate(binding.playPauseButton, show, duration, AnimationType.SCALE_AND_ALPHA);

        @Nullable final PlayQueue playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        if (!show || playQueue.getIndex() > 0) {
            animate(
                    binding.playPreviousButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
        if (!show || playQueue.getIndex() + 1 < playQueue.getStreams().size()) {
            animate(
                    binding.playNextButton,
                    show,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Repeat, shuffle, mute
    //////////////////////////////////////////////////////////////////////////*/
    //region Repeat, shuffle, mute

    public void onRepeatClicked() {
        if (DEBUG) {
            Log.d(TAG, "onRepeatClicked() called");
        }
        player.cycleNextRepeatMode();
    }

    public void onShuffleClicked() {
        if (DEBUG) {
            Log.d(TAG, "onShuffleClicked() called");
        }
        player.toggleShuffleModeEnabled();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);

        if (repeatMode == REPEAT_MODE_ALL) {
            binding.repeatButton.setImageResource(
                    androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_all);
        } else if (repeatMode == REPEAT_MODE_ONE) {
            binding.repeatButton.setImageResource(
                    androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_one);
        } else /* repeatMode == REPEAT_MODE_OFF */ {
            binding.repeatButton.setImageResource(
                    androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_off);
        }
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        setShuffleButton(shuffleModeEnabled);
    }

    @Override
    public void onMuteUnmuteChanged(final boolean isMuted) {
        super.onMuteUnmuteChanged(isMuted);
        setMuteButton(isMuted);
    }

    private void setMuteButton(final boolean isMuted) {
        binding.switchMute.setImageDrawable(AppCompatResources.getDrawable(context, isMuted
                ? R.drawable.ic_volume_off : R.drawable.ic_volume_up));
    }

    private void setShuffleButton(final boolean shuffled) {
        binding.shuffleButton.setImageAlpha(shuffled ? 255 : 77);
    }

    private void setRepeatButton(final int repeatMode) {
        final int resId = switch (repeatMode) {
            case REPEAT_MODE_ALL
                    -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_all;
            case REPEAT_MODE_ONE
                    -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_one;
            default -> androidx.media3.ui.R.drawable.exo_legacy_controls_repeat_off;
        };
        binding.repeatButton.setImageResource(resId);
    }

    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Other player listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region Other player listeners

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        binding.playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onRenderedFirstFrame() {
        super.onRenderedFirstFrame();
        restoreVideoAspectRatioFromPlayer();
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Metadata & stream related views
    //////////////////////////////////////////////////////////////////////////*/
    //region Metadata & stream related views

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        binding.surfaceView.resetUserTransform();

        danmakuController.onMetadataChanged(info);
        updateStreamRelatedViews();

        binding.titleTextView.setText(info.getName());
        binding.channelTextView.setText(info.getUploaderName());
        binding.channelAvatarView.setContentDescription(info.getUploaderName());
        CoilHelper.INSTANCE.loadAvatar(binding.channelAvatarView,
                ExtractorImageCompat.uploaderAvatarImages(info));

        this.seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), info.getPreviewFrames());
    }

    @Override
    public void onMetadataChanged(@NonNull final MediaItemTag tag) {
        super.onMetadataChanged(tag);
        danmakuController.reset();
        binding.surfaceView.resetUserTransform();
        binding.qualityTextView.setVisibility(View.GONE);
        binding.audioTrackTextView.setVisibility(View.GONE);
        binding.playbackLiveSync.setVisibility(View.GONE);
        binding.playbackEndTime.setVisibility(View.VISIBLE);
        binding.endScreen.setVisibility(tag.getStreamType() == StreamType.AUDIO_STREAM
                ? View.VISIBLE : View.GONE);
        binding.surfaceView.setVisibility(tag.getStreamType() == StreamType.AUDIO_STREAM
                ? View.GONE : View.VISIBLE);
        buildPlaybackSpeedMenu();
        binding.playbackSpeed.setVisibility(View.VISIBLE);
        binding.titleTextView.setText(tag.getTitle());
        binding.channelTextView.setText(tag.getUploaderName());
        binding.channelAvatarView.setContentDescription(tag.getUploaderName());
        CoilHelper.INSTANCE.clearAvatar(binding.channelAvatarView);
        binding.channelAvatarView.setImageResource(R.drawable.placeholder_person);
        seekbarPreviewThumbnailHolder.resetFrom(player.getContext(), Collections.emptyList());
    }

    protected void updateStreamRelatedViews() {
        if (!player.getPlaybackPresentationMode().rendersVideo()) {
            binding.qualityTextView.setVisibility(View.GONE);
            binding.audioTrackTextView.setVisibility(View.GONE);
            binding.surfaceView.setVisibility(View.GONE);
            binding.endScreen.setVisibility(player.getPlaybackPresentationMode().allowsVisualizer()
                    ? View.GONE : View.VISIBLE);
            binding.playbackLiveSync.setVisibility(View.GONE);
            binding.playbackEndTime.setVisibility(View.VISIBLE);
            buildPlaybackSpeedMenu();
            binding.playbackSpeed.setVisibility(View.VISIBLE);
            return;
        }
        player.getCurrentStreamInfo().ifPresent(info -> {
            binding.qualityTextView.setVisibility(View.GONE);
            binding.audioTrackTextView.setVisibility(View.GONE);
            binding.playbackSpeed.setVisibility(View.GONE);

            binding.playbackEndTime.setVisibility(View.GONE);
            binding.playbackLiveSync.setVisibility(View.GONE);

            switch (info.getStreamType()) {
                case AUDIO_STREAM:
                case POST_LIVE_AUDIO_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;

                case AUDIO_LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.GONE);
                    binding.endScreen.setVisibility(View.VISIBLE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    break;

                case LIVE_STREAM:
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackLiveSync.setVisibility(View.VISIBLE);
                    updateQualityLabel(null);
                    break;

                case VIDEO_STREAM:
                case POST_LIVE_STREAM:
                    if (player.getCurrentMetadata() != null
                            && player.getCurrentMetadata().getMaybeQuality().isEmpty()
                            || (info.getVideoStreams().isEmpty()
                            && info.getVideoOnlyStreams().isEmpty())) {
                        break;
                    }

                    buildQualityMenu();
                    buildAudioTrackMenu();

                    binding.qualityTextView.setVisibility(View.VISIBLE);
                    binding.surfaceView.setVisibility(View.VISIBLE);
                    // fallthrough
                default:
                    binding.endScreen.setVisibility(View.GONE);
                    binding.playbackEndTime.setVisibility(View.VISIBLE);
                    break;
            }

            buildPlaybackSpeedMenu();
            binding.playbackSpeed.setVisibility(View.VISIBLE);
        });
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup menus ("popup" means that they pop up, not that they belong to the popup player)

    private void buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return;
        }
        qualityPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_QUALITY);

        if (player.isLiveQualityPlayback()) {
            buildLiveQualityMenu();
            return;
        }

        final List<VideoStream> availableStreams = Optional.ofNullable(player.getCurrentMetadata())
                .flatMap(MediaItemTag::getMaybeQuality)
                .map(MediaItemTag.Quality::getSortedVideoStreams)
                .orElse(null);
        if (availableStreams == null) {
            return;
        }

        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                AUTO_QUALITY_MENU_ITEM_ID,
                Menu.NONE,
                R.string.auto);
        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                BEST_QUALITY_MENU_ITEM_ID,
                Menu.NONE,
                R.string.best_resolution);
        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.getResolution());
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
        updateQualityLabel(null);
    }

    private void buildLiveQualityMenu() {
        qualityPopupMenu.getMenu().add(
                POPUP_MENU_ID_QUALITY,
                0,
                Menu.NONE,
                R.string.auto);

        final List<LiveQualityController.Option> options = player.getLiveQualityOptions();
        for (int index = 0; index < options.size(); index++) {
            qualityPopupMenu.getMenu().add(
                    POPUP_MENU_ID_QUALITY,
                    index + 1,
                    Menu.NONE,
                    options.get(index).getLabel());
        }

        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
        updateQualityLabel(null);
    }

    private void buildAudioTrackMenu() {
        if (audioTrackPopupMenu == null) {
            return;
        }
        audioTrackPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_AUDIO_TRACK);

        final List<AudioStream> availableStreams = Optional.ofNullable(player.getCurrentMetadata())
                .flatMap(MediaItemTag::getMaybeAudioTrack)
                .map(MediaItemTag.AudioTrack::getAudioStreams)
                .orElse(null);
        if (availableStreams == null || availableStreams.size() < 2) {
            return;
        }

        for (int i = 0; i < availableStreams.size(); i++) {
            final AudioStream audioStream = availableStreams.get(i);
            audioTrackPopupMenu.getMenu().add(POPUP_MENU_ID_AUDIO_TRACK, i, Menu.NONE,
                    Localization.audioTrackName(context, audioStream));
        }

        player.getSelectedAudioStream()
                .ifPresent(s -> binding.audioTrackTextView.setText(
                        Localization.audioTrackName(context, s)));
        binding.audioTrackTextView.setVisibility(View.VISIBLE);
        audioTrackPopupMenu.setOnMenuItemClickListener(this);
        audioTrackPopupMenu.setOnDismissListener(this);
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return;
        }
        playbackSpeedPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_PLAYBACK_SPEED);

        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            playbackSpeedPopupMenu.getMenu().add(POPUP_MENU_ID_PLAYBACK_SPEED, i, Menu.NONE,
                    formatSpeed(PLAYBACK_SPEEDS[i]));
        }
        binding.playbackSpeed.setText(formatSpeed(player.getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private void buildCaptionMenu(@NonNull final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_CAPTION);

        captionPopupMenu.setOnDismissListener(this);

        // Add option for turning off caption
        final MenuItem captionOffItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            player.setCaptionPreference(null);
            return true;
        });

        // Add all availabl…16087 tokens truncated…sable="true"
                                android:padding="@dimen/player_main_icon_buttons_padding"
                                android:scaleType="fitCenter"
                                android:src="@drawable/ic_fullscreen"
                                android:visibility="gone"
                                app:tint="@color/white"
                                tools:ignore="RtlHardcoded"
                                tools:visibility="visible" />

                        </LinearLayout>
                    </HorizontalScrollView>

                    <org.schabi.newpipe.views.NewPipeTextView
                        android:id="@+id/sleepTimerCountdown"
                        android:layout_width="wrap_content"
                        android:layout_height="28dp"
                        android:layout_gravity="end"
                        android:background="#60000000"
                        android:gravity="center"
                        android:importantForAccessibility="no"
                        android:paddingStart="6dp"
                        android:paddingEnd="6dp"
                        android:textColor="@android:color/white"
                        android:textSize="@dimen/player_main_controls_text_size"
                        android:textStyle="bold"
                        android:visibility="gone"
                        tools:text="29:59"
                        tools:visibility="visible" />

                </LinearLayout>

                <LinearLayout
                    android:id="@+id/bottomSeekbarPreviewLayout"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_above="@id/bottomControls"
                    android:orientation="horizontal">

                    <LinearLayout
                        android:id="@+id/seekbarPreviewContainer"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:gravity="center"
                        android:orientation="vertical"
                        android:paddingBottom="12dp">

                        <org.schabi.newpipe.views.NewPipeTextView
                            android:id="@+id/currentDisplaySeek"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:background="#60000000"
                            android:paddingLeft="5dp"
                            android:paddingRight="5dp"
                            android:paddingBottom="2dp"
                            android:textColor="@android:color/white"
                            android:textSize="18sp"
                            android:textStyle="bold"
                            android:visibility="gone"
                            tools:ignore="RtlHardcoded"
                            tools:text="1:06:29"
                            tools:visibility="visible" />

                        <ImageView
                            android:id="@+id/currentSeekbarPreviewThumbnail"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:paddingTop="2dp"
                            android:src="@drawable/placeholder_thumbnail_video"
                            android:visibility="gone"
                            tools:visibility="visible" />

                    </LinearLayout>

                </LinearLayout>

                <LinearLayout
                    android:id="@+id/bottomControls"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_alignParentBottom="true"
                    android:gravity="center"
                    android:minHeight="40dp"
                    android:orientation="horizontal"
                    android:paddingLeft="@dimen/player_main_controls_padding"
                    android:paddingRight="@dimen/player_main_controls_padding">

                    <org.schabi.newpipe.views.NewPipeTextView
                        android:id="@+id/playbackCurrentTime"
                        android:layout_width="wrap_content"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:minHeight="30dp"
                        android:text="-:--:--"
                        android:textColor="@android:color/white"
                        android:textSize="@dimen/player_main_controls_text_size"
                        tools:ignore="HardcodedText"
                        tools:text="1:06:29" />


                    <FrameLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_gravity="center"
                        android:layout_marginTop="2dp"
                        android:layout_weight="1">

                        <org.schabi.newpipe.views.WavySeekBar
                            android:id="@+id/playbackSeekBar"
                            style="@style/Widget.WizeStream.SeekBar"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:nextFocusDown="@id/screenRotationButton"
                            tools:progress="25"
                            tools:secondaryProgress="50" />

                        <org.schabi.newpipe.views.SponsorBlockSeekBarMarkersView
                            android:id="@+id/sponsorBlockSeekBarMarkers"
                            android:layout_width="match_parent"
                            android:layout_height="@dimen/sponsor_block_marker_height"
                            android:layout_gravity="center_vertical"
                            android:clickable="false"
                            android:focusable="false"
                            android:importantForAccessibility="no"
                            android:visibility="gone"
                            tools:visibility="visible" />

                    </FrameLayout>

                    <org.schabi.newpipe.views.NewPipeTextView
                        android:id="@+id/playbackEndTime"
                        android:layout_width="wrap_content"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:text="-:--:--"
                        android:textColor="@android:color/white"
                        android:textSize="@dimen/player_main_controls_text_size"
                        tools:ignore="HardcodedText"
                        tools:text="1:23:49" />

                    <org.schabi.newpipe.views.NewPipeTextView
                        android:id="@+id/playbackLiveSync"
                        android:layout_width="wrap_content"
                        android:layout_height="match_parent"
                        android:background="?attr/selectableItemBackgroundBorderless"
                        android:gravity="center"
                        android:paddingLeft="4dp"
                        android:paddingRight="4dp"
                        android:text="@string/duration_live"
                        android:textAllCaps="true"
                        android:textColor="@android:color/white"
                        android:textSize="@dimen/player_main_controls_text_size"
                        android:visibility="gone"
                        tools:ignore="HardcodedText,RtlHardcoded,RtlSymmetry" />

                    <androidx.appcompat.widget.AppCompatImageButton
                        android:id="@+id/screenRotationButton"
                        android:layout_width="48dp"
                        android:layout_height="48dp"
                        android:layout_marginStart="4dp"
                        android:background="?attr/selectableItemBackgroundBorderless"
                        android:clickable="true"
                        android:contentDescription="@string/toggle_screen_orientation"
                        android:focusable="true"
                        android:nextFocusUp="@id/playbackSeekBar"
                        android:padding="@dimen/player_main_icon_buttons_padding"
                        android:scaleType="fitCenter"
                        android:src="@drawable/ic_fullscreen"
                        android:visibility="gone"
                        app:tint="@color/white"
                        tools:ignore="RtlHardcoded"
                        tools:visibility="visible" />
                </LinearLayout>
            </RelativeLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:orientation="horizontal"
                android:weightSum="5.5">

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/playPreviousButton"
                    android:layout_width="0dp"
                    android:layout_height="48dp"
                    android:layout_marginEnd="10dp"
                    android:layout_weight="1"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/previous_stream"
                    android:focusable="true"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_previous"
                    app:tint="@color/white" />


                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/playPauseButton"
                    android:layout_width="0dp"
                    android:focusable="true"
                    android:nextFocusUp="@id/commentsButton"
                    android:layout_height="60dp"
                    android:layout_weight="1"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/pause"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_pause"
                    app:tint="@color/white" />

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/playNextButton"
                    android:layout_width="0dp"
                    android:layout_height="48dp"
                    android:layout_marginStart="10dp"
                    android:layout_weight="1"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/next_stream"
                    android:focusable="true"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_next"
                    app:tint="@color/white" />

            </LinearLayout>

        </RelativeLayout>

        <RelativeLayout
            android:id="@+id/itemsListPanel"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@color/queue_background_color"
            android:visibility="gone"
            tools:visibility="visible">

            <RelativeLayout
                android:id="@+id/itemsListControl"
                android:layout_width="match_parent"
                android:layout_height="60dp">

                <androidx.appcompat.widget.AppCompatTextView
                    android:id="@+id/itemsListHeaderTitle"
                    style="@style/TextAppearance.Material3.BodyMedium"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_alignEnd="@id/itemsListClose"
                    android:layout_alignParentStart="true"
                    android:layout_centerVertical="true"
                    android:layout_marginStart="16dp"
                    android:layout_marginEnd="56dp"
                    android:ellipsize="end"
                    android:maxLines="2"
                    android:text="@string/chapters"
                    android:textColor="@android:color/white"
                    android:visibility="gone" />

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/repeatButton"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_alignParentStart="true"
                    android:layout_alignParentLeft="true"
                    android:layout_centerVertical="true"
                    android:layout_marginStart="40dp"
                    android:layout_marginLeft="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/notification_action_repeat"
                    android:focusable="true"
                    android:padding="10dp"
                    android:scaleType="fitXY"
                    android:src="@drawable/exo_legacy_controls_repeat_off"
                    android:tint="?attr/colorAccent"
                    tools:ignore="RtlHardcoded" />

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/shuffleButton"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_centerVertical="true"
                    android:layout_toRightOf="@id/repeatButton"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/notification_action_shuffle"
                    android:focusable="true"
                    android:padding="10dp"
                    android:scaleType="fitXY"
                    android:src="@drawable/ic_shuffle"
                    android:tint="?attr/colorAccent"
                    tools:ignore="RtlHardcoded" />

                <androidx.appcompat.widget.AppCompatTextView
                    android:id="@+id/itemsListHeaderDuration"
                    style="@style/TextAppearance.Material3.BodyMedium"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerVertical="true"
                    android:layout_toStartOf="@id/addToPlaylistButton"
                    android:layout_toEndOf="@id/shuffleButton"
                    android:gravity="center"
                    android:textColor="@android:color/white" />

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/addToPlaylistButton"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_centerVertical="true"
                    android:layout_toLeftOf="@+id/itemsListClose"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/add_to_playlist"
                    android:focusable="true"
                    android:padding="10dp"
                    android:scaleType="fitXY"
                    android:src="@drawable/ic_playlist_add"
                    android:tint="?attr/colorAccent"
                    tools:ignore="RtlHardcoded" />

                <androidx.appcompat.widget.AppCompatImageButton
                    android:id="@+id/itemsListClose"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_alignParentEnd="true"
                    android:layout_centerVertical="true"
                    android:layout_marginEnd="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:clickable="true"
                    android:contentDescription="@string/close"
                    android:focusable="true"
                    android:padding="10dp"
                    android:scaleType="fitXY"
                    android:src="@drawable/ic_close"
                    app:tint="@color/white" />

            </RelativeLayout>

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/itemsList"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:layout_below="@id/itemsListControl"
                android:scrollbars="vertical"
                android:theme="@style/PlayQueueItemTextTheme"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                tools:listitem="@layout/play_queue_item" />

        </RelativeLayout>

        <RelativeLayout
            android:id="@+id/player_overlays"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <RelativeLayout
                android:id="@+id/loading_panel"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@android:color/black"
                tools:visibility="gone">

                <ProgressBar
                    android:id="@+id/progressBarLoadingPanel"
                    style="?android:attr/progressBarStyleLargeInverse"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:indeterminate="true" />
            </RelativeLayout>

            <RelativeLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:layout_gravity="center"
                tools:ignore="RtlHardcoded">

                <RelativeLayout
                    android:id="@+id/volumeRelativeLayout"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:background="@drawable/background_oval_black_transparent"
                    android:visibility="gone"
                    tools:visibility="visible">

                    <ProgressBar
                        android:id="@+id/volumeProgressBar"
                        style="?android:progressBarStyleHorizontal"
                        android:layout_width="128dp"
                        android:layout_height="128dp"
                        android:indeterminate="false"
                        android:progressDrawable="@drawable/progress_circular_white" />

                    <androidx.appcompat.widget.AppCompatImageView
                        android:id="@+id/volumeImageView"
                        android:layout_width="70dp"
                        android:layout_height="70dp"
                        android:layout_centerInParent="true"
                        app:tint="@color/white"
                        tools:ignore="ContentDescription"
                        tools:src="@drawable/ic_volume_up" />
                </RelativeLayout>

                <RelativeLayout
                    android:id="@+id/brightnessRelativeLayout"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:background="@drawable/background_oval_black_transparent"
                    android:visibility="gone"
                    tools:visibility="visible">

                    <ProgressBar
                        android:id="@+id/brightnessProgressBar"
                        style="?android:progressBarStyleHorizontal"
                        android:layout_width="128dp"
                        android:layout_height="128dp"
                        android:indeterminate="false"
                        android:progressDrawable="@drawable/progress_circular_white" />

                    <androidx.appcompat.widget.AppCompatImageView
                        android:id="@+id/brightnessImageView"
                        android:layout_width="70dp"
                        android:layout_height="70dp"
                        android:layout_centerInParent="true"
                        app:tint="@color/white"
                        tools:ignore="ContentDescription"
                        tools:src="@drawable/ic_brightness_high" />
                </RelativeLayout>

                <org.schabi.newpipe.views.NewPipeTextView
                    android:id="@+id/swipeSeekDisplay"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:background="@drawable/player_seek_gesture_background"
                    android:paddingStart="@dimen/player_seek_gesture_horizontal_padding"
                    android:paddingTop="@dimen/player_seek_gesture_vertical_padding"
                    android:paddingEnd="@dimen/player_seek_gesture_horizontal_padding"
                    android:paddingBottom="@dimen/player_seek_gesture_vertical_padding"
                    android:textColor="@android:color/white"
                    android:textSize="@dimen/player_seek_gesture_text_size"
                    android:textStyle="bold"
                    android:visibility="gone"
                    tools:text="+00:15 (03:12)"
                    tools:visibility="visible" />

                <org.schabi.newpipe.views.NewPipeTextView
                    android:id="@+id/speedGestureDisplay"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:accessibilityLiveRegion="polite"
                    android:background="@drawable/background_oval_black_transparent"
                    android:paddingStart="18dp"
                    android:paddingTop="12dp"
                    android:paddingEnd="18dp"
                    android:paddingBottom="12dp"
                    android:textColor="@android:color/white"
                    android:textSize="24sp"
                    android:textStyle="bold"
                    android:visibility="gone"
                    tools:text="1.25×"
                    tools:visibility="visible" />

            </RelativeLayout>
            <org.schabi.newpipe.views.NewPipeTextView
                android:id="@+id/sponsorBlockSkipButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentBottom="true"
                android:layout_alignParentEnd="true"
                android:layout_marginEnd="24dp"
                android:layout_marginBottom="96dp"
                android:background="@drawable/sponsor_block_skip_button_background"
                android:clickable="true"
                android:contentDescription="@string/sponsor_block_skip_button_content_description"
                android:focusable="true"
                android:minHeight="40dp"
                android:paddingStart="18dp"
                android:paddingTop="10dp"
                android:paddingEnd="18dp"
                android:paddingBottom="10dp"
                android:text="@string/sponsor_block_skip_segment"
                android:textAllCaps="false"
                android:textColor="@android:color/white"
                android:textStyle="bold"
                android:visibility="gone"
                tools:visibility="visible" />


        </RelativeLayout>

        <View
            android:id="@+id/closingOverlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="#AAFF0000"
            android:visibility="gone" />

        <Button
            android:id="@+id/closeButton"
            style="@style/Widget.AppCompat.Button.Borderless"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_centerInParent="true"
            android:layout_marginTop="10dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true"
            android:text="@string/close"
            android:textAllCaps="true"
            android:textColor="@color/white"
            android:visibility="gone" />

        <org.schabi.newpipe.views.player.PlayerFastSeekOverlay
            android:id="@+id/fast_seek_overlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:alpha="0"
            android:visibility="invisible" /> <!-- Required for the first appearance fading correctly -->

        <org.schabi.newpipe.views.TouchLockOverlay
            android:id="@+id/touchLockOverlay"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:visibility="gone">
            <com.google.android.material.button.MaterialButton
                android:id="@+id/touchUnlockButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center_vertical|end"
                android:layout_margin="32dp"
                android:minHeight="48dp"
                android:text="@string/player_touch_unlock"
                app:icon="@drawable/ic_touch_lock" />
        </org.schabi.newpipe.views.TouchLockOverlay>
    </RelativeLayout>
</RelativeLayout>
