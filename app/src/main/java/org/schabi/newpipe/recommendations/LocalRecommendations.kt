package org.schabi.newpipe.recommendations

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ContentBlockingHelper

/** Bounded, device-local model. No history import, network requests, or sync serialization. */
object LocalRecommendations {
    const val PREFERENCES = "local_recommendations"
    private const val MAX_CANDIDATES = 200
    private const val MAX_TERMS = 200

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private fun profile(context: Context) = ProfileManager.getActiveProfileId(context)
    private fun key(context: Context, setting: String) = "${profile(context)}:$setting"

    @JvmStatic
    fun setting(context: Context, name: String): Boolean = prefs(context).getBoolean(key(context, name), false)

    @JvmStatic
    fun enabled(context: Context): Boolean = setting(context, "enabled") && !IncognitoMode.isEnabled(context)

    @JvmStatic
    fun configurationToken(context: Context): String {
        val prefix = "${profile(context)}:"
        return prefix + prefs(context).all.filterKeys { it.startsWith(prefix) && it != prefix + "model" }.hashCode() + IncognitoMode.isEnabled(context)
    }

    private fun learns(context: Context): Boolean = enabled(context) && !setting(context, "paused")

    @JvmStatic
    @Synchronized
    fun configure(context: Context, values: Map<String, Boolean>, excludedTopics: String) {
        val editor = prefs(context).edit()
        values.forEach { (name, value) -> editor.putBoolean(key(context, name), value) }
        editor.putString(key(context, "topics"), excludedTopics.take(4000)).apply()
    }

    @JvmStatic
    fun topics(context: Context): String = prefs(context).getString(key(context, "topics"), "").orEmpty()

    @JvmStatic
    @Synchronized
    fun reset(context: Context) {
        prefs(context).edit().remove(key(context, "model")).remove(key(context, "excluded"))
            .remove(key(context, "topics")).putLong(key(context, "generation"), System.nanoTime()).apply()
    }

    @JvmStatic
    @Synchronized
    fun exclude(context: Context, item: StreamInfoItem) {
        if (!enabled(context)) return
        val entries = prefs(context).getStringSet(key(context, "excluded"), emptySet()).orEmpty().toMutableSet()
        entries.add(id(item))
        prefs(context).edit().putStringSet(key(context, "excluded"), entries).apply()
        val model = read(context)
        model.candidates.remove(id(item))
        write(context, model)
    }

    @JvmStatic
    @Synchronized
    fun observe(context: Context, items: List<InfoItem>) {
        if (!learns(context)) return
        val model = read(context)
        val isEligible = eligibility(context)
        items.filterIsInstance<StreamInfoItem>().take(MAX_CANDIDATES).forEach { item ->
            if (isEligible(item) && id(item) !in model.watched) {
                model.candidates.remove(id(item))
                model.candidates[id(item)] = Candidate.from(item)
            }
        }
        write(context, model)
    }

