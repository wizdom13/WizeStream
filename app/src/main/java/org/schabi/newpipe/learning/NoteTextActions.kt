package org.schabi.newpipe.learning

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.widget.TextView
import java.util.function.LongConsumer
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Links remain ordinary editable text; only an explicit long press opens or seeks them. */
object NoteTextActions {
    private val urls = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)
    private val timestamps = Regex("(?<![\\d:])(?:\\d+:)?\\d{1,2}:\\d{2}(?![\\d:])")

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun install(view: TextView, onSeek: LongConsumer?, fallback: Runnable?) {
        var touchedOffset = -1
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touchedOffset = view.getOffsetForPosition(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchedOffset = -1
            }
            false
        }
        view.setOnLongClickListener {
            val offset = if (touchedOffset >= 0) touchedOffset else view.selectionStart
            val text = view.text.toString()
            val url = urlAt(text, offset)
            val timestamp = timestampAt(text, offset)
            when {
                url != null -> ShareUtils.openUrlInBrowser(view.context, url)
                timestamp != null && onSeek != null -> onSeek.accept(timestamp)
                fallback != null -> fallback.run()
                else -> return@setOnLongClickListener false
            }
            true
        }
    }

    internal fun urlAt(text: String, offset: Int): String? = urls.findAll(text)
        .firstOrNull { offset in it.range }?.value?.trimEnd('.', ',', ';', ')', ']', '}')

    internal fun timestampAt(text: String, offset: Int): Long? {
        if (urlAt(text, offset) != null) return null
        return timestamps.findAll(text).firstOrNull { offset in it.range }
            ?.value?.let(LearningNoteTime::parse)
    }
}
