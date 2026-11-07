package org.schabi.newpipe.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.lang.ref.WeakReference
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player

/** Event-driven controls. No saved listening metadata, background worker, or network access. */
class PlaybackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, activePlayer.get())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        val player = activePlayer.get()
        if (player != null && !player.exoPlayerIsNull()) {
            when (action) {
                PLAY -> player.playPause()
                PREVIOUS -> player.playPrevious()
                NEXT -> player.playNext()
            }
        }
        update(context, player)
    }

    companion object {
        private const val PLAY = "org.schabi.newpipe.widget.PLAY"
        private const val PREVIOUS = "org.schabi.newpipe.widget.PREVIOUS"
        private const val NEXT = "org.schabi.newpipe.widget.NEXT"
        private val ACTIONS = setOf(PLAY, PREVIOUS, NEXT)
        private var activePlayer = WeakReference<Player>(null)

        fun update(context: Context, player: Player?) {
            activePlayer = WeakReference(player)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidget::class.java))
            if (ids.isEmpty()) return
            val available = player != null && !player.exoPlayerIsNull()
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.playback_widget)
            // Keep content titles off the launcher, including while Incognito changes mid-track.
            views.setTextViewText(R.id.widget_status, context.getString(if (available) R.string.playback_widget_active else R.string.playback_widget_idle))
            views.setOnClickPendingIntent(R.id.widget_status, open)
            views.setImageViewResource(R.id.widget_play, if (available && player?.playWhenReady == true) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            views.setContentDescription(R.id.widget_play, context.getString(if (available && player?.playWhenReady == true) R.string.pause else R.string.play))
            listOf(R.id.widget_previous to PREVIOUS, R.id.widget_play to PLAY, R.id.widget_next to NEXT).forEach { (id, action) ->
                val pending = if (available) {
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, PlaybackWidget::class.java).setAction(action),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    open
                }
                views.setOnClickPendingIntent(id, pending)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
