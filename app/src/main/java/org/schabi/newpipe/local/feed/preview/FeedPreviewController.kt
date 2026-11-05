package org.schabi.newpipe.local.feed.preview

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/** View-scoped, opt-in playback. No service, audio focus, history writes or automatic extraction. */
class FeedPreviewController(
    private val list: RecyclerView,
    private val open: (StreamEntity, SinglePlayQueue?) -> Unit,
    private val load: (StreamEntity) -> Single<MediaSource> = FeedPreviewSource(list.context.applicationContext)::load,
    private val otherPlayerOpen: () -> Boolean = { PlayerHolder.getInstance().player.isPresent }
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(list.context)
    private var session: Session? = null
    private var disposed = false
    private class Session(val row: View, val stream: StreamEntity, val container: FrameLayout, val overlay: View, val button: View) {
        val durationBadge: View? = row.findViewById(R.id.itemDurationView)
        val durationVisibility: Int? = durationBadge?.visibility
        var request: Disposable? = null
        var engine: ExoPlayer? = null
    }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == list.context.getString(R.string.feed_preview_key)) {
            stop()
            list.adapter?.notifyDataSetChanged()
        }
    }
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = checkVisible()
    }
    private val childListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) = Unit
        override fun onChildViewDetachedFromWindow(view: View) {
            if (session?.row === view) stop()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            checkVisible()
            if (session != null) list.postDelayed(this, 250)
        }
    }

    init {
        list.addOnScrollListener(scrollListener)
        list.addOnChildAttachStateChangeListener(childListener)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun bind(row: View, stream: StreamEntity, card: Boolean) {
        if (session?.row === row) stop()
        val container = row.findViewById<FrameLayout>(R.id.itemThumbnailContainer) ?: return
        container.findViewById<View>(R.id.feed_preview_button)?.let(container::removeView)
        if (disposed || !card || !enabled() || stream.requiresMembership || stream.streamType != StreamType.VIDEO_STREAM) return
        val button = LayoutInflater.from(row.context).inflate(R.layout.feed_preview_button, container, false)
        button.setOnClickListener { start(row, container, button, stream) }
        container.addView(button)
    }

    private fun enabled() = preferences.getBoolean(list.context.getString(R.string.feed_preview_key), false)

    private fun start(row: View, container: FrameLayout, button: View, stream: StreamEntity) {
        stop()
        if (disposed || !enabled() || !row.isAttachedToWindow) return
        if (otherPlayerOpen()) {
            Toast.makeText(list.context, R.string.feed_preview_player_busy, Toast.LENGTH_SHORT).show()
            return
        }
        val overlay = LayoutInflater.from(row.context).inflate(R.layout.feed_preview, container, false)
        val current = Session(row, stream, container, overlay, button)
        session = current
        button.visibility = View.GONE
        current.durationBadge?.visibility = View.GONE
        container.addView(overlay)
        val video = overlay.findViewById<PlayerView>(R.id.feed_preview_player)
        val actions = overlay.findViewById<View>(R.id.feed_preview_actions)
        video.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                actions.visibility = visibility
            }
        )
        // Tint only the preview backgrounds, keeping text and icons fully opaque.
        video.findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)
            ?.setBackgroundColor(ContextCompat.getColor(list.context, R.color.feed_preview_scrim))
        video.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)
            ?.setBackgroundColor(ContextCompat.getColor(list.context, R.color.feed_preview_panel))
        video.setShowNextButton(false)
        video.setShowPreviousButton(false)
        overlay.findViewById<Button>(R.id.feed_preview_stop).setOnClickListener { stop() }
        overlay.findViewById<Button>(R.id.feed_preview_open).setOnClickListener {
            val queue = takeQueue(stream)
            open(stream, queue)
        }
        overlay.findViewById<Button>(R.id.feed_preview_mute).setOnClickListener { view ->
            current.engine?.let { engine ->
                engine.volume = if (engine.volume == 0f) 1f else 0f
                (view as Button).setText(if (engine.volume == 0f) R.string.feed_preview_unmute else R.string.feed_preview_mute)
            }
        }
        list.post(tick)
        current.request = Single.defer { load(stream) }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ source ->
            if (session !== current) return@subscribe
            if (otherPlayerOpen() || !row.isShown) {
                stop()
                return@subscribe
            }
            try {
                val engine = ExoPlayer.Builder(list.context.applicationContext).setUsePlatformDiagnostics(false).build()
                current.engine = engine
                engine.volume = 0f
                engine.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (session === current) fail()
                    }
                })
                video.player = engine
                engine.setMediaSource(source)
                engine.prepare()
                engine.play()
            } catch (_: RuntimeException) {
                if (session === current) fail()
            }
        }, { if (session === current) fail() })
    }

    /** Capture before release; preview progress is never persisted to watch history. */
    fun takeQueue(stream: StreamEntity): SinglePlayQueue? {
        val current = session
        val queue = if (current?.stream?.serviceId == stream.serviceId && current.stream.url == stream.url) {
            current.engine?.let { engine ->
                SinglePlayQueue(stream.toStreamInfoItem()).apply { setRecovery(0, engine.currentPosition.coerceAtLeast(0)) }
            }
        } else {
            null
        }
        stop()
        return queue
    }

    private fun checkVisible() {
        val current = session ?: return
        val rowRect = Rect()
        val listRect = Rect()
        if (!enabled() || otherPlayerOpen() || !current.row.isShown || !current.container.getGlobalVisibleRect(rowRect) ||
            !list.getGlobalVisibleRect(listRect) || !Rect.intersects(rowRect, listRect)
        ) {
            stop()
        }
    }

    private fun fail() {
        stop()
        Toast.makeText(list.context, R.string.feed_preview_failed, Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        val previous = session ?: return
        session = null
        list.removeCallbacks(tick)
        previous.request?.dispose()
        previous.overlay.findViewById<PlayerView>(R.id.feed_preview_player).apply {
            setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
            player = null
        }
        previous.engine?.release()
        previous.container.removeView(previous.overlay)
        previous.durationVisibility?.let { previous.durationBadge?.visibility = it }
        previous.button.visibility = View.VISIBLE
    }

    fun dispose() {
        stop()
        disposed = true
        list.removeOnScrollListener(scrollListener)
        list.removeOnChildAttachStateChangeListener(childListener)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }
}
