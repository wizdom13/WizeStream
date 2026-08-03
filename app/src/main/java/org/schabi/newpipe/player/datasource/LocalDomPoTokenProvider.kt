package org.schabi.newpipe.player.datasource

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.HashMap
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrProtocolException
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState

internal fun youtubeCredentialIdentity(loggedIn: Boolean, tokens: String?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(if (loggedIn) 1.toByte() else 0.toByte())
    if (loggedIn) {
        digest.update(0.toByte())
        digest.update(tokens.orEmpty().toByteArray(StandardCharsets.UTF_8))
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
}

internal class CredentialIdentityTracker(private val onChanged: () -> Unit) {
    private var observedIdentity: String? = null

    @Synchronized
    fun observe(identity: String) {
        val previous = observedIdentity
        if (previous != null && previous != identity) {
            onChanged()
        }
        observedIdentity = identity
    }
}

class LocalDomPoTokenProvider(context: Context) :
    SabrPoTokenProvider,
    YoutubeSessionPoTokenProvider {
    private data class CachedToken(
        val token: ByteArray,
        val mintedAtMs: Long,
        val visitorData: String,
        val credentialIdentity: String,
        val clientContextIdentity: String
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<String, CachedToken>()
    private val mintLocks = ConcurrentHashMap<String, Any>()
    private val generatorLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generatorContext: LocalDomPoTokenContext? = null
    private var generatorCredentialIdentity: String? = null
    private var generator: LocalDomPoTokenGenerator? = null
    private val visitorDataLock = Any()
    private var fetchedVisitorData: String? = null
    private var fetchedVisitorDataLoggedIn: Boolean? = null
    private var fetchedVisitorDataCredentialIdentity: String? = null
    private var visitorDataFetchedAtMs: Long = 0
    private val credentialIdentityTracker = CredentialIdentityTracker(
        onChanged = ::invalidateCredentialBoundState
    )
    private val prewarmExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YoutubeSessionPoTokenPrewarm").apply { isDaemon = true }
    }
    private val sessionPoTokenPrewarmer = ContextBoundSingleFlight<
        YoutubeSessionPoTokenPrewarmContext,
        PreparedYoutubeSessionPoToken
        >(prewarmExecutor)

    override fun getSessionPoToken(
        clientName: String,
        clientVersion: String,
        userAgent: String?,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean
    ): YoutubeSessionPoToken? {
        if (clientName.isBlank() || clientVersion.isBlank() || userAgent.isNullOrBlank()) {
            return null
        }
        val credentialIdentity = currentCredentialIdentity(loggedIn)
        credentialIdentityTracker.observe(credentialIdentity)
        val requestContext = YoutubeSessionPoTokenContext(
            clientName,
            clientVersion,
            userAgent,
            localization,
            contentCountry,
            loggedIn,
            credentialIdentity
        )
        sessionPoTokenPrewarmer.inFlight(requestContext.prewarmContext())?.let {
            val prepared = awaitSessionPoTokenPrewarm(it)
            if (prepared.context == requestContext) {
                return prepared.token
            }
        }
        return getSessionPoTokenNow(requestContext)
    }

    fun prewarmSessionPoToken(
        clientName: String,
        userAgent: String?,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
        clientVersionResolver: Callable<String>
    ) {
        val credentialIdentity = currentCredentialIdentity(loggedIn)
        credentialIdentityTracker.observe(credentialIdentity)
        val prewarmContext = YoutubeSessionPoTokenPrewarmContext(
            clientName,
            userAgent,
            localization,
            contentCountry,
            loggedIn,
            credentialIdentity
        )
        sessionPoTokenPrewarmer.start(prewarmContext) {
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                val requestContext = YoutubeSessionPoTokenContext(
                    clientName,
                    clientVersionResolver.call(),
                    userAgent,
                    localization,
                    contentCountry,
                    loggedIn,
                    credentialIdentity
                )
                PreparedYoutubeSessionPoToken(
                    requestContext,
                    getSessionPoTokenNow(requestContext)
                ).also {
                    Log.i(
                        TAG,
                        "session token prewarm ready client=$clientName in " +
                            "${SystemClock.elapsedRealtime() - startedAtMs}ms"
                    )
                }
            } catch (error: Throwable) {
                Log.w(TAG, "session token prewarm failed client=$clientName", error)
                throw error
            }
        }
    }

    fun cancelSessionPoTokenPrewarm() {
        sessionPoTokenPrewarmer.cancel()
    }

    private fun awaitSessionPoTokenPrewarm(
        prewarm: Future<PreparedYoutubeSessionPoToken>
    ): PreparedYoutubeSessionPoToken {
        try {
            return prewarm.get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SabrProtocolException(
                "Interrupted waiting for session PO token prewarm",
                error
            )
        } catch (error: CancellationException) {
            throw SabrProtocolException("Session PO token prewarm was invalidated", error)
        } catch (error: ExecutionException) {
            throw SabrProtocolException(
                "Session PO token prewarm failed",
                error.cause ?: error
            )
        }
    }

    private fun getSessionPoTokenNow(
        requestContext: YoutubeSessionPoTokenContext
    ): YoutubeSessionPoToken {
        if (!credentialsStillMatch(requestContext.credentialIdentity)) {
            throw SabrProtocolException(
                "YouTube credentials changed before session PO token initialization"
            )
        }
        val visitorData = getOrFetchVisitorData(
            requestContext.localization,
            requestContext.contentCountry,
            requestContext.loggedIn,
            requestContext.credentialIdentity
        )
        val playerContext = createPoTokenContext(
            visitorData,
            requestContext.clientName,
            requestContext.clientVersion,
            requestContext.userAgent
        )
        val attestationContext = localDomAttestationContext(
            visitorData,
            YoutubeParsingHelper.getClientVersion()
        )
        val credentialHeaders = createCredentialHeaders(requestContext.loggedIn)
        val rawToken = getOrMintToken(
            visitorData,
            attestationContext,
            requestContext.credentialIdentity,
            playerContext.cacheIdentity + ':' + attestationContext.cacheIdentity,
            credentialHeaders
        )
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken)
        Log.i(
            TAG,
            "session token ready client=${requestContext.clientName} " +
                "loggedIn=${requestContext.loggedIn} bytes=${rawToken.size}"
        )
        return YoutubeSessionPoToken(visitorData, encoded)
    }

    override fun getPoToken(
        info: YoutubeSabrInfo,
        streamState: YoutubeSabrStreamState
    ): ByteArray? {
        val credentialIdentity = currentCredentialIdentity(ServiceList.YouTube.hasTokens())
        credentialIdentityTracker.observe(credentialIdentity)
        val videoId = info.videoId
        val visitorData = info.visitorData ?: synchronized(visitorDataLock) {
            fetchedVisitorData
        } ?: throw SabrProtocolException("Missing visitorData for Local DOM PO token")
        val playerContext = createPoTokenContext(
            visitorData,
            info.profile.clientName,
            info.clientVersion,
            info.profile.userAgent
        )
        val loggedIn = ServiceList.YouTube.hasTokens()
        val attestationContext = localDomAttestationContext(
            visitorData,
            YoutubeParsingHelper.getClientVersion()
        )
        return getOrMintToken(
            videoId,
            attestationContext,
            credentialIdentity,
            playerContext.cacheIdentity + ':' + attestationContext.cacheIdentity,
            createCredentialHeaders(loggedIn)
        )
    }

    override fun invalidatePoTokenIdentity(info: YoutubeSabrInfo): Boolean {
        invalidatePoTokenIdentity("pending attestation video=${info.videoId}")
        return true
    }

    fun invalidateRejectedPoToken(videoId: String) {
        invalidatePoTokenIdentity("rejected PO token video=$videoId")
    }

    private fun invalidatePoTokenIdentity(reason: String) {
        sessionPoTokenPrewarmer.cancel()
        synchronized(visitorDataLock) {
            fetchedVisitorData = null
            fetchedVisitorDataLoggedIn = null
            fetchedVisitorDataCredentialIdentity = null
            visitorDataFetchedAtMs = 0
        }
        synchronized(generatorLock) {
            generator?.let { mainHandler.post { it.close() } }
            generator = null
            generatorContext = null
            generatorCredentialIdentity = null
        }
        cache.clear()
        prefs.edit().clear().commit()
        Log.i(TAG, "rotated YouTube attestation identity: $reason")
    }

    private fun getOrMintToken(
        contentBinding: String,
        context: LocalDomPoTokenContext,
        credentialIdentity: String,
        clientContextIdentity: String,
        credentialHeaders: Map<String, List<String>>
    ): ByteArray {
        synchronized(mintLocks.computeIfAbsent(contentBinding) { Any() }) {
            val now = System.currentTimeMillis()
            val cached = cache[contentBinding]
                ?: diskLoad(contentBinding)?.also { cache[contentBinding] = it }
            if (cached != null && cached.visitorData == context.visitorData &&
                cached.credentialIdentity == credentialIdentity &&
                cached.clientContextIdentity == clientContextIdentity &&
                now - cached.mintedAtMs < TOKEN_TTL_MS
            ) {
                Log.i(
                    TAG,
                    "cache hit bindingBytes=${contentBinding.length} " +
                        "bytes=${cached.token.size}"
                )
                return cached.token.clone()
            }
            val token = synchronized(generatorLock) {
                ensureGenerator(context, credentialIdentity, credentialHeaders)
                    .generateRawPoToken(contentBinding)
            }
            cache[contentBinding] = CachedToken(
                token,
                now,
                context.visitorData,
                credentialIdentity,
                clientContextIdentity
            )
            diskSave(
                contentBinding,
                token,
                now,
                context.visitorData,
                credentialIdentity,
                clientContextIdentity
            )
            Log.i(TAG, "mint complete bindingBytes=${contentBinding.length} bytes=${token.size}")
            return token.clone()
        }
    }

    fun hasCachedToken(videoId: String): Boolean {
        val credentialIdentity = currentCredentialIdentity(ServiceList.YouTube.hasTokens())
        credentialIdentityTracker.observe(credentialIdentity)
        val mem = cache[videoId]
        if (mem != null && mem.credentialIdentity == credentialIdentity &&
            System.currentTimeMillis() - mem.mintedAtMs < TOKEN_TTL_MS
        ) {
            return true
        }
        return diskLoad(videoId)?.credentialIdentity == credentialIdentity
    }

    fun clearCachedToken(videoId: String) {
        synchronized(mintLocks.computeIfAbsent(videoId) { Any() }) {
            cache.remove(videoId)
            prefs.edit().remove(videoId).commit()
        }
    }

    private fun ensureGenerator(
        context: LocalDomPoTokenContext,
        credentialIdentity: String,
        credentialHeaders: Map<String, List<String>>
    ): LocalDomPoTokenGenerator {
        synchronized(generatorLock) {
            val current = generator
            if (current != null && !current.isExpired() &&
                generatorContext == context &&
                generatorCredentialIdentity == credentialIdentity
            ) {
                return current
            }
            if (!credentialsStillMatch(credentialIdentity)) {
                throw SabrProtocolException(
                    "YouTube credentials changed before PO token generator initialization"
                )
            }
            current?.let { mainHandler.post { it.close() } }
            val fresh = LocalDomPoTokenGenerator.create(
                appContext,
                context,
                credentialHeaders
            )
            if (!credentialsStillMatch(credentialIdentity)) {
                mainHandler.post { fresh.close() }
                throw SabrProtocolException(
                    "YouTube credentials changed during PO token generator initialization"
                )
            }
            generator = fresh
            generatorContext = context
            generatorCredentialIdentity = credentialIdentity
            return fresh
        }
    }

    private fun createPoTokenContext(
        visitorData: String,
        clientName: String,
        clientVersion: String,
        userAgent: String?
    ): LocalDomPoTokenContext {
        if (clientName.isBlank() || clientVersion.isBlank() || userAgent.isNullOrBlank()) {
            throw SabrProtocolException("Missing YouTube client context for Local DOM PO token")
        }
        return LocalDomPoTokenContext(visitorData, clientName, clientVersion, userAgent)
    }

    private fun createCredentialHeaders(loggedIn: Boolean): Map<String, List<String>> {
        val headers = HashMap<String, List<String>>()
        if (loggedIn) {
            YoutubeParsingHelper.addLoggedInHeaders(headers)
        } else {
            YoutubeParsingHelper.addCookieHeader(headers)
        }
        return headers
    }

    private fun getOrFetchVisitorData(
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
        credentialIdentity: String
    ): String {
        synchronized(visitorDataLock) {
            val now = System.currentTimeMillis()
            val cached = fetchedVisitorData
            if (cached != null && fetchedVisitorDataLoggedIn == loggedIn &&
                fetchedVisitorDataCredentialIdentity == credentialIdentity &&
                now - visitorDataFetchedAtMs < VISITOR_DATA_TTL_MS
            ) {
                return cached
            }

            val headers = HashMap<String, List<String>>()
            YoutubeParsingHelper.addYoutubeHeaders(headers)
            headers["Content-Type"] = listOf("application/json")
            if (loggedIn) {
                YoutubeParsingHelper.addLoggedInHeaders(headers)
            }
            val fresh = YoutubeParsingHelper.getVisitorDataFromInnertube(
                InnertubeClientRequestInfo.ofWebClient(),
                localization,
                contentCountry,
                headers,
                YoutubeParsingHelper.YOUTUBEI_V1_URL,
                null,
                false
            )
            if (!credentialsStillMatch(credentialIdentity)) {
                throw SabrProtocolException(
                    "YouTube credentials changed while fetching visitorData"
                )
            }
            fetchedVisitorData = fresh
            fetchedVisitorDataLoggedIn = loggedIn
            fetchedVisitorDataCredentialIdentity = credentialIdentity
            visitorDataFetchedAtMs = now
            return fresh
        }
    }

    private fun currentCredentialIdentity(loggedIn: Boolean): String {
        return youtubeCredentialIdentity(loggedIn, ServiceList.YouTube.getTokens())
    }

    private fun credentialsStillMatch(credentialIdentity: String): Boolean {
        return currentCredentialIdentity(ServiceList.YouTube.hasTokens()) == credentialIdentity
    }

    private fun invalidateCredentialBoundState() {
        sessionPoTokenPrewarmer.cancel()
        prewarmExecutor.execute {
            synchronized(visitorDataLock) {
                fetchedVisitorData = null
                fetchedVisitorDataLoggedIn = null
                fetchedVisitorDataCredentialIdentity = null
                visitorDataFetchedAtMs = 0
            }
            synchronized(generatorLock) {
                generator?.let { mainHandler.post { it.close() } }
                generator = null
                generatorContext = null
                generatorCredentialIdentity = null
            }
            cache.clear()
            prefs.edit().clear().commit()
            Log.i(TAG, "YouTube credentials changed; cleared credential-bound PO token state")
        }
    }

    private fun diskLoad(videoId: String): CachedToken? {
        val value = prefs.getString(videoId, null) ?: return null
        val parts = value.split('|', limit = 5)
        if (parts.size != 5) {
            prefs.edit().remove(videoId).apply()
            return null
        }
        return try {
            val mintedAt = parts[0].toLong()
            if (System.currentTimeMillis() - mintedAt >= TOKEN_TTL_MS) {
                prefs.edit().remove(videoId).apply()
                null
            } else {
                val visitorData = String(
                    Base64.getUrlDecoder().decode(parts[3]),
                    StandardCharsets.UTF_8
                )
                CachedToken(
                    Base64.getUrlDecoder().decode(parts[4]),
                    mintedAt,
                    visitorData,
                    parts[1],
                    String(
                        Base64.getUrlDecoder().decode(parts[2]),
                        StandardCharsets.UTF_8
                    )
                )
            }
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun diskSave(
        videoId: String,
        token: ByteArray,
        mintedAt: Long,
        visitorData: String,
        credentialIdentity: String,
        clientContextIdentity: String
    ) {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encodedVisitorData = encoder.encodeToString(
            visitorData.toByteArray(StandardCharsets.UTF_8)
        )
        val encodedToken = encoder.encodeToString(token)
        val encodedContextIdentity = encoder.encodeToString(
            clientContextIdentity.toByteArray(StandardCharsets.UTF_8)
        )
        prefs.edit().putString(
            videoId,
            "$mintedAt|$credentialIdentity|$encodedContextIdentity|" +
                "$encodedVisitorData|$encodedToken"
        ).commit()
    }

    companion object {
        private const val TAG = "SabrLocalDomPoToken"
        private const val PREFS = "sabr_local_dom_video_token_cache"
        private const val TOKEN_TTL_MS = 6L * 60L * 60L * 1000L
        private const val VISITOR_DATA_TTL_MS = 6L * 60L * 60L * 1000L

        @Volatile
        private var sharedInstance: LocalDomPoTokenProvider? = null

        @JvmStatic
        fun shared(context: Context): LocalDomPoTokenProvider {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: LocalDomPoTokenProvider(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }
    }
}
