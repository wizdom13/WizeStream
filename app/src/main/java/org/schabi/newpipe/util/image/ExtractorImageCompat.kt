package org.schabi.newpipe.util.image

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Compatibility helpers for extractor image APIs.
 *
 * PipePipeExtractor builds may expose either NewPipeExtractor's Image-list getters
 * (getThumbnails()/getUploaderAvatars()) or older/smaller single URL accessors
 * (getThumbnailUrl()/thumbnailUrl and getUploaderAvatarUrl()/uploaderAvatarUrl). Keep these
 * differences isolated here instead of sprinkling branch-specific API checks through the app.
 */
object ExtractorImageCompat {
    @JvmStatic
    fun thumbnailImages(item: Any?): List<Image> = imageList(item, "getThumbnails")
        .ifEmpty { singleUrlImage(item, "getThumbnailUrl", "thumbnailUrl") }
        .ifEmpty { youtubeThumbnailFallback(item) }

    @JvmStatic
    fun uploaderAvatarImages(streamItem: Any?): List<Image> {
        if (streamItem is StreamInfo) {
            return streamItem.uploaderAvatars
                .ifEmpty { urlToImageList(streamItem.uploaderAvatarUrl) }
        }

        return imageList(streamItem, "getUploaderAvatars")
            .ifEmpty {
                singleUrlImage(
                    streamItem,
                    "getUploaderAvatarUrl",
                    "uploaderAvatarUrl"
                )
            }
    }

    @JvmStatic
    fun parentChannelAvatarImages(channelInfo: Any?): List<Image> = imageList(channelInfo, "getParentChannel" + "Avatars")
        .ifEmpty {
            singleUrlImage(
                channelInfo,
                "getParentChannelAvatarUrl",
                "parentChannelAvatarUrl"
            )
        }

    @JvmStatic
    fun setThumbnailImages(item: InfoItem, images: List<Image>) {
        if (!invokeSetter(item, "setThumbnails", images)) {
            setSingleUrl(
                item,
                "setThumbnailUrl",
                "thumbnailUrl",
                ImageStrategy.imageListToDbUrl(images)
            )
        }
    }

    @JvmStatic
    fun setUploaderAvatarImages(item: StreamInfoItem, images: List<Image>) {
        if (!invokeSetter(item, "setUploaderAvatars", images)) {
            setSingleUrl(
                item,
                "setUploaderAvatarUrl",
                "uploaderAvatarUrl",
                ImageStrategy.imageListToDbUrl(images)
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun imageList(item: Any?, getterName: String): List<Image> {
        if (item == null) return emptyList()
        return runCatching {
            item.javaClass.methods
                .firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(item) as? List<Image>
        }.getOrNull().orEmpty()
    }

    private fun singleUrlImage(item: Any?, getterName: String, fieldName: String): List<Image> {
        if (item == null) return emptyList()
        val url = runCatching {
            item.javaClass.methods
                .firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(item) as? String
        }.getOrNull() ?: runCatching {
            item.javaClass.fields
                .firstOrNull { it.name == fieldName }
                ?.get(item) as? String
        }.getOrNull()

        return urlToImageList(url)
    }

    private fun youtubeThumbnailFallback(item: Any?): List<Image> {
        if (item == null) return emptyList()
        val url = runCatching {
            item.javaClass.methods
                .firstOrNull { it.name == "getUrl" && it.parameterCount == 0 }
                ?.invoke(item) as? String
        }.getOrNull() ?: runCatching {
            item.javaClass.fields
                .firstOrNull { it.name == "url" }
                ?.get(item) as? String
        }.getOrNull()

        val videoId = youtubeVideoId(url) ?: return emptyList()
        return urlToImageList("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
    }

    private fun youtubeVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})(?:[&#].*)?"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})(?:[?&#/].*)?"),
            Regex("/(?:embed|shorts|live)/([A-Za-z0-9_-]{11})(?:[?&#/].*)?"),
            Regex("/watch/([A-Za-z0-9_-]{11})(?:[?&#/].*)?")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(url)?.groupValues?.getOrNull(1)
        }
    }

    private fun urlToImageList(url: String?): List<Image> {
        return url?.takeIf { it.isNotBlank() }?.let {
            listOf(
                Image(
                    it,
                    Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN,
                    Image.ResolutionLevel.UNKNOWN
                )
            )
        }.orEmpty()
    }

    private fun invokeSetter(item: Any, setterName: String, images: List<Image>): Boolean {
        return runCatching {
            item.javaClass.methods
                .firstOrNull { it.name == setterName && it.parameterCount == 1 }
                ?.also { it.invoke(item, images) } != null
        }.getOrDefault(false)
    }

    private fun setSingleUrl(item: Any, setterName: String, fieldName: String, url: String?) {
        runCatching {
            item.javaClass.methods
                .firstOrNull { it.name == setterName && it.parameterCount == 1 }
                ?.invoke(item, url)
                ?: item.javaClass.fields
                    .firstOrNull { it.name == fieldName }
                    ?.set(item, url)
        }
    }
}
