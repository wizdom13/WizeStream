package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment

/** App-private metadata for intact downloads. Playback never fetches SponsorBlock over the network. */
object OfflineSponsorBlockStore {
    private const val FILE = "offline_sponsorblock"

    @JvmStatic
    fun save(context: Context, uri: String, length: Long, segments: Array<SponsorBlockSegment>?) {
        val values = JSONArray()
        segments.orEmpty().take(1000).filter { valid(it) }.forEach { segment ->
            values.put(
                JSONObject().put("id", segment.uuid).put("start", segment.startTime)
                    .put("end", segment.endTime).put("category", segment.category.name)
                    .put("action", segment.action.name).put("service", segment.serviceId)
            )
        }
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val identity = fileIdentity(context, uri) ?: return
        val editor = prefs.edit()
        prefs.getString(uri, null)?.let { editor.remove(it) }
        if (length <= 0 || values.length() == 0) {
            editor.remove(uri).remove(identity.first).apply()
        } else {
            val encoded = JSONObject().put("length", length).put("segments", values).toString()
            editor.putString(uri, identity.first).putString(identity.first, encoded).apply()
        }
    }

    @JvmStatic
    fun load(context: Context, uri: String): List<SponsorBlockSegment> = runCatching {
        val identity = fileIdentity(context, uri) ?: return emptyList()
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val saved = prefs.getString(identity.first, null) ?: return emptyList()
        val json = JSONObject(saved)
        if (identity.second <= 0 || identity.second != json.getLong("length")) return emptyList()
        val values = json.getJSONArray("segments")
        (0 until minOf(values.length(), 1000)).mapNotNull { index ->
            runCatching {
                val item = values.getJSONObject(index)
                SponsorBlockSegment(
                    item.optString("id"),
                    item.getDouble("start"),
                    item.getDouble("end"),
                    SponsorBlockCategory.valueOf(item.getString("category")),
                    SponsorBlockAction.valueOf(item.getString("action")),
                    item.getInt("service")
                )
            }.getOrNull()?.takeIf { valid(it) }
        }
    }.getOrDefault(emptyList())

    @JvmStatic
    fun remove(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val editor = prefs.edit().remove(uri)
        prefs.getString(uri, null)?.let { editor.remove(it) }
        fileIdentity(context, uri)?.let { editor.remove(it.first) }
        editor.apply()
    }

    // A downloaded file may be opened through SAF, MediaStore, or our FileProvider.
    // Descriptor identity follows the same file across those URI aliases without title matching.
    private fun fileIdentity(context: Context, uri: String): Pair<String, Long>? = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
            val stat = Os.fstat(descriptor.fileDescriptor)
            "file:${stat.st_dev}:${stat.st_ino}:${stat.st_mtime}:${stat.st_size}" to stat.st_size
        }
    }.getOrNull()

    private fun valid(segment: SponsorBlockSegment): Boolean = segment.startTime.isFinite() &&
        segment.endTime.isFinite() && segment.startTime >= 0 && segment.endTime > segment.startTime &&
        segment.category != null && segment.action != null
}
