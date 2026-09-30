/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.util.ExtractorHelper

/** Fetches playlist membership when marking a bookmark without opening its detail screen. */
object LearningPlaylistMarker {
    @JvmStatic
    fun markRemote(manager: LearningContentManager, serviceId: Int, url: String): Completable = ExtractorHelper.getPlaylistInfo(serviceId, url, false)
        .subscribeOn(Schedulers.io())
        .flatMapCompletable { info ->
            manager.setRemotePlaylistMarked(
                serviceId,
                url,
                info.name,
                PlaylistRemoteEntity(info).thumbnailUrl,
                info.relatedItems.map(::StreamEntity),
                true
            ).andThen(indexRemaining(manager, serviceId, url, info.nextPage))
        }

    private fun indexRemaining(manager: LearningContentManager, serviceId: Int, url: String, page: Page?): Completable = Completable.defer {
        if (!Page.isValid(page) || !manager.isRemotePlaylistMarked(serviceId, url)) {
            Completable.complete()
        } else {
            ExtractorHelper.getMorePlaylistItems(serviceId, url, page)
                .subscribeOn(Schedulers.io())
                .flatMapCompletable { result ->
                    manager.addRemotePlaylistStreams(serviceId, url, result.items.map(::StreamEntity))
                        .andThen(indexRemaining(manager, serviceId, url, result.nextPage))
                }
        }
    }
}
