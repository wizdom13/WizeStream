package org.schabi.newpipe.themes

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject

/** Versioned, data-only theme recipe. No resource paths, URLs, or executable content. */
data class ThemePack(
    val name: String,
    val author: String,
    val mode: String = "system",
    val night: String = "dark",
    val lightAccent: String = "wizestream",
    val darkAccent: String = "wizestream"
) {
    fun encode(): String = JSONObject().apply {
        put("format", "wizestream-theme")
        put("version", 1)
        put("name", name)
        put("author", author)
        put("mode", mode)
        put("night", night)
        put("lightAccent", lightAccent)
        put("darkAccent", darkAccent)
    }.toString(2)

    companion object {
        const val MAX_BYTES = 16 * 1024
        val accents = setOf("wizestream", "neutral", "green", "blue", "cyan", "purple", "yellow", "orange", "pink", "red", "follow_system")
        private val fields = setOf("format", "version", "name", "author", "mode", "night", "lightAccent", "darkAccent")

        fun read(input: InputStream): ThemePack {
            val buffer = ByteArray(MAX_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    buffer[size++] = single.toByte()
                } else {
                    size += count
                }
            }
            require(size <= MAX_BYTES) { "Theme pack exceeds 16 KiB" }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            return parse(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
        }

        fun parse(text: String): ThemePack {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val json = JSONObject()
            JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(key in fields && !json.has(key)) { "Unknown or duplicate field" }
                    if (key == "version") {
                        require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1") { "Unsupported version" }
                        json.put(key, 1)
                    } else {
                        require(reader.peek() == JsonToken.STRING) { "Expected text: $key" }
                        json.put(key, reader.nextString())
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing content" }
            }
            require(json.get("format") == "wizestream-theme") { "Unsupported format" }
            require(json.get("version") == 1) { "Unsupported version" }
            fun string(key: String, fallback: String? = null): String {
                if (!json.has(key) && fallback != null) return fallback
                return json.get(key) as? String ?: error("Expected text: $key")
            }
            fun label(key: String): String = string(key).also {
                require(it.isNotBlank() && it.length <= 80 && it.none { c -> c.isISOControl() || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' }) {
                    "Invalid $key"
                }
            }
            val mode = string("mode", "system").also { require(it in setOf("system", "light", "dark", "black", "eye_comfort")) }
            val night = string("night", "dark").also { require(it in setOf("dark", "black")) }
            val light = string("lightAccent", "wizestream").also { require(it in accents) }
            val dark = string("darkAccent", "wizestream").also { require(it in accents) }
            return ThemePack(label("name"), label("author"), mode, night, light, dark)
        }
    }
}
