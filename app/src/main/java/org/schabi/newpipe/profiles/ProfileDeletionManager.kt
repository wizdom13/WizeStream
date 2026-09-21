package org.schabi.newpipe.profiles

import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.local.bookmark.PlaylistCategories
import org.schabi.newpipe.sync.HistorySyncRecorder

object ProfileDeletionManager {
    // Delete only data that has become profile-scoped so later phases can extend cleanup safely.
    @JvmStatic
    fun deleteProfile(context: Context, profileId: String): Completable {
        if (profileId == ProfileManager.DEFAULT_PROFILE_ID) {
            return Completable.error(
                IllegalArgumentException("The Default profile cannot be deleted")
            )
        }

        val appContext = context.applicationContext
        return Completable.fromAction {
            runCatching {
                val historySyncRecorder = HistorySyncRecorder.get(appContext)
                historySyncRecorder.recordWatchAllDeleteForProfile(profileId)
                historySyncRecorder.recordProgressAllDeleteForProfile(profileId)
            }
            val database = NewPipeDatabase.getInstance(appContext)
            database.runInTransaction {
                database.savedSearchFeedDAO().deleteAllForProfile(profileId)
                database.playlistRemoteDAO().deleteAllForProfile(profileId)
                database.playlistDAO().deleteAllForProfile(profileId)
                database.streamHistoryDAO().deleteAllForProfile(profileId)
                database.streamStateDAO().deleteAllForProfile(profileId)
                database.learningSessionDAO().deleteAllForProfile(profileId)
                database.feedGroupDAO().deleteAllForProfile(profileId)
                database.subscriptionDAO().deleteAllForProfile(profileId)
            }
            PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .remove(PlaylistCategories.preferenceKey(profileId))
                .apply()
            database.streamDAO().deleteOrphans()
            check(ProfileManager.deleteProfile(appContext, profileId)) {
                "Profile metadata no longer exists"
            }
        }.subscribeOn(Schedulers.io())
    }
}
