package org.schabi.newpipe.player.ui

import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog

internal object AnchoredPlaybackDialog {
    fun show(anchor: View, dialog: AlertDialog) {
        if (!anchor.isAttachedToWindow || anchor.windowToken == null) return
        val window = dialog.window ?: return
        window.attributes = window.attributes.apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            token = anchor.windowToken
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = dialog.dismiss()
        }
        anchor.addOnAttachStateChangeListener(listener)
        dialog.setOnDismissListener { anchor.removeOnAttachStateChangeListener(listener) }
        try {
            dialog.show()
        } catch (_: WindowManager.BadTokenException) {
            anchor.removeOnAttachStateChangeListener(listener)
            dialog.dismiss()
        }
    }
}
