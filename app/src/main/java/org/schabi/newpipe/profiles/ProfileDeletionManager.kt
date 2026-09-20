package org.schabi.newpipe.profiles

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase

object ProfileDeletionManager {
    // Delete only data already scoped to profiles; later phases extend this cleanup.
    @JvmStatic
    fun deleteProfile(context: Context, profileId: String): Completable {
        if (profileId == ProfileManager.DEFAULT_PROFILE_ID) {
            return Completable.error(
                IllegalArgumentException("The Default profile cannot be deleted")
            )
        }

        val appContext = context.applicationContext
        return Completable.fromAction {
            val database = NewPipeDatabase.getInstance(appContext)
            database.runInTransaction {
                database.feedGroupDAO().deleteAllForProfile(profileId)
                database.subscriptionDAO().deleteAllForProfile(profileId)
            }
            database.streamDAO().deleteOrphans()
            check(ProfileManager.deleteProfile(appContext, profileId)) {
                "Profile metadata no longer exists"
            }
        }.subscribeOn(Schedulers.io())
    }
}
