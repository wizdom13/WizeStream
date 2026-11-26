/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.Optional
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.PlaybackPresentationMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.event.PlayerServiceEventListener
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.helper.PlayerRotationMode
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy
import org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerUi
import org.schabi.newpipe.player.ui.PlayerUiList
import org.schabi.newpipe.player.ui.VideoPlayerUi
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.views.PlaybackDimmingLayout

/** Exercise the same forwarding listener and size/playback callbacks used by the service. */
class FullscreenCallbackIntegrationTest {
    private val staticMocks = mutableListOf<AutoCloseable>()
    private lateinit var fragment: VideoDetailFragment
    private lateinit var ui: MainPlayerUi
    private lateinit var player: Player
    private lateinit var activity: AppCompatActivity
    private lateinit var configuration: Configuration
    private lateinit var preferences: SharedPreferences
    private lateinit var holder: PlayerHolder
    private lateinit var forwarding: PlayerServiceEventListener
    private lateinit var bottomSheet: BottomSheetBehavior<FrameLayout>
    private var tablet = false
    private var requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Before
    fun setUp() {
        staticMocks += mockStatic(Log::class.java)
        staticMocks += mockStatic(AppCompatResources::class.java)
        staticMocks += mockStatic(OneShotPreDrawListener::class.java)
        staticMocks += mockStatic(ViewCompat::class.java)
        // Rendering is outside this regression; all fullscreen and listener methods stay real.
        staticMocks += mockStatic(Class.forName("org.schabi.newpipe.ktx.ViewUtils"))
        val devices = mockStatic(DeviceUtils::class.java).also { staticMocks += it }
        val helper = mockStatic(PlayerHelper::class.java).also { staticMocks += it }
        val rotation = mockStatic(PlayerRotationMode::class.java).also { staticMocks += it }
        val preferenceManager = mockStatic(PreferenceManager::class.java).also { staticMocks += it }

        fragment = mock(VideoDetailFragment::class.java, CALLS_REAL_METHODS)
        player = mock(Player::class.java)
        val realUiMethods = setOf(
            "isFullscreen",
            "getVideoContentOrientation",
            "setFullscreen",
            "toggleFullscreenWithOrientation",
            "checkLandscape",
            "onPlaying",
            "onVideoSizeChanged"
        )
        ui = mock(MainPlayerUi::class.java) { invocation ->
            if (invocation.method.name in realUiMethods) invocation.callRealMethod() else RETURNS_DEFAULTS.answer(invocation)
        }
        activity = mock(AppCompatActivity::class.java)
        configuration = mock(Configuration::class.java).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 400
        }
        val resources = mock(Resources::class.java)
        `when`(resources.configuration).thenReturn(configuration)
        `when`(activity.resources).thenReturn(resources)
        `when`(activity.requestedOrientation).thenAnswer { requestedOrientation }
        doAnswer {
            requestedOrientation = it.getArgument(0)
            null
        }.`when`(activity).setRequestedOrientation(org.mockito.ArgumentMatchers.anyInt())
        doReturn(resources).`when`(fragment).resources
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(true).`when`(fragment).isAdded
        doReturn(true).`when`(fragment).isResumed
        doNothing().`when`(fragment).onFullscreenStateChanged(anyBoolean())
        doNothing().`when`(fragment).refreshFullscreenLayout(anyBoolean())
        devices.`when`<Boolean> { DeviceUtils.isTablet(activity) }.thenAnswer { tablet }
        devices.`when`<Boolean> { DeviceUtils.isLandscape(activity) }
            .thenAnswer { configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        helper.`when`<Boolean> { PlayerHelper.globalScreenOrientationLocked(activity) }.thenReturn(true)
        rotation.`when`<PlayerRotationMode> { PlayerRotationMode.get(activity) }.thenReturn(PlayerRotationMode.FIXED)
        preferences = mock(SharedPreferences::class.java)
        `when`(preferences.getString("fullscreen_orientation_preference", "auto")).thenReturn("auto")
        preferenceManager.`when`<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(activity) }.thenReturn(preferences)

