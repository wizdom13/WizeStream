package org.schabi.newpipe.learning

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.learning.model.VideoNoteStream
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** One reactive note index per attached adapter, shared by all of its video rows. */
class VideoNoteBadges @JvmOverloads constructor(
    context: Context,
    private val onChanged: Runnable,
    private val streams: Flowable<List<VideoNoteStream>> =
        NewPipeDatabase.getInstance(context.applicationContext).learningNoteDAO().streamsWithNotes()
) {
    private var subscription: Disposable? = null
    private var attachments = 0
    private var streamIds = emptyMap<Pair<Int, String>, Long>()

    private val windowListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = attach()

        override fun onViewDetachedFromWindow(view: View) = detach()
    }

    fun watch(view: View) {
        view.addOnAttachStateChangeListener(windowListener)
        if (view.isAttachedToWindow) attach()
    }

    fun unwatch(view: View) {
        view.removeOnAttachStateChangeListener(windowListener)
        if (view.isAttachedToWindow) detach()
    }

    fun attach() {
        attachments++
        if (attachments != 1) return
        var firstEmission = true
        subscription = streams.distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ rows ->
                val updated = rows.associate { (it.serviceId to it.url) to it.streamId }
                if (firstEmission || updated != streamIds) {
                    firstEmission = false
                    streamIds = updated
                    onChanged.run()
                }
            }, { error -> Log.w("VideoNoteBadges", "Unable to load note indicators", error) })
    }

    fun detach() {
        if (attachments == 0) return
        attachments--
        if (attachments != 0) return
        subscription?.dispose()
        subscription = null
        streamIds = emptyMap()
    }

    fun bindOnline(root: View, item: InfoItem) {
        if (item is StreamInfoItem) {
            bind(root, item.serviceId, item.url, item.name)
        } else {
            clear(root)
        }
    }

    fun bindLocal(root: View, item: LocalItem) {
        val stream = when (item) {
            is PlaylistStreamEntry -> item.streamEntity
            is StreamStatisticsEntry -> item.streamEntity
            else -> null
        }
        if (stream == null) {
            clear(root)
        } else {
            bindStream(root, stream)
        }
    }

    fun bindStream(root: View, stream: StreamEntity) {
        bind(root, stream.serviceId, stream.url, stream.title)
    }

    private fun bind(root: View, serviceId: Int, url: String, title: String) {
        val streamId = streamIds[serviceId to url]
        if (streamId == null) {
            clear(root)
            return
        }
        val container = root.findViewById<View>(R.id.itemThumbnailContainer) as? FrameLayout ?: return
        val badge = container.findViewById<ImageButton>(R.id.itemVideoNoteBadge) ?: createBadge(container)
        badge.visibility = View.VISIBLE
        // The icon is an indicator; a normal tap must not start playback or open an editor.
        badge.setOnClickListener { }
        badge.setOnLongClickListener {
            val manager = fragmentManager(root) ?: return@setOnLongClickListener false
            VideoNotesDialog.show(manager, streamId, title)
            true
        }
    }

    fun clear(root: View) {
        root.findViewById<ImageButton>(R.id.itemVideoNoteBadge)?.apply {
            visibility = View.GONE
            setOnClickListener(null)
            setOnLongClickListener(null)
        }
    }

    private fun createBadge(container: FrameLayout): ImageButton {
        val context = container.context
        val density = context.resources.displayMetrics.density
        val size = (48 * density).toInt()
        val padding = (12 * density).toInt()
        return ImageButton(context).apply {
            id = R.id.itemVideoNoteBadge
            contentDescription = context.getString(R.string.video_note_badge_description)
            setImageResource(R.drawable.ic_video_note)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            setBackgroundResource(R.drawable.video_note_badge_background)
            setPadding(padding, padding, padding, padding)
            isFocusable = true
            container.addView(
                this,
                FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END)
            )
        }
    }

    private fun fragmentManager(root: View): FragmentManager? {
        try {
            return FragmentManager.findFragment<Fragment>(root).parentFragmentManager
        } catch (_: IllegalStateException) {
            var context = root.context
            while (context is ContextWrapper) {
                if (context is FragmentActivity) return context.supportFragmentManager
                val base = context.baseContext
                if (base === context) break
                context = base
            }
            return null
        }
    }
}
