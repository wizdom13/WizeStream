package org.schabi.newpipe.extractor.channel

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.net.URI
import java.net.URLDecoder

/** Optional channel metadata; never resolves a URL or makes a network request. */
object ChannelHandle {
    @JvmStatic
    fun normalize(value: String?): String? {
        val handle = value?.trim()?.takeIf { it.startsWith("@") } ?: return null
        return handle.takeIf { it.length > 1 && it.drop(1).none { c -> c.isWhitespace() || c in "/?#@" || c.isISOControl() } }
    }

    @JvmStatic
    fun fromUrl(value: String?): String? {
        if (value == null) return null
        return runCatching {
            val path = URI(value).rawPath ?: return@runCatching null
            normalize(URLDecoder.decode(path.removePrefix("/").substringBefore('/').replace("+", "%2B"), "UTF-8"))
        }.getOrNull()
    }

    @JvmStatic
    fun fromYoutube(response: JsonObject?): String? {
        if (response == null) return null
        fromUrl(response.obj("metadata").obj("channelMetadataRenderer").getString("vanityChannelUrl"))?.let { return it }
        val header = response.obj("header")
        val legacy = header.obj("c4TabbedHeaderRenderer").obj("channelHandleText")
        normalize(legacy.getString("simpleText"))?.let { return it }
        for (run in legacy.array("runs")) {
            normalize((run as? JsonObject)?.getString("text"))?.let { return it }
        }
        val metadata = header.obj("pageHeaderRenderer").obj("content").obj("pageHeaderViewModel")
            .obj("metadata").obj("contentMetadataViewModel")
        for (row in metadata.array("metadataRows")) {
            for (part in (row as? JsonObject)?.array("metadataParts") ?: JsonArray()) {
                normalize((part as? JsonObject)?.obj("text")?.getString("content"))?.let { return it }
            }
        }
        return null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject()
    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray()
}