        val root = mock(FrameLayout::class.java)
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.`when`(root).post(any(Runnable::class.java))
        val binding = mock(FragmentVideoDetailBinding::class.java)
        `when`(binding.root).thenReturn(root)
        listOf("overlayPlayPauseButton", "overlayVideo", "overlayThumbnail").forEach { mockField(FragmentVideoDetailBinding::class.java, binding, it) }
        @Suppress("UNCHECKED_CAST")
        val sheet = mock(BottomSheetBehavior::class.java) as BottomSheetBehavior<FrameLayout>
        bottomSheet = sheet
        `when`(bottomSheet.state).thenReturn(BottomSheetBehavior.STATE_EXPANDED)
        setField(BaseFragment::class.java, fragment, "activity", activity)
        setField(VideoDetailFragment::class.java, fragment, "binding", binding)
        setField(VideoDetailFragment::class.java, fragment, "player", player)
        setField(VideoDetailFragment::class.java, fragment, "bottomSheetBehavior", bottomSheet)
        setField(VideoDetailFragment::class.java, fragment, "bottomSheetState", BottomSheetBehavior.STATE_EXPANDED)
        setField(VideoDetailFragment::class.java, fragment, "lastStableBottomSheetState", BottomSheetBehavior.STATE_EXPANDED)
        setField(VideoDetailFragment::class.java, fragment, "pendingFullscreenState", FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE)
        mockField(VideoDetailFragment::class.java, fragment, "minimizedVideoSurface")

        val playerRoot = mock(PlaybackDimmingLayout::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(parent.context).thenReturn(activity)
        `when`(playerRoot.parent).thenReturn(parent)
        doReturn(Optional.of(playerRoot)).`when`(fragment).getRoot()
        val playerBinding = mock(PlayerBinding::class.java)
        `when`(playerBinding.root).thenReturn(playerRoot)
        listOf(
            "surfaceView", "screenRotationButton", "videoSurfaceContainer", "playbackSeekBar",
            "loadingPanel", "currentDisplaySeek", "playPauseButton", "touchLockButton", "touchLockOverlay",
            "commentsButton", "playbackControlRoot", "metadataView", "playerCloseButton"
        ).forEach { mockField(PlayerBinding::class.java, playerBinding, it) }
        setField(PlayerUi::class.java, ui, "player", player)
        setField(PlayerUi::class.java, ui, "context", activity)
        setField(VideoPlayerUi::class.java, ui, "binding", playerBinding)
        listOf("qualityFeedback", "replayHeatmapController", "danmakuController", "chapterController", "playerUiTheme")
            .forEach { mockField(VideoPlayerUi::class.java, ui, it) }
        mockField(MainPlayerUi::class.java, ui, "ambientMode")
        setField(MainPlayerUi::class.java, ui, "videoContentOrientation", UNKNOWN)
        setField(MainPlayerUi::class.java, ui, "videoOrientationStreamKey", "0:https://example.com/one")
        `when`(ui.isAutomaticFullscreenAllowed(activity)).thenAnswer { !tablet }
        `when`(ui.parentActivity).thenReturn(Optional.empty())
        `when`(player.UIs()).thenReturn(PlayerUiList(ui))
        `when`(player.videoPlayerSelected()).thenReturn(true)
        `when`(player.playbackPresentationMode).thenReturn(PlaybackPresentationMode.VIDEO)
        val initialItem = item("one")
        `when`(player.currentItem).thenReturn(initialItem)

        // Use the actual holder, so default interface methods cannot hide omitted forwarding.
        val service = mock(PlayerService::class.java)
        setField(VideoDetailFragment::class.java, fragment, "playerService", service)
        `when`(service.player).thenReturn(player)
        doNothing().`when`(fragment).onServiceConnected(service)
        holder = PlayerHolder::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        setField(PlayerHolder::class.java, holder, "playerService", service)
        holder.setListener(fragment)
        forwarding = getField(PlayerHolder::class.java, holder, "internalListener") as PlayerServiceEventListener
        verify(player).setFragmentListener(forwarding)
        `when`(player.fragmentListener).thenReturn(Optional.of(forwarding))
    }

    @After
    fun tearDown() {
        staticMocks.asReversed().forEach { it.close() }
    }

    @Test
    fun latePortraitDimensionsReplaceThePendingLandscapeFallback() {
        ui.toggleFullscreenWithOrientation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requestedOrientation)
        assertFalse(ui.isFullscreen)

