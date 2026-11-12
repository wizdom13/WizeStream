package org.schabi.newpipe.player.video

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.spherical.SphericalGLSurfaceView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.OptionalPlaybackFeatures
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playback.SurfaceHolderCallback
import org.schabi.newpipe.player.ui.AnchoredPlaybackDialog
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.player.ui.PlayerPopupMenu
import org.schabi.newpipe.player.ui.VideoPlayerUi

/** Owns exactly one Media3 output and its listeners, independently of the player Activity. */
class PlayerVideoSurface internal constructor(
    private val binding: PlayerBinding,
    private val state: SphericalPlaybackState,
    private val engineProvider: () -> ExoPlayer?,
    private val rendersVideo: () -> Boolean,
    private val popup: () -> Boolean,
    private val itemKey: () -> String,
    private val onTap: () -> Unit,
    private val keepRenderingPaused: () -> Boolean = { false }
) : AnalyticsListener, DefaultLifecycleObserver {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(binding.root.context)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == OptionalPlaybackFeatures.SPHERICAL_KEY) {
            dismissDialog()
            updateOutput()
        }
    }
    private val enabled: Boolean get() = preferences.getBoolean(OptionalPlaybackFeatures.SPHERICAL_KEY, false)
    private var closed = false
    private var engine: ExoPlayer? = null
    private var formatKey: String? = null
    private var formatProjection: ByteArray? = null
    private var flatCallback: SurfaceHolderCallback? = null
    private var spherical: SphericalGLSurfaceView? = null
    private var resumed = false
    private var screenOn = true
    private var owner: LifecycleOwner? = null
    private var dialog: AlertDialog? = null
    private val host = binding.videoSurfaceContainer
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            bindLifecycle()
            setup()
        }
        override fun onViewDetachedFromWindow(view: View) {
            dismissDialog()
            pause()
            owner?.lifecycle?.removeObserver(this@PlayerVideoSurface)
            owner = null
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        binding.projectionButton.visibility = View.GONE
        host.addOnAttachStateChangeListener(attachListener)
        host.onPresentationChanged = ::updateVisibility
        host.onBeforeDetach = ::pause
        binding.projectionButton.setOnClickListener { showOptions() }
        bindLifecycle()
    }

    private fun bindLifecycle() {
        val next = host.findViewTreeLifecycleOwner()
        if (owner === next) return
        owner?.lifecycle?.removeObserver(this)
        owner = next
        next?.lifecycle?.addObserver(this)
    }

    fun setup() {
        if (closed) return
        val next = engineProvider() ?: return
        if (binding.root.parent == null) return
        if (engine !== next) {
            clear()
            engine = next
            state.selectItem(itemKey())
            next.addAnalyticsListener(this)
            formatKey = currentFormatKey()
            formatProjection = next.videoFormat?.projectionData
        }
        updateOutput()
    }

    fun selectItem(key: String, hint: Boolean) {
        state.selectItem(key)
        state.setExtractorHint(hint)
        updateOutput()
    }

    fun onMediaItemTransition() {
        state.selectItem(itemKey())
        updateOutput()
    }

    fun presentationChanged() {
        setup()
        updateVisibility()
    }

    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        val active = engine ?: return
        if (eventTime.mediaPeriodId != null && eventTime.windowIndex != active.currentMediaItemIndex) return
        formatKey = currentFormatKey()
        formatProjection = format.projectionData
        updateOutput()
    }

    fun setMode(mode: SphericalPlaybackState.Mode) {
        if (!enabled) return
        state.mode = mode
        updateOutput()
    }

    fun isSpherical(): Boolean = spherical != null
    fun activeVideoView(): SurfaceView = spherical ?: binding.surfaceView

    private fun currentFormatKey(): String = MediaItemTag.from(engine?.currentMediaItem)
        .map { "${it.serviceId}:${it.streamUrl}" }.orElse(itemKey())

    private fun updateOutput() {
        if (closed) return
        state.setContainerHint(if (formatKey == state.currentItemKey()) formatProjection else null)
        val eligible = rendersVideo()
        if (!enabled) {
            state.mode = SphericalPlaybackState.Mode.AUTO
            state.useSensorRotation = false
        }
        binding.projectionButton.visibility = if (eligible && enabled) View.VISIBLE else View.GONE
        val useSphere = eligible && enabled && state.isSpherical()
        binding.projectionButton.isActivated = useSphere
        binding.resizeTextView.isEnabled = !useSphere
        if (engine == null) return
        if (!eligible) {
            clearOutput()
            binding.surfaceView.visibility = View.GONE
            return
        }
        if (useSphere && spherical == null) {
            clearOutput()
            binding.surfaceView.visibility = View.GONE
            val view = SphericalGLSurfaceView(host.context)
            spherical = view
            view.setUseSensorRotation(false)
            view.setOnClickListener { onTap() }
            host.addView(view, FrameLayout.LayoutParams(-1, -1))
            engine?.setVideoSurfaceView(view)
        } else if (!useSphere && (spherical != null || flatCallback == null)) {
            clearOutput()
            binding.surfaceView.visibility = View.VISIBLE
            flatCallback = SurfaceHolderCallback(host.context, engine).also {
                binding.surfaceView.holder.addCallback(it)
                it.rebindVideoSurfaceIfValid(binding.surfaceView.holder)
            }
        }
        updateVisibility()
    }

    private fun updateVisibility() {
        val view = spherical ?: return
        // Other presentation helpers still update the flat view. Keep it out of the GL output.
        if (binding.surfaceView.visibility != View.GONE) binding.surfaceView.visibility = View.GONE
        val requiredState = if (keepRenderingPaused()) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
        val started = owner?.lifecycle?.currentState?.isAtLeast(requiredState) != false
        val visible = host.isAttachedToWindow && host.isShown && host.windowVisibility == View.VISIBLE &&
            screenOn && started && rendersVideo()
        if (visible && !resumed) {
            view.onResume()
            engine?.setVideoSurfaceView(view)
            resumed = true
        } else if (!visible) {
            pause()
        }
        val foreground = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false
        view.setUseSensorRotation(visible && foreground && state.useSensorRotation && !host.miniPlayerMode && !popup())
    }

    private fun pause() {
        spherical?.let { view ->
            view.setUseSensorRotation(false)
            // Disconnect the producer before the GL consumer/context stops or is reparented.
            engine?.clearVideoSurfaceView(view)
            view.onPause()
        }
        resumed = false
    }

    override fun onStart(owner: LifecycleOwner) = updateVisibility()
    override fun onResume(owner: LifecycleOwner) = updateVisibility()
    override fun onPause(owner: LifecycleOwner) {
        // Pause EGL while the window is still valid, before legacy window/surface teardown.
        if (keepRenderingPaused()) spherical?.setUseSensorRotation(false) else pause()
    }
    override fun onStop(owner: LifecycleOwner) {
        dismissDialog()
        pause()
    }

    fun onBroadcast(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                screenOn = false
                pause()
            }

            Intent.ACTION_SCREEN_ON -> {
                screenOn = true
                updateVisibility()
            }
        }
    }

    /** Sphere drags replace flat-video gestures; buttons remain separate touch targets. */
    fun handleTouch(source: View, event: MotionEvent): Boolean {
        val view = spherical ?: return false
        if (popup() || !view.isShown || host.miniPlayerMode) return false
        source.parent?.requestDisallowInterceptTouchEvent(true)
        val from = IntArray(2)
        val to = IntArray(2)
        source.getLocationOnScreen(from)
        view.getLocationOnScreen(to)
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation((from[0] - to[0]).toFloat(), (from[1] - to[1]).toFloat())
        view.dispatchTouchEvent(copy)
        copy.recycle()
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            source.parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    /** DPAD pans while controls are hidden, leaving normal control/list navigation intact. */
    fun handleKey(keyCode: Int, controlsVisible: Boolean): Boolean {
        val view = spherical ?: return false
        if (controlsVisible || !view.isShown || host.miniPlayerMode) return false
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Pair(125f, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> Pair(-125f, 0f)
            KeyEvent.KEYCODE_DPAD_UP -> Pair(0f, 125f)
            KeyEvent.KEYCODE_DPAD_DOWN -> Pair(0f, -125f)
            else -> return false
        }
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(
                time,
                time + action,
                action,
                view.width / 2f + if (action == MotionEvent.ACTION_DOWN) 0f else delta.first,
                view.height / 2f + if (action == MotionEvent.ACTION_DOWN) 0f else delta.second,
                0
            )
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        return true
    }

    @JvmOverloads
    fun restore(retries: Int = 2) {
        if (closed || binding.root.parent == null) return
        setup()
        if (spherical != null) {
            engine?.setVideoSurfaceView(spherical)
            updateVisibility()
        } else if (flatCallback?.rebindVideoSurfaceIfValid(binding.surfaceView.holder) == false && retries > 0) {
            val expectedEngine = engine
            binding.surfaceView.postOnAnimation { if (engine === expectedEngine) restore(retries - 1) }
        }
    }

    fun recenter() {
        if (spherical == null) return
        clearOutput()
        updateOutput()
    }

    private fun showOptions() {
        if (!enabled) return
        dialog?.dismiss()
        val context = PlayerPopupMenu.dialogContext(binding.projectionButton)
        val labels = arrayOf(context.getString(R.string.player_projection_auto), context.getString(R.string.player_projection_flat), context.getString(R.string.player_projection_360))
        val sensors = context.getSystemService(SensorManager::class.java)
        val hasSensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null || sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.player_projection)
            .setSingleChoiceItems(labels, state.mode.ordinal) { modal, which ->
                setMode(SphericalPlaybackState.Mode.entries[which])
                modal.dismiss()
            }
            .setPositiveButton(if (state.useSensorRotation) R.string.player_projection_motion_off else R.string.player_projection_motion_on) { _, _ ->
                state.useSensorRotation = !state.useSensorRotation
                updateVisibility()
            }
            .setNeutralButton(R.string.player_projection_reset) { _, _ -> recenter() }
            .setNegativeButton(R.string.close, null)
            .create().also {
                AnchoredPlaybackDialog.show(binding.projectionButton, it)
                if (it.isShowing) {
                    it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = spherical != null && hasSensor && !popup()
                    it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = spherical != null
                }
            }
    }

    private fun clearOutput() {
        flatCallback?.let {
            binding.surfaceView.holder.removeCallback(it)
            it.release()
        }
        flatCallback = null
        spherical?.let {
            engine?.clearVideoSurfaceView(it)
            pause()
            it.setOnClickListener(null)
            host.removeView(it)
        }
        spherical = null
    }

    private fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    fun clear() {
        dismissDialog()
        engine?.removeAnalyticsListener(this)
        clearOutput()
        engine = null
        formatKey = null
        formatProjection = null
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        closed = true
        clear()
        owner?.lifecycle?.removeObserver(this)
        owner = null
        host.removeOnAttachStateChangeListener(attachListener)
        host.onPresentationChanged = null
        host.onBeforeDetach = null
        binding.projectionButton.setOnClickListener(null)
    }

    companion object {
        @JvmStatic
        fun forUi(ui: VideoPlayerUi): PlayerVideoSurface {
            val player = ui.player
            return PlayerVideoSurface(
                ui.binding,
                player.sphericalPlayback,
                { player.getExoPlayer() },
                { player.playbackPresentationMode.rendersVideo() && !player.isAudioOnly },
                { player.popupPlayerSelected() },
                { player.currentItem?.let { "${it.serviceId}:${it.url}" }.orEmpty() },
                { if (ui.isControlsVisible) ui.hideControls(150, 0) else ui.showControlsThenHide() },
                {
                    (ui as? MainPlayerUi)?.parentActivity?.map { activity ->
                        val multiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            (activity.isInPictureInPictureMode || activity.isInMultiWindowMode)
                        val visibleMiniature = ui.binding.videoSurfaceContainer.miniPlayerMode &&
                            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        multiWindow || visibleMiniature
                    }?.orElse(false) ?: false
                }
            )
        }
    }
}
