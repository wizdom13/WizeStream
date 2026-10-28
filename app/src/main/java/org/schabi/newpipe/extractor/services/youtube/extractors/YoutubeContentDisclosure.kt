package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.stream.Description

/** A provider-authored disclosure, never a classification inferred from video text. */
class YoutubeContentDisclosure(header: String, body: String) : MetaInfo(
    header,
    Description(body, Description.PLAIN_TEXT),
    emptyList(),
    emptyList()
) {
    companion object {
        const val HELP_URL = "https://support.google.com/youtube/answer/15447836"

        @JvmStatic
        fun appendTo(metadata: List<MetaInfo>, response: JsonObject?): List<MetaInfo> {
            if (response == null) return metadata
            val disclosures = mutableListOf<YoutubeContentDisclosure>()
            for (panel in response.getArray("engagementPanels")) {
                val items = (panel as? JsonObject)?.getObject("engagementPanelSectionListRenderer")
                    ?.getObject("content")?.getObject("structuredDescriptionContentRenderer")
                    ?.getArray("items") ?: continue
                for (item in items) {
                    val section = (item as? JsonObject)?.getObject("howThisWasMadeSectionViewModel") ?: continue
                    val title = section.getObject("bodyHeader").getString("content", "").trim()
                    val body = section.getObject("bodyText").getString("content", "").trim()
                    if (title.isNotEmpty()) {
                        disclosures += YoutubeContentDisclosure(title.take(160), body.take(1200))
                    }
                }
            }
            return metadata + disclosures.distinctBy { it.title to it.content.content }
        }

        @JvmStatic
        fun otherMetadata(metadata: List<MetaInfo>): List<MetaInfo> = metadata.filterNot { it is YoutubeContentDisclosure }
    }
}
