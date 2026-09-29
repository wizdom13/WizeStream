package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException

object OdyseeApi {
    @JvmStatic
    fun rpc(method: String, params: JsonObject): JsonObject {
        val body = """{"jsonrpc":"2.0","method":"$method","params":$params,"id":1}"""
        val response = NewPipe.getDownloader().post(
            OdyseeConstants.SDK_PROXY,
            mapOf("Content-Type" to listOf("application/json")),
            body.toByteArray()
        )
        if (response.responseCode() !in 200..299) {
            throw ExtractionException("Odysee API returned HTTP ${response.responseCode()}")
        }
        return JsonParser.`object`().from(response.responseBody())
    }

    @JvmStatic
    fun webUrl(uri: String): String {
        val value = uri.removePrefix("lbry://")
        return OdyseeConstants.BASE_URL + "/" + value.replace("#", ":")
    }

    @JvmStatic
    fun lbryUriFromWebUrl(url: String): String {
        val path = java.net.URI(url).path.trim('/')
        require(path.isNotBlank()) { "Invalid Odysee URL" }
        return "lbry://" + path.replace(":", "#")
    }
}
