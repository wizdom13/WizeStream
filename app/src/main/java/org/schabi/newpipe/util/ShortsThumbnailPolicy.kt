package org.schabi.newpipe.util

import android.widget.ImageView
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object ShortsThumbnailPolicy {
    @JvmStatic
    fun scaleType(stream: StreamInfoItem): ImageView.ScaleType = if (
        StreamListFilter.categoryOf(stream) == StreamListFilter.SHORTS
    ) {
        ImageView.ScaleType.FIT_CENTER
    } else {
        ImageView.ScaleType.CENTER_CROP
    }

    fun scaleType(streamUrl: String?, durationSeconds: Long): ImageView.ScaleType {
        val isShort = streamUrl?.contains("/shorts/") == true
        return if (isShort) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
    }
}
