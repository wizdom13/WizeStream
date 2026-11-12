package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.schabi.newpipe.R

/** Flat and spherical video surfaces travel together into the miniplayer. */
class VideoSurfaceContainer(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    var miniPlayerMode = false
        private set
    var onPresentationChanged: (() -> Unit)? = null
    internal var onBeforeDetach: (() -> Unit)? = null

    fun prepareForReparent() {
        onBeforeDetach?.invoke()
    }

    fun setMiniPlayerMode(enabled: Boolean) {
        miniPlayerMode = enabled
        findViewById<ExpandableSurfaceView>(R.id.surfaceView)?.setMiniPlayerMode(enabled)
        onPresentationChanged?.invoke()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = miniPlayerMode || super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent): Boolean = !miniPlayerMode && super.onTouchEvent(event)

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        onPresentationChanged?.invoke()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        onPresentationChanged?.invoke()
    }
}
