package org.schabi.newpipe.local.radio

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/** Reads timed track metadata, never a station's static media title or an arbitrary website. */
internal object RadioTrackMetadata {
    fun containsTitle(metadata: Metadata): Boolean = (0 until metadata.length()).any {
        val entry = metadata[it]
        entry is IcyInfo || entry is TextInformationFrame && entry.id in listOf("TIT2", "TT2")
    }

    fun title(metadata: Metadata): String? {
        var title: String? = null
        var artist: String? = null
        for (index in 0 until metadata.length()) {
            when (val entry = metadata[index]) {
                is IcyInfo -> return clean(entry.title)

                is TextInformationFrame -> when (entry.id) {
                    "TIT2", "TT2" -> title = clean(entry.values.firstOrNull())
                    "TPE1", "TP1" -> artist = clean(entry.values.firstOrNull())
                }
            }
        }
        return title?.let { if (artist == null) it else "$artist - $it" }
    }

    fun clean(value: String?): String? = value
        ?.replace(Regex("[\\p{Cc}\\s]+"), " ")?.trim()?.take(1024)?.takeIf(String::isNotBlank)
}
