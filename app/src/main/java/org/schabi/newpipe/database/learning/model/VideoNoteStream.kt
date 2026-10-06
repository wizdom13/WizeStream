package org.schabi.newpipe.database.learning.model

/** Minimal identity for a video with at least one saved note. */
data class VideoNoteStream(val streamId: Long, val serviceId: Int, val url: String)
