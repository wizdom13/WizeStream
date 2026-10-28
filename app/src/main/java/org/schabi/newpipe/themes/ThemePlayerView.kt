package org.schabi.newpipe.themes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import org.schabi.newpipe.util.ThemeHelper

/** Native decorative listening layouts; playback controls stay in the existing accessible layer. */
class ThemePlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var pack: ThemePack? = ThemePacks.active(context)
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val accent = ThemeHelper.resolveColorFromAttr(context, androidx.appcompat.R.attr.colorAccent)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = pack ?: return
        val style = theme.player
        canvas.drawColor(Color.rgb(12, 12, 16))
        style.background?.let { id ->
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(theme.assets.getValue(id).bitmap, null, rect, paint)
            canvas.drawColor(0x99000000.toInt())
        }
        val size = minOf(width.toFloat(), height.toFloat()) * 0.64f
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f
        paint.style = Paint.Style.FILL
        paint.color = ColorUtils.blendARGB(accent, Color.BLACK, 0.7f)
        when (style.layout) {
            "vinyl" -> {
                canvas.drawCircle(cx, cy, r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(1f, r / 180)
                paint.color = 0x55666666
                for (i in 4..14) canvas.drawCircle(cx, cy, r * i / 15, paint)
                paint.style = Paint.Style.FILL
                paint.color = accent
                canvas.drawCircle(cx, cy, r * 0.36f, paint)
                artwork(canvas, theme, cx, cy, r * 0.34f)
                paint.color = Color.BLACK
                canvas.drawCircle(cx, cy, r * 0.045f, paint)
            }

            "pocket" -> {
                rect.set(cx - r * 0.7f, cy - r, cx + r * 0.7f, cy + r)
                canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, paint)
                paint.color = 0xff171b20.toInt()
                rect.set(cx - r * 0.56f, cy - r * 0.85f, cx + r * 0.56f, cy - r * 0.05f)
                canvas.drawRoundRect(rect, r * 0.04f, r * 0.04f, paint)
                artwork(canvas, theme, cx, cy - r * 0.45f, r * 0.34f)
                paint.color = accent
                canvas.drawCircle(cx, cy + r * 0.46f, r * 0.39f, paint)
                paint.color = 0xff171b20.toInt()
                canvas.drawCircle(cx, cy + r * 0.46f, r * 0.15f, paint)
            }

            "cassette" -> {
                rect.set(cx - r, cy - r * 0.65f, cx + r, cy + r * 0.65f)
                canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, paint)
                for (x in listOf(cx - r * 0.48f, cx + r * 0.48f)) {
                    paint.color = accent
                    canvas.drawCircle(x, cy, r * 0.3f, paint)
                    artwork(canvas, theme, x, cy, r * 0.26f)
                    paint.color = Color.BLACK
                    canvas.drawCircle(x, cy, r * 0.1f, paint)
                }
            }
        }
    }

    private fun artwork(canvas: Canvas, theme: ThemePack, x: Float, y: Float, radius: Float) {
        val bitmap = theme.player.artwork?.let { theme.assets.getValue(it).bitmap } ?: return
        val save = canvas.save()
        canvas.clipPath(Path().apply { addCircle(x, y, radius, Path.Direction.CW) })
        rect.set(x - radius, y - radius, x + radius, y + radius)
        paint.color = Color.WHITE
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restoreToCount(save)
    }
    companion object {
        @JvmStatic
        fun updatePresentation(binding: org.schabi.newpipe.databinding.PlayerBinding, listening: Boolean) {
            val theme = ThemePacks.active(binding.root.context)
            val skinned = listening && theme != null &&
                (theme.player.layout != "standard" || theme.player.background != null)
            binding.themePlayer.pack = theme
            binding.themePlayer.visibility = if (skinned) VISIBLE else GONE
            binding.audioVisualizer.visibility = if (listening && !skinned) VISIBLE else GONE
        }
    }
}
