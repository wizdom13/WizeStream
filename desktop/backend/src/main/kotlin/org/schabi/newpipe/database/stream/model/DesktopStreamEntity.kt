package org.schabi.newpipe.database.stream.model

import org.schabi.newpipe.extractor.stream.StreamType

/** Minimal JVM representation required by the shared playlist and history models. */
data class StreamEntity(
    var uid: Long = 0,
    var serviceId: Int,
    var url: String,
    var title: String,
    var streamType: StreamType,
    var duration: Long,
    var uploader: String,
    var uploaderUrl: String? = null,
    var thumbnailUrl: String? = null
)
