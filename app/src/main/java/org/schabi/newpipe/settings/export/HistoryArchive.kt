package org.schabi.newpipe.settings.export

import java.io.InputStream
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.stream.StreamType

@Serializable
internal data class HistoryArchive(
    val format: String = "wizestream-history",
    val version: Int = 1,
    val watches: List<ArchivedWatch> = emptyList(),
    val searches: List<ArchivedSearch> = emptyList()
)

@Serializable
internal data class ArchivedWatch(
    val serviceId: Int,
    val url: String,
    val title: String,
    val streamType: String,
    val duration: Long,
    val uploader: String,
    val timestamp: Long,
    val repeatCount: Long,
    val thumbnailUrl: String? = null,
    val sourceType: String = "REMOTE",
    val mimeType: String? = null
)

@Serializable
internal data class ArchivedSearch(val serviceId: Int, val query: String, val timestamp: Long)

internal object HistoryArchiveCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val json = Json { encodeDefaults = true }

    fun encode(archive: HistoryArchive): String {
        validate(archive)
        return json.encodeToString(archive)
    }

    fun decode(input: InputStream): HistoryArchive {
        val bytes = input.readBytesLimited()
        val text = bytes.toString(Charsets.UTF_8)
        val root = json.parseToJsonElement(text)
        require(root is JsonObject && root["format"]?.jsonPrimitive?.content == "wizestream-history" && root.containsKey("version")) { "Not a WizeStream history archive" }
        return json.decodeFromString<HistoryArchive>(text).also(::validate)
    }

    private fun InputStream.readBytesLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "History file exceeds 32 MiB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun validate(archive: HistoryArchive) {
        require(archive.format == "wizestream-history" && archive.version == 1) { "Unsupported history format" }
        require(archive.watches.size + archive.searches.size <= 100_000) { "Too many history entries" }
        archive.watches.forEach {
            require(it.url.isNotBlank() && it.url.length <= 8192 && it.title.length <= 20_000 && it.uploader.length <= 20_000)
            require(it.duration >= -1 && it.repeatCount in 1..1_000_000 && it.timestamp in 0..253402300799999)
            require(it.sourceType in listOf("LOCAL", "REMOTE"))
            require(it.serviceId >= 0 || it.sourceType == "LOCAL")
            require(it.thumbnailUrl == null || it.thumbnailUrl.length <= 8192)
            require(it.mimeType == null || it.mimeType.length <= 256)
            require(StreamType.valueOf(it.streamType) != StreamType.NONE)
        }
        archive.searches.forEach {
            require(it.serviceId >= 0)
            require(it.query.isNotBlank() && it.query.length <= 20_000 && it.timestamp in 0..253402300799999)
        }
    }

    fun csv(archive: HistoryArchive): String = buildString {
        append("kind,service_id,url,title_or_query,timestamp_utc,repeat_count\r\n")
        fun row(values: List<String>) {
            append(
                values.joinToString(",") { value ->
                    // Spreadsheet applications must treat imported titles and queries as text.
                    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
                    "\"${safe.replace("\"", "\"\"")}\""
                }
            ).append("\r\n")
        }
        archive.watches.forEach { row(listOf("watch", it.serviceId.toString(), it.url, it.title, Instant.ofEpochMilli(it.timestamp).toString(), it.repeatCount.toString())) }
        archive.searches.forEach { row(listOf("search", it.serviceId.toString(), "", it.query, Instant.ofEpochMilli(it.timestamp).toString(), "")) }
    }
}