    @JvmStatic
    @Synchronized
    fun searched(context: Context, serviceId: Int, query: String, profileId: String) {
        if (profileId != profile(context) || !learns(context) ||
            !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.enable_search_history_key), true)
        ) {
            return
        }
        val model = read(context, profileId)
        addTerms(model, serviceId, query, 2.0)
        write(context, model, profileId)
    }

    @JvmStatic
    @Synchronized
    fun watched(context: Context, info: StreamInfo, seconds: Long) {
        if (!learns(context) || !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_watch_history_key), true)
        ) {
            return
        }
        val model = read(context)
        val item = StreamInfoItem(info.serviceId, info.url, info.name, info.streamType).apply {
            uploaderName = info.uploaderName
        }
        if (!eligibility(context)(item)) return
        val streamId = id(item)
        model.watched.remove(streamId)
        model.watched.add(streamId)
        model.candidates.remove(streamId)
        addTerms(model, info.serviceId, "${info.name} ${info.uploaderName}", if (seconds == 0L) 1.0 else seconds.coerceIn(0, 60) / 30.0)
        write(context, model)
    }

    /** Returns a fresh list; extractor/cache results are never reordered in place. */
    @JvmStatic
    @Synchronized
    fun rank(context: Context, items: List<StreamInfoItem>): List<StreamInfoItem> {
        if (!enabled(context)) return items.toList()
        val model = read(context)
        val isEligible = eligibility(context)
        return items.filter { isEligible(it) && id(it) !in model.watched }
            .distinctBy(::id).sortedByDescending { score(model.weights, it.serviceId, "${it.name} ${it.uploaderName}") }
    }

    @JvmStatic
    @Synchronized
    fun feed(context: Context, serviceId: Int): List<StreamInfoItem> {
        if (!enabled(context)) return emptyList()
        return rank(context, read(context).candidates.values.filter { it.service == serviceId }.map { it.item() })
    }

    @JvmStatic
    fun shorts(context: Context, items: List<StreamInfoItem>): List<StreamInfoItem> = if (enabled(context) && setting(context, "shorts")) rank(context, items) else items

    @JvmStatic
    fun autoplay(context: Context, items: List<InfoItem>, serviceId: Int, shorts: Boolean, queuedUrls: Set<String>): StreamInfoItem? {
        if (!enabled(context) || !setting(context, "autoplay")) return null
        val rules = ContentBlockingHelper.getRules(context, ContentBlockingHelper.Target.RELATED_ITEMS)
        return rank(context, items.filterIsInstance<StreamInfoItem>().filterNot(rules::isBlocked))
            .firstOrNull { it.serviceId == serviceId && (!shorts || it.isShortFormContent) && it.url !in queuedUrls }
    }

    private fun eligibility(context: Context): (StreamInfoItem) -> Boolean {
        val excluded = prefs(context).getStringSet(key(context, "excluded"), emptySet()).orEmpty()
        val hidden = topics(context).split(',', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        val rules = ContentBlockingHelper.getRules(context, ContentBlockingHelper.Target.KIOSKS)
        return { item ->
            val text = "${item.name} ${item.uploaderName}".lowercase(Locale.ROOT)
            item.url.length <= 2048 && (item.url.startsWith("https://") || item.url.startsWith("http://")) &&
                !item.requiresMembership() && item.streamType in listOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM) &&
                id(item) !in excluded && hidden.none(text::contains) && !rules.isBlocked(item)
        }
    }

    internal fun tokens(text: String): Set<String> = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length in 3..40 }.take(40).toSet()

    internal fun score(weights: Map<String, Double>, service: Int, text: String): Double = tokens(text).sumOf { weights["$service:$it"] ?: 0.0 }

    private fun addTerms(model: Model, service: Int, text: String, amount: Double) {
        model.weights.replaceAll { _, value -> value * 0.995 }
        tokens(text).forEach { token ->
            val key = "$service:$token"
            model.weights[key] = ((model.weights[key] ?: 0.0) + amount).coerceAtMost(100.0)
        }
    }

    private fun id(item: StreamInfoItem) = "${item.serviceId}:${item.url}"
    private class Model(
        val candidates: LinkedHashMap<String, Candidate> = linkedMapOf(),
        val weights: MutableMap<String, Double> = linkedMapOf(),
        val watched: MutableSet<String> = linkedSetOf()
    )

    private data class Candidate(val service: Int, val url: String, val name: String, val uploader: String, val channel: String, val thumbnail: String, val duration: Long, val short: Boolean, val audio: Boolean) {
        fun item() = StreamInfoItem(service, url, name, if (audio) StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM).also {
            it.uploaderName = uploader
            it.uploaderUrl = channel
            it.thumbnailUrl = thumbnail
            it.duration = duration
            it.isShortFormContent = short
        }
        fun json() = JSONObject().put("service", service).put("url", url).put("name", name).put("uploader", uploader)
            .put("channel", channel).put("thumbnail", thumbnail).put("duration", duration).put("short", short).put("audio", audio)
        companion object {
            fun from(item: StreamInfoItem) = Candidate(item.serviceId, item.url.take(2048), item.name.take(300), item.uploaderName.orEmpty().take(200), item.uploaderUrl.orEmpty().take(2048), item.thumbnailUrl.orEmpty().take(2048), item.duration, item.isShortFormContent, item.streamType == StreamType.AUDIO_STREAM)
            fun parse(json: JSONObject) = Candidate(json.getInt("service"), json.getString("url"), json.getString("name"), json.optString("uploader"), json.optString("channel"), json.optString("thumbnail"), json.optLong("duration", -1), json.optBoolean("short"), json.optBoolean("audio"))
        }
    }

    private fun read(context: Context, profileId: String = profile(context)): Model = try {
        val json = JSONObject(prefs(context).getString("$profileId:model", "{}").orEmpty())
        val model = Model()
        json.optJSONArray("candidates")?.let { array ->
            for (i in 0 until minOf(array.length(), MAX_CANDIDATES)) {
                val candidate = Candidate.parse(array.getJSONObject(i))
                model.candidates["${candidate.service}:${candidate.url}"] = candidate
            }
        }
        json.optJSONObject("weights")?.let { weights ->
            weights.keys().asSequence().take(MAX_TERMS).forEach { model.weights[it] = weights.optDouble(it, 0.0) }
        }
        json.optJSONArray("watched")?.let { array ->
            for (i in 0 until minOf(array.length(), MAX_CANDIDATES)) model.watched.add(array.getString(i))
        }
        model
    } catch (_: Exception) {
        Model()
    }

    private fun write(context: Context, model: Model, profileId: String = profile(context)) {
        val candidates = JSONArray(model.candidates.values.toList().takeLast(MAX_CANDIDATES).map { it.json() })
        val weights = model.weights.entries.sortedByDescending { it.value }.take(MAX_TERMS).associate { it.key to it.value }
        prefs(context).edit().putString(
            key(context, "model"),
            JSONObject().put("candidates", candidates)
                .put("weights", JSONObject(weights)).put("watched", JSONArray(model.watched.toList().takeLast(MAX_CANDIDATES))).toString()
        ).apply()
    }
}
