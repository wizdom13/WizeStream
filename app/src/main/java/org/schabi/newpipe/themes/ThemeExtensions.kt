package org.schabi.newpipe.themes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject

/** Portable raster assets only: no URLs, resource names, vectors, fonts or executable content. */
data class ThemeAsset(val base64: String, val width: Int, val height: Int) {
    val bitmap: Bitmap by lazy {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}

data class ThemeGlass(val blur: Int = 8, val tint: Int = 80, val radius: Int = 16)
data class ThemePlayer(val layout: String = "standard", val background: String? = null, val artwork: String? = null)

internal data class ThemeExtensions(
    val glass: ThemeGlass?,
    val player: ThemePlayer,
    val skin: String?,
    val icons: Map<String, String>,
    val assets: Map<String, ThemeAsset>
) {
    companion object {
        val iconSlots = setOf("play", "pause", "replay", "previous", "next", "shuffle", "repeatOff", "repeatOne", "repeatAll")
        val layouts = setOf("standard", "vinyl", "pocket", "cassette")
        private val identifier = Regex("[a-z][a-z0-9_-]{0,31}")

        fun parse(json: JSONObject): ThemeExtensions {
            fun objectOrEmpty(key: String): JSONObject = if (json.has(key)) {
                json.get(key) as? JSONObject
                    ?: error("Expected object: $key")
            } else {
                JSONObject()
            }
            fun JSONObject.keysOnly(allowed: Set<String>) {
                require(keys().asSequence().all { it in allowed }) { "Unknown extension field" }
            }
            fun JSONObject.text(key: String): String? = if (has(key)) {
                get(key) as? String
                    ?: error("Expected text: $key")
            } else {
                null
            }
            fun JSONObject.number(key: String, default: Int, range: IntRange): Int {
                val value = if (has(key)) get(key) as? Int ?: error("Expected integer: $key") else default
                require(value in range) { "Out of range: $key" }
                return value
            }
            val assetsJson = objectOrEmpty("assets")
            require(assetsJson.length() <= 6) { "At most six assets" }
            var pixels = 0L
            val assets = assetsJson.keys().asSequence().associateWith { key ->
                require(identifier.matches(key)) { "Invalid asset ID" }
                val encoded = assetsJson.text(key)!!
                require(encoded.length <= 128 * 1024 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) { "Invalid base64 PNG" }
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                require(bytes.size in 33..96 * 1024 && Base64.encodeToString(bytes, Base64.NO_WRAP) == encoded)
                require(bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)) { "Only PNG assets are supported" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outMimeType == "image/png" && bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
                pixels += bounds.outWidth.toLong() * bounds.outHeight
                require(pixels <= 1024 * 1024) { "Asset pixel budget exceeded" }
                // Fully decode before accepting a pack, never first discover corruption in the UI.
                requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).recycle()
                ThemeAsset(encoded, bounds.outWidth, bounds.outHeight)
            }
            fun reference(value: String?): String? = value?.also { require(it in assets) { "Missing asset: $it" } }
            val iconsJson = objectOrEmpty("icons").apply { keysOnly(iconSlots) }
            val icons = iconsJson.keys().asSequence().associateWith { key ->
                reference(iconsJson.text(key))!!.also {
                    require(assets.getValue(it).let { asset -> asset.width <= 256 && asset.height <= 256 }) { "Icons must fit 256 by 256" }
                }
            }
            val playerJson = objectOrEmpty("player").apply { keysOnly(setOf("layout", "background", "artwork")) }
            val player = ThemePlayer(
                (playerJson.text("layout") ?: "standard").also { require(it in layouts) },
                reference(playerJson.text("background")),
                reference(playerJson.text("artwork"))
            )
            val glass = if (json.has("glass")) {
                objectOrEmpty("glass").let {
                    it.keysOnly(setOf("blur", "tint", "radius"))
                    ThemeGlass(it.number("blur", 8, 0..16), it.number("tint", 80, 60..95), it.number("radius", 16, 0..32))
                }
            } else {
                null
            }
            return ThemeExtensions(glass, player, reference(json.text("skin")), icons, assets)
        }
    }
}
