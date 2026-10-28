package org.schabi.newpipe.views

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Android 10 can lose cached hardware text draws beside a repeatedly updating video surface. */
object DetailTextRendering {
    @JvmStatic
    fun apply(root: View) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return
        if (root is TextView) {
            // Isolate only the small detail labels; never put the player SurfaceView or the
            // scrolling page into a software layer. Touch, selection and accessibility stay native.
            root.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        } else if (root is ViewGroup) {
            for (index in 0 until root.childCount) apply(root.getChildAt(index))
        }
    }
}
