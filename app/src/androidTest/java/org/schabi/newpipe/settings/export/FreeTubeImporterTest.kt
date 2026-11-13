package org.schabi.newpipe.settings.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.profiles.ProfileManager

class FreeTubeImporterTest {
    @Test
    fun repeatedImportsKeepExistingSubscriptionsPlaylistOccurrencesAndOtherProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val profile = ProfileManager.DEFAULT_PROFILE_ID
            val other = "11111111-1111-1111-1111-111111111111"
            val url = "https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"
            db.subscriptionDAO().insert(SubscriptionEntity(serviceId = 0, url = url, name = "Keep my name", profileId = profile))
            val data = FreeTubeBackup.parse(
                """{"_id":"allChannels","name":"Main","subscriptions":[{"id":"UCabcdefghijklmnopqrstuv","name":"Imported"}]}
                   |{"playlistName":"Same","videos":[]}
                   |{"playlistName":"Same","videos":[{"videoId":"abcdefghijk"},{"videoId":"abcdefghijk"}]}
                """.trimMargin()
            )
            val importer = LibreTubeImporter(db, profile)
            val selected = setOf(LibreTubeCategory.SUBSCRIPTIONS, LibreTubeCategory.PLAYLISTS)
            importer.import(data, selected, 1234, sourceName = "FreeTube")
            assertEquals(0, importer.import(data, selected, 5678, sourceName = "FreeTube").added)
            assertEquals("Keep my name", db.subscriptionDAO().getSubscriptionDirectForProfile(profile, 0, url)!!.name)
            val playlists = db.playlistDAO().getAllDirectForProfile(profile)
            assertEquals(setOf("Same (FreeTube)", "Same (FreeTube 2)"), playlists.map { it.name }.toSet())
            val populated = playlists.single { it.name == "Same (FreeTube 2)" }
            assertEquals(2, db.playlistStreamDAO().getOrderedStreamsDirectForProfile(profile, populated.uid).size)
            assertTrue(db.subscriptionDAO().getAllDirectForProfile(other).isEmpty())
            assertTrue(db.playlistDAO().getAllDirectForProfile(other).isEmpty())
        } finally {
            db.close()
        }
    }
}
