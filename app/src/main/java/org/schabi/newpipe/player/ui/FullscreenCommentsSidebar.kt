package org.schabi.newpipe.player.ui

import androidx.fragment.app.FragmentActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.list.comments.CommentsFragment

object FullscreenCommentsSidebar {
    const val TAG = "fullscreen_comments_sidebar"

    @JvmStatic
    fun show(activity: FragmentActivity, serviceId: Int, url: String, title: String) {
        val manager = activity.supportFragmentManager
        if (manager.findFragmentByTag(TAG) != null) return

        manager.beginTransaction()
            .add(
                R.id.fullscreenCommentsContainer,
                CommentsFragment.getInstance(serviceId, url, title),
                TAG
            )
            .addToBackStack(TAG)
            .commit()
    }

    @JvmStatic
    fun hide(activity: FragmentActivity): Boolean {
        val manager = activity.supportFragmentManager
        if (manager.findFragmentByTag(TAG) == null) return false
        manager.popBackStack()
        return true
    }
}
