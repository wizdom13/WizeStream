package org.schabi.newpipe.extractor.services.youtube

import com.grack.nanojson.JsonObject

/** Projection is explicit metadata, never inferred from titles or a video's aspect ratio. */
object YoutubeProjection {
    @JvmStatic
    fun isSpherical(vararg streamingData: JsonObject?): Boolean = streamingData.filterNotNull().any { data ->
        listOf("formats", "adaptiveFormats").any { key ->
            data.getArray(key)?.filterIsInstance<JsonObject>()?.any { format ->
                format.getString("projectionType") in setOf("EQUIRECTANGULAR", "MESH")
            } == true
        }
    }
}
