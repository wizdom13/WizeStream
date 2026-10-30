package org.schabi.newpipe.views

import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup

/** Scoped workaround for Android 10 devices that lose the detail panel's cached draws. */
object DetailTextRendering {
    @JvmStatic
    fun apply(root: View) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q || containsVideoSurface(root)) return
        // The recording shows action icons disappearing together with text. Rasterize the
        // bounded detail panel together, rather than caching each text label independently.
        // The caller passes detailContentRootLayout: the player and scrolling pager are siblings.
        root.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun containsVideoSurface(view: View): Boolean = when (view) {
        is SurfaceView, is TextureView -> true
        is ViewGroup -> (0 until view.childCount).any { containsVideoSurface(view.getChildAt(it)) }
        else -> false
    }
}
