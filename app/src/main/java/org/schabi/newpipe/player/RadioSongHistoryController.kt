package org.schabi.newpipe.player

import android.content.Context
import androidx.media3.common.Metadata
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.local.radio.RadioSongHistoryStore
import org.schabi.newpipe.local.radio.RadioTrackMetadata
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager

/** Retains only the latest pending song while buffering; repeated ICY packets are not new plays. */
internal class RadioSongHistoryController(
    context: Context,
    private val updates: CompositeDisposable,
    private val store: RadioSongHistoryStore = RadioSongHistoryStore(context),
    private val activeProfile: () -> String = { ProfileManager.getActiveProfileId(context) }
) {
    private var station: PlayQueueItem? = null
    private var profile: String? = null
    private var lastTitle: String? = null
    private var pending: String? = null

    fun onMetadata(item: PlayQueueItem?, metadata: Metadata, playing: Boolean) {
        refresh(item)
        if (station == null) return
        if (!RadioTrackMetadata.containsTitle(metadata)) return
        val title = RadioTrackMetadata.title(metadata)
        if (title == lastTitle) return
        lastTitle = title
        pending = title.takeIf { store.enabled() }
        onPlayback(item, playing)
    }

    fun onPlayback(item: PlayQueueItem?, playing: Boolean) {
        refresh(item)
        if (!store.enabled()) pending = null
        val title = pending ?: return
        if (!playing) return
        val current = station ?: return
        pending = null
        updates.add(store.record(current, title, requireNotNull(profile)).onErrorComplete().subscribe())
    }

    private fun refresh(item: PlayQueueItem?) {
        val current = item?.takeIf { it.isRadio }
        val profileId = activeProfile()
        if (current !== station || profile != profileId) {
            station = current
            profile = profileId
            lastTitle = null
            pending = null
        }
    }
}
