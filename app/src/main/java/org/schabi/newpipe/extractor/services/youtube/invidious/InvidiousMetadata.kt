package org.schabi.newpipe.extractor.services.youtube.invidious

import com.grack.nanojson.JsonObject
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.Description

internal object InvidiousMetadata {
    @JvmStatic
    fun description(video: JsonObject): Description {
        val plain = video.getString("description", "")
        if (plain.isNotBlank()) return Description(plain, Description.PLAIN_TEXT)
        // Parse locally into text: instance HTML must never load embedded images or other resources.
        val document = Jsoup.parseBodyFragment(video.getString("descriptionHtml", ""))
        document.select("script, style, iframe, object, embed").remove()
        document.select("p, div, li").before("\n").append("\n")
        return Description(document.body().wholeText().trim(), Description.PLAIN_TEXT)
    }

    @JvmStatic
    fun commentAvatars(comment: JsonObject, api: InvidiousApi): List<Image> {
        val images = api.images(comment.getArray("authorThumbnails"))
        if (images.isNotEmpty()) return images
        val url = api.image(comment.getString("authorThumbnail", ""))
        return if (url.isEmpty()) emptyList() else listOf(Image(url, -1, -1, Image.ResolutionLevel.UNKNOWN))
    }
}
