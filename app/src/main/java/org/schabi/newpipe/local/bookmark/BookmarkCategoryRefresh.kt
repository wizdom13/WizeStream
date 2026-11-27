package org.schabi.newpipe.local.bookmark

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.profiles.ProfileManager

/** Returning to an unchanged tab must not clear and rebuild its playlist adapter. */
class BookmarkCategoryRefresh {
    private var previous: Snapshot? = null

    @JvmOverloads
    fun refresh(context: Context, profileId: String, list: RecyclerView?, update: Runnable, unchanged: Runnable = Runnable {}) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val categories = preferences.getString(PlaylistCategories.preferenceKey(profileId), null)
            ?: if (profileId == ProfileManager.DEFAULT_PROFILE_ID) {
                preferences.getString(PlaylistCategories.PREFERENCE_KEY, "")
            } else {
                ""
            }
        val current = Snapshot(profileId, categories.orEmpty(), LearningMode.isEnabled(context, profileId), list?.layoutManager)
        if (current == previous) {
            unchanged.run()
            return
        }
        previous = current
        update.run()
    }

    /** A recreated or reset view needs an initial render even when its preferences are unchanged. */
    fun invalidate() {
        previous = null
    }

    private data class Snapshot(
        val profileId: String,
        val categories: String,
        val learningEnabled: Boolean,
        // Layout preferences and configuration changes can replace the manager on resume.
        // Its new viewport still needs the fragment's saved scroll position restored.
        val layoutManager: RecyclerView.LayoutManager?
    )
}
