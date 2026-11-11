package org.schabi.newpipe.local.radio

import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager

/** Uses normal profile history, deletion, retention, and backups without a new database table. */
internal class RadioSongHistoryStore(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun enabled(): Boolean = !IncognitoMode.isEnabled(preferences) &&
        preferences.getBoolean(context.getString(R.string.enable_watch_history_key), false)

    fun record(station: PlayQueueItem, title: String, profileId: String): Completable {
        val date = OffsetDateTime.now(ZoneOffset.UTC)
        return Completable.fromAction {
            val database = NewPipeDatabase.getInstance(context)
            database.runInTransaction {
                if (!enabled() || ProfileManager.getActiveProfileId(context) != profileId) return@runInTransaction
                val stream = entity(station, title) ?: return@runInTransaction
                val id = database.streamDAO().upsert(stream)
                val history = database.streamHistoryDAO()
                val previous = history.getLatestEntryForProfile(profileId, id)
                if (previous != null) history.delete(previous)
                history.insert(StreamHistoryEntity(id, date, (previous?.repeatCount ?: 0) + 1, profileId))
            }
        }.subscribeOn(Schedulers.single())
    }

    companion object {
        fun entity(station: PlayQueueItem, title: String): StreamEntity? {
            if (!station.isRadio) return null
            val url = secureRadioUrl(station.url) ?: return null
            val track = RadioTrackMetadata.clean(title) ?: return null
            val key = MessageDigest.getInstance("SHA-256").digest(track.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return StreamEntity(
                serviceId = PlayQueueItem.RADIO_SERVICE_ID,
                url = "$url#wizestream-track=$key",
                title = track,
                streamType = StreamType.AUDIO_LIVE_STREAM,
                duration = -1,
                uploader = station.title,
                thumbnailUrl = station.localThumbnailUrl,
                sourceType = StreamEntity.SOURCE_TYPE_RADIO,
                mimeType = station.mimeType,
                localAlbum = station.uploader
            )
        }
    }
}
