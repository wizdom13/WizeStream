package org.schabi.newpipe.database.playlist.model

/** Minimal JVM representation required by the shared playlist synchronization model. */
data class PlaylistRemoteEntity(
    var uid: Long = 0,
    val serviceId: Int = -1,
    val orderingName: String?,
    val url: String?,
    val thumbnailUrl: String?,
    val uploader: String?,
    var displayIndex: Long = -1,
    val streamCount: Long?
)
