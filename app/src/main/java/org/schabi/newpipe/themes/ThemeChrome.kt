package org.schabi.newpipe.themes

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.graphics.ColorUtils
import org.schabi.newpipe.databinding.ActivityMainBinding
import org.schabi.newpipe.util.ThemeHelper

/** Bounded native backdrop sampling. Never captures the player surface or blurs text/controls. */
object ThemeChrome {
    fun requiresSoftwareImages(context: Context): Boolean {
        val pack = ThemePacks.active(context) ?: return false
        return pack.glass != null && pack.skin == null &&
            !(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    @JvmStatic
    fun apply(binding: ActivityMainBinding) {
        val pack = ThemePacks.active(binding.root.context) ?: return
        if (pack.glass == null && pack.skin == null) return
        val source = binding.fragmentHolder
        fun install(target: View) {
            target.background = GlassBackdrop(target, source, pack, binding.fragmentPlayerHolder)
        }
        install(binding.toolbarLayout.root)
        binding.toolbarLayout.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        install(binding.mainBottomNavigation)
        install(binding.mainNavigationRail)
        install(binding.drawerLayout.navigation)
    }
}

internal class GlassBackdrop(
    private val target: View,
    private val source: View,
    private val pack: ThemePack,
    private val player: View? = null
) : Drawable(), View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val surface = ThemeHelper.resolveColorFromAttr(target.context, com.google.android.material.R.attr.colorSurface)
    private val border = ThemeHelper.resolveColorFromAttr(target.context, com.google.android.material.R.attr.colorOnSurface)
    private val lowRam = (target.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    private var bitmap: Bitmap? = null
    private var lastCapture = 0L
    private var observer: ViewTreeObserver? = null
    private val rect = RectF()

    init {
        target.addOnAttachStateChangeListener(this)
        if (target.isAttachedToWindow) onViewAttachedToWindow(target)
    }

    override fun onViewAttachedToWindow(v: View) {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = target.viewTreeObserver.also { it.addOnPreDrawListener(this) }
    }

    override fun onViewDetachedFromWindow(v: View) {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        bitmap = null
    }

    override fun onPreDraw(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (lowRam || !target.isShown || target.width <= 0 || target.height <= 0 || now - lastCapture < 125) return true
        lastCapture = now
        // Keep opaque while a video/player overlay occupies the foreground. SurfaceView is not
        // part of software view snapshots; showing stale page content behind it would be misleading.
        if (player?.isShown == true && player.getGlobalVisibleRect(android.graphics.Rect())) {
            if (bitmap != null) {
                bitmap = null
                invalidateSelf()
            }
            return true
        }
        val scale = 96f / maxOf(target.width, target.height)
        val width = maxOf(1, (target.width * scale).toInt())
        val height = maxOf(1, (target.height * scale).toInt())
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        paint.alpha = 255
        canvas.drawColor(surface)
        val skin = pack.skin?.let { pack.assets.getValue(it).bitmap }
        if (skin != null) {
            canvas.drawBitmap(skin, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        } else if (source.width > 0 && source.height > 0 && source.isShown) {
            val sourceLocation = IntArray(2).also(source::getLocationInWindow)
            val targetLocation = IntArray(2).also(target::getLocationInWindow)
            // Existing safe-area insets remain intact. Bars sample the nearest content edge;
            // the drawer samples content at its real position without changing touch geometry.
            val x = (targetLocation[0] - sourceLocation[0]).coerceIn(0, maxOf(0, source.width - target.width))
            val y = (targetLocation[1] - sourceLocation[1]).coerceIn(0, maxOf(0, source.height - target.height))
            canvas.scale(scale, scale)
            canvas.translate(-x.toFloat(), -y.toFloat())
            try {
                source.draw(canvas)
            } catch (_: RuntimeException) {
                // A restored/in-flight image or another native view may still require a
                // hardware canvas. Never crash the page or retain a stale partial snapshot.
                frame.recycle()
                bitmap = null
                invalidateSelf()
                return true
            }
        }
        blur(frame, pack.glass?.blur ?: 0)
        bitmap = frame
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = (pack.glass?.radius ?: 0) * target.resources.displayMetrics.density
        val save = canvas.save()
        val path = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawRect(rect, paint)
        bitmap?.let {
            canvas.drawBitmap(it, null, rect, paint)
            paint.color = ColorUtils.setAlphaComponent(surface, (pack.glass?.tint ?: 80) * 255 / 100)
            canvas.drawRect(rect, paint)
        }
        canvas.restoreToCount(save)
        if (pack.glass != null && bitmap != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = target.resources.displayMetrics.density
            paint.color = ColorUtils.setAlphaComponent(border, 40)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Separable box blur on at most 96 x 96 pixels; no GPU/RenderEffect API dependency. */
        internal fun blur(bitmap: Bitmap, radius: Int) {
            if (radius == 0) return
            val w = bitmap.width
            val h = bitmap.height
            var pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            for (horizontal in listOf(true, false)) {
                val result = IntArray(pixels.size)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        var r = 0
                        var g = 0
                        var b = 0
                        for (offset in -radius..radius) {
                            val color = pixels[if (horizontal) y * w + (x + offset).coerceIn(0, w - 1) else (y + offset).coerceIn(0, h - 1) * w + x]
                            r += color shr 16 and 255
                            g += color shr 8 and 255
                            b += color and 255
                        }
                        val count = radius * 2 + 1
                        result[y * w + x] = android.graphics.Color.rgb(r / count, g / count, b / count)
                    }
                }
                pixels = result
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
