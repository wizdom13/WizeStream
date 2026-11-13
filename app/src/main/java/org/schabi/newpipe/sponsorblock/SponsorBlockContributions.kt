/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sponsorblock

import android.content.Context
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.download.DownloadedStreamInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

internal data class ContributionSegment(val uuid: String, val category: String, val start: Double, val end: Double)
internal data class ContributionDraft(val category: String, val start: Double, val end: Double, val duration: Double) {
    fun validate() {
        require(category in SponsorBlockContributions.CATEGORIES)
        require(duration.isFinite() && duration > 0 && start.isFinite() && end.isFinite())
        require(start >= 0 && start < duration && end <= duration)
        require(if (category == "poi_highlight") end == start else end > start)
    }
}

/** Only explicit user actions call the write endpoints; private identity stays outside app backups. */
internal object SponsorBlockContributions {
    val CATEGORIES = listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic", "preview", "filler", "poi_highlight")
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val ID = Regex("[A-Za-z0-9_-]{30,128}")
    private val VIDEO = Regex("[A-Za-z0-9_-]{11}")
    private val SEGMENT = Regex("[A-Fa-f0-9]{32,64}")

    fun enabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sponsor_block_contributions", false)

    fun eligible(context: Context, info: StreamInfo?): Boolean = enabled(context) && info != null &&
        info !is DownloadedStreamInfo &&
        info.serviceId == ServiceList.YouTube.serviceId && info.duration > 0 &&
        info.streamType in setOf(StreamType.VIDEO_STREAM, StreamType.AUDIO_STREAM, StreamType.POST_LIVE_STREAM, StreamType.POST_LIVE_AUDIO_STREAM) &&
        !info.url.contains("wizestream-segments=") && runCatching { videoId(info) }.isSuccess

    @Synchronized
    fun identity(context: Context): String {
        val preferences = context.getSharedPreferences("sponsorblock-contribution-identity", Context.MODE_PRIVATE)
        return preferences.getString("user_id", null)?.takeIf(::validIdentity) ?: (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").also {
            check(preferences.edit().putString("user_id", it).commit())
        }
    }

    fun validIdentity(value: String): Boolean = ID.matches(value)

    fun setIdentity(context: Context, value: String): Boolean = validIdentity(value) && context.getSharedPreferences("sponsorblock-contribution-identity", Context.MODE_PRIVATE)
        .edit().putString("user_id", value).commit()

    fun videoId(info: StreamInfo): String = ServiceList.YouTube.streamLHFactory.getId(info.url).also { require(VIDEO.matches(it)) }

    fun submit(videoId: String, userId: String, draft: ContributionDraft): Single<Unit> = execute(submitRequest(videoId, userId, draft)).map { response ->
        // Successful submissions return their UUIDs. A generic HTTP 200 response alone
        // can also represent a rejected/filtered request and must not be called success.
        check(JsonParser.array().from(response).filterIsInstance<JsonObject>().any { SEGMENT.matches(it.getString("UUID", "")) })
    }

    fun vote(uuid: String, userId: String, score: Int): Single<Unit> = execute(voteRequest(uuid, userId, score)).map { Unit }

    fun segments(videoId: String): Single<List<ContributionSegment>> {
        require(VIDEO.matches(videoId))
        val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray()).take(2).joinToString("") { "%02x".format(it) }
        val categories = JsonWriter.string().array(CATEGORIES).done()
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("sponsor.ajay.app").addPathSegments("api/skipSegments/$prefix")
            .addQueryParameter("categories", categories).addQueryParameter("actionTypes", "[\"skip\",\"poi\"]").build()
        return execute(Request.Builder().url(url).build(), allowMissing = true).map { json -> parseSegments(json, videoId) }
    }

    internal fun submitRequest(videoId: String, userId: String, draft: ContributionDraft): Request {
        require(VIDEO.matches(videoId) && validIdentity(userId))
        draft.validate()
        val segment = JsonObject().apply {
            put(
                "segment",
                JsonArray().apply {
                    add(draft.start)
                    add(draft.end)
                }
            )
            put("category", draft.category)
            put("actionType", if (draft.category == "poi_highlight") "poi" else "skip")
            put("description", "")
        }
        val payload = JsonObject().apply {
            put("videoID", videoId)
            put("userID", userId)
            put("userAgent", "WizeStream")
            put("videoDuration", draft.duration)
            put("segments", JsonArray().apply { add(segment) })
        }
        return Request.Builder().url("https://sponsor.ajay.app/api/skipSegments")
            .post(JsonWriter.string().`object`(payload).done().toRequestBody(JSON)).build()
    }

    internal fun voteRequest(uuid: String, userId: String, score: Int): Request {
        require(SEGMENT.matches(uuid) && validIdentity(userId) && score in setOf(0, 1, 20))
        // The current vote API accepts identity through its documented query parameters.
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("sponsor.ajay.app").addPathSegments("api/voteOnSponsorTime")
            .addQueryParameter("UUID", uuid).addQueryParameter("userID", userId).addQueryParameter("type", score.toString()).build()
        return Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
    }

    internal fun parseSegments(json: String, videoId: String): List<ContributionSegment> = JsonParser.array().from(json)
        .filterIsInstance<JsonObject>().filter { it.getString("videoID") == videoId }
        .flatMap { it.getArray("segments").orEmpty().filterIsInstance<JsonObject>() }
        .mapNotNull { segment ->
            val uuid = segment.getString("UUID", "")
            val category = segment.getString("category", "")
            val times = segment.getArray("segment") ?: return@mapNotNull null
            if (!SEGMENT.matches(uuid) || category !in CATEGORIES || times.size != 2) return@mapNotNull null
            val start = times.getDouble(0)
            val end = times.getDouble(1)
            if (!start.isFinite() || !end.isFinite() || start < 0 || end < start) return@mapNotNull null
            ContributionSegment(uuid, category, start, end)
        }.take(100)

    private fun execute(request: Request, allowMissing: Boolean = false): Single<String> = Single.create { emitter ->
        // Never replay public submissions automatically or forward an identity on redirects.
        val client = DownloaderImpl.getInstance().client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        val call = client.newCall(request)
        call.timeout().timeout(15, TimeUnit.SECONDS)
        emitter.setCancellable(call::cancel)
        try {
            call.execute().use { response ->
                if (allowMissing && response.code == 404) {
                    if (!emitter.isDisposed) emitter.onSuccess("[]")
                } else {
                    if (!response.isSuccessful) throw IOException("SponsorBlock HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty SponsorBlock response")
                    if (body.contentLength() > 1024 * 1024 || body.source().request(1024 * 1024 + 1L)) throw IOException("SponsorBlock response too large")
                    if (!emitter.isDisposed) emitter.onSuccess(body.string())
                }
            }
        } catch (error: Exception) {
            emitter.tryOnError(error)
        }
    }
}
