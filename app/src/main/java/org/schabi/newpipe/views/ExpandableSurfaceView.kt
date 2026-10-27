package org.schabi.newpipe.views

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM

class ExpandableSurfaceView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs) {
    private var miniPlayerMode = false
    private var resizeMode = RESIZE_MODE_FIT
    private var baseHeight = 0
    private var maxHeight = 0
    var videoAspectRatio = 0f
        private set
    private var baseScaleX = 1f
    private var baseScaleY = 1f
    var userZoomScale = MIN_USER_ZOOM
        private set
    var userTranslationX = 0f
        private set
    var userTranslationY = 0f
        private set

    init {
        // Keep the decoder surface alive during fullscreen, PiP and sheet transitions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setSurfaceLifecycle(SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
        }
    }

    fun setMiniPlayerMode(enabled: Boolean) {
        miniPlayerMode = enabled
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        baseScaleX = 1f
        baseScaleY = 1f
        if (miniPlayerMode) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            val ratio = videoAspectRatio.takeIf { it > 0 } ?: (16f / 9f)
            setMeasuredDimension(
                minOf(availableWidth, (availableHeight * ratio).toInt()),
                minOf(availableHeight, (availableWidth / ratio).toInt())
            )
            return
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (videoAspectRatio <= 0f) return

        var width = MeasureSpec.getSize(widthMeasureSpec)
        val preferredHeight = if (maxHeight > 0 && resizeMode != RESIZE_MODE_FIT && videoAspectRatio < 1) {
            maxHeight
        } else {
            baseHeight
        }
        // A cached fullscreen height can belong to the previous orientation. The current parent
        // bounds must win while the detail fragment catches up with the new window dimensions.
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        var height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            preferredHeight
        } else if (preferredHeight > 0) {
            minOf(preferredHeight, availableHeight)
        } else {
            availableHeight
        }
        if (width <= 0 || height <= 0) return

        val viewAspectRatio = width.toFloat() / height
        val aspectDeformation = videoAspectRatio / viewAspectRatio - 1
        if (resizeMode == RESIZE_MODE_FIT) {
            if (aspectDeformation > 0) {
                height = (width / videoAspectRatio).toInt()
            } else {
                width = (height * videoAspectRatio).toInt()
            }
        } else if (resizeMode == RESIZE_MODE_ZOOM) {
            if (aspectDeformation < 0) {
                baseScaleY = viewAspectRatio / videoAspectRatio
            } else {
                baseScaleX = videoAspectRatio / viewAspectRatio
            }
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        applyUserTransform()
    }

    private fun applyUserTransform() {
        if (miniPlayerMode) {
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
            return
        }
        val effectiveScaleX = baseScaleX * userZoomScale
        val effectiveScaleY = baseScaleY * userZoomScale
        pivotX = width / 2f
        pivotY = height / 2f
        scaleX = effectiveScaleX
        scaleY = effectiveScaleY
        val maxTranslationX = maxOf(0f, width * (effectiveScaleX - 1f) / 2f)
        val maxTranslationY = maxOf(0f, height * (effectiveScaleY - 1f) / 2f)
        userTranslationX = userTranslationX.coerceIn(-maxTranslationX, maxTranslationX)
        userTranslationY = userTranslationY.coerceIn(-maxTranslationY, maxTranslationY)
        translationX = userTranslationX
        translationY = userTranslationY
    }

    fun setHeights(base: Int, max: Int) {
        if (baseHeight == base && maxHeight == max) return
        baseHeight = base
        maxHeight = max
        requestLayout()
    }

    fun setResizeMode(newResizeMode: @AspectRatioFrameLayout.ResizeMode Int) {
        if (resizeMode == newResizeMode) return
        resizeMode = newResizeMode
        requestLayout()
    }

    fun getResizeMode(): @AspectRatioFrameLayout.ResizeMode Int = resizeMode

    fun setUserTransform(zoomScale: Float, translationX: Float, translationY: Float) {
        userZoomScale = zoomScale.coerceIn(MIN_USER_ZOOM, MAX_USER_ZOOM)
        userTranslationX = if (userZoomScale == MIN_USER_ZOOM) 0f else translationX
        userTranslationY = if (userZoomScale == MIN_USER_ZOOM) 0f else translationY
        applyUserTransform()
    }

    fun resetUserTransform() = setUserTransform(MIN_USER_ZOOM, 0f, 0f)

    /** Clear the previous item's geometry until Media3 provides the next video's dimensions. */
    fun clearAspectRatio() {
        videoAspectRatio = 0f
        baseScaleX = 1f
        baseScaleY = 1f
        resetUserTransform()
        requestLayout()
    }

    fun setAspectRatio(aspectRatio: Float) {
        if (videoAspectRatio == aspectRatio || aspectRatio <= 0 || !aspectRatio.isFinite()) return
        videoAspectRatio = aspectRatio
        requestLayout()
    }

    companion object {
        const val MIN_USER_ZOOM = 1f
        const val MAX_USER_ZOOM = 4f
    }
}
