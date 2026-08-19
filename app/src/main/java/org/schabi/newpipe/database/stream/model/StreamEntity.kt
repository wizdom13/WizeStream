package org.schabi.newpipe.database.stream.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_SERVICE_ID
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_URL
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(
    tableName = STREAM_TABLE,
    indices = [
        Index(value = [STREAM_SERVICE_ID, STREAM_URL], unique = true)
    ]
)
data class StreamEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = STREAM_ID)
    var uid: Long = 0,

    @ColumnInfo(name = STREAM_SERVICE_ID)
    var serviceId: Int,

    @ColumnInfo(name = STREAM_URL)
    var url: String,

    @ColumnInfo(name = STREAM_TITLE)
    var title: String,

    @ColumnInfo(name = STREAM_TYPE)
    var streamType: StreamType,

    @ColumnInfo(name = STREAM_DURATION)
    var duration: Long,

    @ColumnInfo(name = STREAM_UPLOADER)
    var uploader: String,

    @ColumnInfo(name = STREAM_UPLOADER_URL)
    var uploaderUrl: String? = null,

    @ColumnInfo(name = STREAM_THUMBNAIL_URL)
    var thumbnailUrl: String? = null,

    @ColumnInfo(name = STREAM_VIEWS)
    var viewCount: Long? = null,

    @ColumnInfo(name = STREAM_TEXTUAL_UPLOAD_DATE)
    var textualUploadDate: String? = null,

    @ColumnInfo(name = STREAM_UPLOAD_DATE)
    var uploadDate: OffsetDateTime? = null,

    @ColumnInfo(name = STREAM_IS_UPLOAD_DATE_APPROXIMATION)
    var isUploadDateApproximation: Boolean? = null,

    @ColumnInfo(name = STREAM_UPLOADER_AVATAR_URL)
    var uploaderAvatarUrl: String? = null,

    @ColumnInfo(name = STREAM_REQUIRES_MEMBERSHIP, defaultValue = "0")
    var requiresMembership: Boolean = false,

    @ColumnInfo(name = STREAM_SOURCE_TYPE, defaultValue = "'REMOTE'")
    var sourceType: String = SOURCE_TYPE_REMOTE,

    @ColumnInfo(name = STREAM_MIME_TYPE)
    var mimeType: String? = null,

    @ColumnInfo(name = STREAM_LOCAL_MEDIA_ID)
    var localMediaId: Long? = null,

    @ColumnInfo(name = STREAM_LOCAL_ALBUM)
    var localAlbum: String? = null,

    @ColumnInfo(name = STREAM_LOCAL_FOLDER)
    var localFolder: String? = null
) : Serializable {
    @Ignore
    constructor(item: StreamInfoItem) : this(
        serviceId = item.serviceId, url = item.url, title = item.name,
        streamType = item.streamType, duration = item.duration, uploader = item.uploaderName,
        uploaderUrl = item.uploaderUrl,
        thumbnailUrl = ImageStrategy.imageListToDbUrl(ExtractorImageCompat.thumbnailImages(item)), viewCount = item.viewCount,
        textualUploadDate = item.textualUploadDate, uploadDate = item.uploadDate?.offsetDateTime(),
        isUploadDateApproximation = item.uploadDate?.isApproximation,
        uploaderAvatarUrl = ImageStrategy.imageListToDbUrl(
            ExtractorImageCompat.uploaderAvatarImages(item)
        ),
        requiresMembership = item.requiresMembership()
    )

    @Ignore
    constructor(info: StreamInfo) : this(
        serviceId = info.serviceId, url = info.url, title = info.name,
        streamType = info.streamType, duration = info.duration, uploader = info.uploaderName,
        uploaderUrl = info.uploaderUrl,
        thumbnailUrl = ImageStrategy.imageListToDbUrl(ExtractorImageCompat.thumbnailImages(info)), viewCount = info.viewCount,
        textualUploadDate = info.textualUploadDate, uploadDate = info.uploadDate?.offsetDateTime(),
        isUploadDateApproximation = info.uploadDate?.isApproximation,
        uploaderAvatarUrl = ImageStrategy.imageListToDbUrl(
            ExtractorImageCompat.uploaderAvatarImages(info)
        ),
        requiresMembership = info.requiresMembership()
    )

    @Ignore
    constructor(item: PlayQueueItem) : this(
        serviceId = item.serviceId,
        url = item.url,
        title = item.title,
        streamType = item.streamType,
        duration = item.duration,
        uploader = item.uploader,
        uploaderUrl = item.uploaderUrl,
        thumbnailUrl = if (item.isLocalMedia) {
            item.localThumbnailUrl
        } else {
            ImageStrategy.imageListToDbUrl(item.getThumbnails())
        },
        sourceType = item.sourceType.name,
        mimeType = item.mimeType,
        localMediaId = item.localMediaId.takeIf { it >= 0 },
        localAlbum = item.album,
        localFolder = item.folder
    )

    val isLocalMedia: Boolean
        get() = sourceType == SOURCE_TYPE_LOCAL

    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(serviceId, url, title, streamType)
        item.duration = duration
        item.uploaderName = uploader
        item.uploaderUrl = uploaderUrl
        ExtractorImageCompat.setThumbnailImages(item, ImageStrategy.dbUrlToImageList(thumbnailUrl))
        ExtractorImageCompat.setUploaderAvatarImages(
            item,
            ImageStrategy.dbUrlToImageList(uploaderAvatarUrl)
        )

        if (viewCount != null) item.viewCount = viewCount as Long
        item.textualUploadDate = textualUploadDate
        item.uploadDate = uploadDate?.let {
            DateWrapper(it, isUploadDateApproximation ?: false)
        }
        item.setRequiresMembership(requiresMembership)

        return item
    }

    fun toPlayQueueItem(): PlayQueueItem = if (isLocalMedia) {
        PlayQueueItem.localMedia(
            title,
            url,
            duration,
            uploader,
            localAlbum,
            localFolder,
            mimeType,
            localMediaId ?: -1L,
            streamType == StreamType.VIDEO_STREAM,
            thumbnailUrl
        )
    } else {
        PlayQueueItem(toStreamInfoItem())
    }

    companion object {
        const val STREAM_TABLE = "streams"
        const val STREAM_ID = "uid"
        const val STREAM_SERVICE_ID = "service_id"
        const val STREAM_URL = "url"
        const val STREAM_TITLE = "title"
        const val STREAM_TYPE = "stream_type"
        const val STREAM_DURATION = "duration"
        const val STREAM_UPLOADER = "uploader"
        const val STREAM_UPLOADER_URL = "uploader_url"
        const val STREAM_UPLOADER_AVATAR_URL = "uploader_avatar_url"
        const val STREAM_REQUIRES_MEMBERSHIP = "requires_membership"
        const val STREAM_SOURCE_TYPE = "source_type"
        const val STREAM_MIME_TYPE = "mime_type"
        const val STREAM_LOCAL_MEDIA_ID = "local_media_id"
        const val STREAM_LOCAL_ALBUM = "local_album"
        const val STREAM_LOCAL_FOLDER = "local_folder"
        const val STREAM_THUMBNAIL_URL = "thumbnail_url"

        const val SOURCE_TYPE_REMOTE = "REMOTE"
        const val SOURCE_TYPE_LOCAL = "LOCAL"

        const val STREAM_VIEWS = "view_count"
        const val STREAM_TEXTUAL_UPLOAD_DATE = "textual_upload_date"
        const val STREAM_UPLOAD_DATE = "upload_date"
        const val STREAM_IS_UPLOAD_DATE_APPROXIMATION = "is_upload_date_approximation"
    }
}