        ui.onVideoSizeChanged(VideoSize(720, 1280))

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)
        assertTrue(ui.isFullscreen)
        assertNoPendingTransition()
        verify(fragment).onVideoContentOrientationChanged()
    }

    @Test
    fun lateSquareDimensionsCorrectAnAlreadyAppliedLandscapeFallback() {
        ui.toggleFullscreenWithOrientation()
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertTrue(ui.isFullscreen)

        ui.onVideoSizeChanged(VideoSize(1080, 1080))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)
        rotate(Configuration.ORIENTATION_PORTRAIT)

        assertTrue(ui.isFullscreen)
        assertNoPendingTransition()
    }

    @Test
    fun knownDimensionsStillHonorAnExplicitLandscapePreference() {
        `when`(preferences.getString("fullscreen_orientation_preference", "auto")).thenReturn("landscape")
        ui.toggleFullscreenWithOrientation()
        ui.onVideoSizeChanged(VideoSize(720, 1280))

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requestedOrientation)
        assertFalse(ui.isFullscreen)
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertTrue(ui.isFullscreen)
    }

    @Test
    fun explicitPortraitFullscreenSurvivesAttachmentAndForegroundSynchronization() {
        `when`(preferences.getString("fullscreen_orientation_preference", "auto")).thenReturn("portrait")
        ui.onVideoSizeChanged(VideoSize(1920, 1080))
        ui.toggleFullscreenWithOrientation()

        assertTrue(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)
        assertNoPendingTransition()

        // The fullscreen layout posts another attachment pass after the request is consumed.
        VideoDetailFragment::class.java.getDeclaredMethod("syncFullscreenWithOrientation", Optional::class.java)
            .apply { isAccessible = true }.invoke(fragment, Optional.of(ui))
        assertTrue(ui.isFullscreen)

        forwarding.onVideoPlaybackResumed()
        assertTrue(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requestedOrientation)

        assertTrue(fragment.onBackPressed())
        assertFalse(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
    }

    @Test
    fun manualExitSurvivesPlayingAndVideoSizeCallbacksThroughTheHolder() {
        ui.onVideoSizeChanged(VideoSize(1920, 1080))
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertTrue(ui.isFullscreen)
        ui.toggleFullscreenWithOrientation()
        assertFalse(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
        assertTrue(forwarding.shouldSuppressAutomaticFullscreenInLandscape())

        `when`(player.isPlaying).thenReturn(true)
        ui.onPlaying()
        ui.onVideoSizeChanged(VideoSize(1280, 720))
        assertFalse(ui.isFullscreen)

        rotate(Configuration.ORIENTATION_PORTRAIT)
        assertFalse(forwarding.shouldSuppressAutomaticFullscreenInLandscape())
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertTrue(ui.isFullscreen)
        verify(player, never()).pause()
        verify(player, never()).play()
    }

    @Test
    fun tabletManualFullscreenCompletesEvenWhenAutomaticFullscreenIsDisabled() {
        tablet = true
        ui.onVideoSizeChanged(VideoSize(1920, 1080))
        ui.toggleFullscreenWithOrientation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requestedOrientation)
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertTrue(ui.isFullscreen)
        assertNoPendingTransition()

        assertTrue(fragment.onBackPressed())
        assertFalse(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
        rotate(Configuration.ORIENTATION_PORTRAIT)
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        ui.onPlaying()
        assertFalse(ui.isFullscreen)
        verify(player, never()).pause()
    }

    @Test
    fun anUnreadyRequestSurvivesDetachmentAndCompletesAfterPlaybackAttachment() {
        ui.onVideoSizeChanged(VideoSize(720, 1280))
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        ui.toggleFullscreenWithOrientation()
        assertFalse(ui.isFullscreen)
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN, pendingState())

        holder.setListener(null)
        forwarding.onVideoContentOrientationChanged()
        playbackUpdate()
        assertFalse(ui.isFullscreen)
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN, pendingState())

        holder.setListener(fragment)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        playbackUpdate()
        assertTrue(ui.isFullscreen)
        assertNoPendingTransition()

        ui.toggleFullscreenWithOrientation()
        playbackUpdate()
        assertFalse(ui.isFullscreen)
    }

    @Test
    fun anAbsentUiRetainsTheRequestUntilTheAttachmentSynchronization() {
        `when`(player.UIs()).thenReturn(PlayerUiList())
        forwarding.onManualFullscreenButtonClicked(true)
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN, pendingState())
        assertFalse(ui.isFullscreen)

        `when`(player.UIs()).thenReturn(PlayerUiList(ui))
        // tryAddVideoPlayerView invokes this same synchronization after adding the rebuilt view.
        VideoDetailFragment::class.java.getDeclaredMethod("syncFullscreenWithOrientation", Optional::class.java)
            .apply { isAccessible = true }.invoke(fragment, Optional.of(ui))

        assertTrue(ui.isFullscreen)
        assertNoPendingTransition()
    }

    @Test
    fun lateDimensionsCannotReplaceAnExitWaitingForTheEngine() {
        ui.onVideoSizeChanged(VideoSize(1920, 1080))
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        `when`(player.exoPlayerIsNull()).thenReturn(true)
        ui.toggleFullscreenWithOrientation()
        assertTrue(ui.isFullscreen)
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN, pendingState())

        ui.onVideoSizeChanged(VideoSize(720, 1280))
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN, pendingState())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
        `when`(player.exoPlayerIsNull()).thenReturn(false)
        playbackUpdate()

        assertFalse(ui.isFullscreen)
        assertNoPendingTransition()
    }

    @Test
    fun aDifferentQueueItemCannotConsumeAStaleManualRequest() {
        ui.toggleFullscreenWithOrientation()
        `when`(player.currentItem).thenReturn(null)
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        assertFalse(ui.isFullscreen)
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN, pendingState())

        // Late dimensions must also retain the original identity through that empty interval.
        ui.onVideoSizeChanged(VideoSize(720, 1280))
        rotate(Configuration.ORIENTATION_PORTRAIT)
        assertFalse(ui.isFullscreen)
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN, pendingState())

        val replacementItem = item("two")
        `when`(player.currentItem).thenReturn(replacementItem)
        playbackUpdate()

        assertFalse(ui.isFullscreen)
        assertNoPendingTransition()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
        val originalItem = item("one")
        `when`(player.currentItem).thenReturn(originalItem)
        playbackUpdate()
        assertFalse(ui.isFullscreen)
    }

    @Test
    fun resumeReleasesAnUncompletedRotationAbandonedByTheAuthoritativeConfiguration() {
        ui.toggleFullscreenWithOrientation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requestedOrientation)

        forwarding.onVideoPlaybackResumed()

        assertFalse(ui.isFullscreen)
        assertNoPendingTransition()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
    }

    @Test
    fun collapsedResumeReleasesTheRotationOwnedByManualTabletFullscreen() {
        tablet = true
        ui.onVideoSizeChanged(VideoSize(1920, 1080))
        ui.toggleFullscreenWithOrientation()
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        `when`(bottomSheet.state).thenReturn(BottomSheetBehavior.STATE_COLLAPSED)

        forwarding.onVideoPlaybackResumed()

        assertFalse(ui.isFullscreen)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation)
        assertNoPendingTransition()
        verify(player, never()).pause()
        verify(player, never()).play()
    }

    @Test
    fun onlyTheCurrentHolderListenerReceivesOrientationAndSuppressionCallbacks() {
        holder.setListener(null)
        clearInvocations(fragment)
        forwarding.onVideoContentOrientationChanged()
        assertFalse(forwarding.shouldSuppressAutomaticFullscreenInLandscape())
        verify(fragment, never()).onVideoContentOrientationChanged()

        val replacement = mock(PlayerServiceExtendedEventListener::class.java)
        `when`(replacement.shouldSuppressAutomaticFullscreenInLandscape()).thenReturn(true)
        holder.setListener(replacement)
        forwarding.onVideoContentOrientationChanged()
        assertTrue(forwarding.shouldSuppressAutomaticFullscreenInLandscape())
        verify(replacement).onVideoContentOrientationChanged()
        verify(fragment, never()).onVideoContentOrientationChanged()
    }

    private fun playbackUpdate() {
        forwarding.onPlaybackUpdate(Player.STATE_BUFFERING, 0, false, PlaybackParameters.DEFAULT)
    }

    private fun rotate(orientation: Int) {
        configuration.orientation = orientation
        fragment.onConfigurationChanged(configuration)
    }

    private fun pendingState(): Int = getField(VideoDetailFragment::class.java, fragment, "pendingFullscreenState") as Int

    private fun assertNoPendingTransition() {
        assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE, pendingState())
    }

    private fun item(id: String): PlayQueueItem = mock(PlayQueueItem::class.java).also {
        `when`(it.serviceId).thenReturn(0)
        `when`(it.url).thenReturn("https://example.com/$id")
    }

    private fun mockField(type: Class<*>, target: Any, name: String) {
        val field = type.getDeclaredField(name).apply { isAccessible = true }
        field.set(target, mock(field.type))
    }

    private fun setField(type: Class<*>, target: Any, name: String, value: Any) {
        type.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun getField(type: Class<*>, target: Any, name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
