package org.schabi.newpipe.player.ui

import androidx.fragment.app.FragmentActivity
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

object FullscreenCommentsSidebar {
    const val TAG = "fullscreen_comments_sidebar"

    @JvmStatic
    fun show(activity: FragmentActivity, serviceId: Int, url: String, title: String) {
        val manager = activity.supportFragmentManager
        if (manager.isStateSaved || manager.isDestroyed) return
        val current = manager.findFragmentByTag(TAG) as? FullscreenCommentsDialog
        if (current?.dialog?.isShowing == true) return
        FullscreenCommentsDialog.newInstance(serviceId, url, title).show(manager, TAG)
    }

    @JvmStatic
    fun hide(activity: FragmentActivity): Boolean {
        val sidebar = activity.supportFragmentManager.findFragmentByTag(TAG) as? FullscreenCommentsDialog ?: return false
        sidebar.dismissAllowingStateLoss()
        return true
    }

    @JvmStatic
    fun showReplies(activity: FragmentActivity, comment: CommentsInfoItem): Boolean {
        val sidebar = activity.supportFragmentManager.findFragmentByTag(TAG) as? FullscreenCommentsDialog ?: return false
        if (sidebar.dialog?.isShowing != true || sidebar.childFragmentManager.isStateSaved) return false
        sidebar.showReplies(comment)
        return true
    }
}
