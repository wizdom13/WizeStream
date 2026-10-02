package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.net.URI
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException

object OdyseeApi {
    @JvmStatic
    fun rpc(method: String, params: JsonObject): JsonObject {
        val body = JsonWriter.string(
            JsonObject().apply {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
                put("id", 1)
            }
        )
        val response = NewPipe.getDownloader().post(
            OdyseeConstants.SDK_PROXY,
            mapOf("Content-Type" to listOf("application/json")),
            body.toByteArray()
        )
        if (response.responseCode() !in 200..299) {
            throw ExtractionException("Odysee API returned HTTP ${response.responseCode()}")
        }
        val payload = try {
            JsonParser.`object`().from(response.responseBody())
        } catch (error: Exception) {
            throw ExtractionException("Invalid Odysee API response", error)
        }
        if (payload.has("error") && !payload.isNull("error")) {
            throw ExtractionException("Odysee API rejected $method: " + payload.getObject("error").getString("message", "Unknown error"))
        }
        if (!payload.has("result") || payload.isNull("result")) {
            throw ExtractionException("Odysee API response has no result for $method")
        }
        return payload
    }

    @JvmStatic
    fun webUrl(uri: String): String {
        val value = uri.removePrefix("lbry://")
        return OdyseeConstants.BASE_URL + "/" + value.replace("#", ":")
    }

    @JvmStatic
    fun lbryUriFromWebUrl(url: String): String {
        if (url.startsWith("lbry://", ignoreCase = true)) {
            return validateClaimUri(url.substring(7).trimEnd('/'))
        }
        val uri = URI(url)
        // Service detection also receives search queries. A relative URI such as "Astrum"
        // has a path, but must not turn a search into Odysee playback.
        require(
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                (uri.host.equals("odysee.com", ignoreCase = true) || uri.host.equals("www.odysee.com", ignoreCase = true)) &&
                uri.userInfo == null
        ) { "Not an Odysee web URL" }
        val path = uri.path.orEmpty().trim('/')
        require(path.isNotBlank()) { "Invalid Odysee URL" }
        return validateClaimUri(path.replace(":", "#"))
    }
    private fun validateClaimUri(path: String): String {
        val parts = path.split('/')
        require(parts.size in 1..2 && (parts.size == 1 || parts[0].startsWith("@"))) { "Invalid Odysee claim path" }
        require(parts.all { it.matches(Regex("@?[^/#:\\s?@]+(?:#[0-9a-fA-F]+)?")) }) { "Invalid Odysee claim identifier" }
        return "lbry://$path"
    }

    fun resolveClaim(uri: String): JsonObject {
        val claim = rpc("resolve", JsonObject().apply { put("urls", listOf(uri)) })
            .getObject("result").getObject(uri)
        if (claim.has("error") || claim.getString("claim_id", "").isBlank() || !claim.has("value")) {
            throw ExtractionException("Odysee claim is unavailable")
        }
        return claim
    }
}
