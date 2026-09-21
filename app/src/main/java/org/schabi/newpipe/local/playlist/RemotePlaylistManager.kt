/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.profiles.ProfileManager

class RemotePlaylistManager @JvmOverloads constructor(
    private val database: AppDatabase,
    private val profileId: String = ProfileManager.DEFAULT_PROFILE_ID
) {
    private val playlistRemoteTable = database.playlistRemoteDAO()

    val playlists: Flowable<MutableList<PlaylistRemoteEntity>>
        get() = playlistRemoteTable.getPlaylistsForProfile(profileId)
            .subscribeOn(Schedulers.io())

    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity> {
        return playlistRemoteTable.getPlaylistForProfile(profileId, playlistId)
            .subscribeOn(Schedulers.io())
    }

    fun getPlaylist(info: PlaylistInfo): Flowable<MutableList<PlaylistRemoteEntity>> {
        return getPlaylist(info.serviceId, info.url)
    }

    fun getPlaylist(serviceId: Int, url: String?): Flowable<MutableList<PlaylistRemoteEntity>> {
        return playlistRemoteTable.getPlaylistForProfile(profileId, serviceId.toLong(), url)
            .subscribeOn(Schedulers.io())
    }

    fun deletePlaylist(playlistId: Long): Single<Int> {
        return Single.fromCallable {
            playlistRemoteTable.deletePlaylistForProfile(profileId, playlistId)
        }
            .subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(
        updateItems: List<PlaylistRemoteEntity>,
        deletedItems: List<Long>
    ): Completable {
        return Completable.fromRunnable {
            database.runInTransaction {
                deletedItems.forEach {
                    playlistRemoteTable.deletePlaylistForProfile(profileId, it)
                }
                updateItems.forEach {
                    playlistRemoteTable.upsertForProfile(profileId, it)
                }
            }
        }.subscribeOn(Schedulers.io())
    }

    fun onBookmark(playlistInfo: PlaylistInfo): Single<Long> {
        return Single.fromCallable {
            val playlist = PlaylistRemoteEntity(playlistInfo).apply {
                this.profileId = this@RemotePlaylistManager.profileId
            }
            playlistRemoteTable.upsertForProfile(profileId, playlist)
        }.subscribeOn(Schedulers.io())
    }

    @JvmOverloads
    fun onUpdate(
        playlistId: Long,
        playlistInfo: PlaylistInfo,
        existingThumbnailUrl: String? = null
    ): Single<Int> {
        return Single.fromCallable {
            val playlist = PlaylistRemoteEntity(
                playlistInfo,
                existingThumbnailUrl
            ).apply {
                uid = playlistId
                profileId = this@RemotePlaylistManager.profileId
            }
            playlistRemoteTable.updateForProfile(profileId, playlist)
        }.subscribeOn(Schedulers.io())
    }
}
