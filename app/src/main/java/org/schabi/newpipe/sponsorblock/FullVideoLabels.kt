/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sponsorblock

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException
import java.security.MessageDigest
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

internal enum class FullVideoLabel(@StringRes val text: Int) {
    SPONSOR(R.string.full_video_label_sponsor),
    EXCLUSIVE_ACCESS(R.string.full_video_label_exclusive),
    SELF_PROMOTION(R.string.full_video_label_self_promotion)
}

/** Informational disclosures never cause the entire video to be skipped. */
internal class FullVideoLabelRepository(
    private val fetch: (String) -> String?,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    private data class Cached(val value: Optional<FullVideoLabel>, val expires: Long)
    private val cache = LruCache<String, Cached>(256)
    private val requests = mutableMapOf<String, Single<Optional<FullVideoLabel>>>()

    @Synchronized
    fun get(id: String): Single<Optional<FullVideoLabel>> {
        if (!VIDEO_ID.matches(id)) return Single.just(Optional.empty())
        cache.get(id)?.takeIf { it.expires > now() }?.let { return Single.just(it.value) }
        requests[id]?.let { return it }
        val request = Single.fromCallable {
            var lifetime = TimeUnit.HOURS.toMillis(6)
            val label = try {
                fetch(hashPrefix(id))?.let { parse(it, id) }
            } catch (_: Exception) {
                lifetime = TimeUnit.MINUTES.toMillis(1)
                null
            }
            Optional.ofNullable(label).also { value ->
                synchronized(this) { cache.put(id, Cached(value, now() + lifetime)) }
            }
        }.subscribeOn(WORKER).doFinally { synchronized(this) { requests.remove(id) } }
            .toObservable().replay(1).refCount().firstOrError()
        requests[id] = request
        return request
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val WORKER = Schedulers.from(Executors.newFixedThreadPool(2) { task -> Thread(task, "SponsorBlockLabels").apply { isDaemon = true } })

        internal fun hashPrefix(id: String): String = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8)).take(3).joinToString("") { "%02x".format(it) }.take(5)

        internal fun parse(json: String, id: String): FullVideoLabel? {
            require(json.length <= 1024 * 1024)
            val categories = JsonParser.array().from(json).filterIsInstance<JsonObject>()
                .filter { it.getString("videoID") == id }
                .flatMap { it.getArray("segments").orEmpty().filterIsInstance<JsonObject>() }
                .map { it.getString("category") }.toSet()
            return when {
                "sponsor" in categories -> FullVideoLabel.SPONSOR
                "exclusive_access" in categories -> FullVideoLabel.EXCLUSIVE_ACCESS
                "selfpromo" in categories -> FullVideoLabel.SELF_PROMOTION
                else -> null
            }
        }
    }
}

internal object FullVideoLabels {
    private val repository = FullVideoLabelRepository(
        fetch = { prefix ->
            val call = DownloaderImpl.getInstance().client.newCall(Request.Builder().url("https://sponsor.ajay.app/api/videoLabels/$prefix").build())
            call.timeout().timeout(10, TimeUnit.SECONDS)
            call.execute().use { response ->
                if (response.code == 404) {
                    null
                } else {
                    if (!response.isSuccessful) throw IOException("Label lookup failed")
                    val body = response.body ?: throw IOException("Empty label response")
                    if (body.contentLength() > 1024 * 1024 || body.source().request(1024 * 1024 + 1L)) throw IOException("Label response too large")
                    body.string()
                }
            }
        }
    )

    fun enabled(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getBoolean(context.getString(R.string.sponsor_block_enable_key), false) &&
            preferences.getBoolean("sponsor_block_full_video_labels", true)
    }

    fun get(context: Context, serviceId: Int, url: String): Single<Optional<FullVideoLabel>> {
        if (!enabled(context) || serviceId != ServiceList.YouTube.serviceId) return Single.just(Optional.empty())
        val id = try {
            ServiceList.YouTube.streamLHFactory.getId(url)
        } catch (_: Exception) {
            return Single.just(Optional.empty())
        }
        return repository.get(id)
    }
}
