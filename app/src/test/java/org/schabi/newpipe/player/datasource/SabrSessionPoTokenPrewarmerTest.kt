package org.schabi.newpipe.player.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SabrSessionPoTokenPrewarmerTest {
    @Test(timeout = 5_000)
    fun sameContextSharesOneInFlightTask() {
        val executor = Executors.newSingleThreadExecutor()
        val prewarmer = ContextBoundSingleFlight<String, String>(executor)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        try {
            assertTrue(prewarmer.start("context") {
                calls.incrementAndGet()
                started.countDown()
                release.await()
                "token"
            })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val shared = prewarmer.inFlight("context")

            assertFalse(prewarmer.start("context") {
                calls.incrementAndGet()
                "duplicate"
            })
            release.countDown()

            assertEquals("token", shared?.get(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun replacingContextCancelsOldTask() {
        val executor = Executors.newSingleThreadExecutor()
        val prewarmer = ContextBoundSingleFlight<String, String>(executor)
        val started = CountDownLatch(1)
        val replacementStarted = CountDownLatch(1)
        val replacementRelease = CountDownLatch(1)
        try {
            assertTrue(prewarmer.start("old") {
                started.countDown()
                CountDownLatch(1).await()
                "old-token"
            })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val old = prewarmer.inFlight("old")

            assertTrue(prewarmer.start("new") {
                replacementStarted.countDown()
                replacementRelease.await()
                "new-token"
            })
            assertTrue(replacementStarted.await(2, TimeUnit.SECONDS))
            val replacement = prewarmer.inFlight("new")

            assertTrue(old?.isCancelled == true)
            replacementRelease.countDown()
            assertEquals("new-token", replacement?.get(2, TimeUnit.SECONDS))
            assertNull(prewarmer.inFlight("old"))
        } finally {
            replacementRelease.countDown()
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun foregroundSharesTaskWhileClientVersionResolutionIsBlocked() {
        val prewarmExecutor = Executors.newSingleThreadExecutor()
        val foregroundExecutor = Executors.newSingleThreadExecutor()
        val prewarmer = ContextBoundSingleFlight<
            YoutubeSessionPoTokenPrewarmContext,
            PreparedYoutubeSessionPoToken
        >(prewarmExecutor)
        val requestContext = YoutubeSessionPoTokenContext(
            "MWEB",
            "2.test",
            "test-user-agent",
            Localization("en", "US"),
            ContentCountry("US"),
            false,
            "credential-a",
        )
        val versionResolutionStarted = CountDownLatch(1)
        val versionResolutionRelease = CountDownLatch(1)
        val foregroundStarted = CountDownLatch(1)
        val initializations = AtomicInteger()
        val synchronousInitializations = AtomicInteger()
        try {
            assertTrue(prewarmer.start(requestContext.prewarmContext()) {
                versionResolutionStarted.countDown()
                versionResolutionRelease.await()
                initializations.incrementAndGet()
                PreparedYoutubeSessionPoToken(
                    requestContext,
                    YoutubeSessionPoToken("visitor-data", "prewarmed-token"),
                )
            })
            assertTrue(versionResolutionStarted.await(2, TimeUnit.SECONDS))

            val foreground = foregroundExecutor.submit<YoutubeSessionPoToken> {
                foregroundStarted.countDown()
                val prepared = prewarmer.inFlight(requestContext.prewarmContext())?.get()
                if (prepared?.context == requestContext) {
                    prepared.token
                } else {
                    synchronousInitializations.incrementAndGet()
                    YoutubeSessionPoToken("visitor-data", "synchronous-token")
                }
            }
            assertTrue(foregroundStarted.await(2, TimeUnit.SECONDS))
            assertFalse(foreground.isDone)
            assertEquals(0, initializations.get())
            assertEquals(0, synchronousInitializations.get())

            versionResolutionRelease.countDown()

            assertEquals(
                "prewarmed-token",
                foreground.get(2, TimeUnit.SECONDS).poToken,
            )
            assertEquals(1, initializations.get())
            assertEquals(0, synchronousInitializations.get())
        } finally {
            versionResolutionRelease.countDown()
            prewarmExecutor.shutdownNow()
            foregroundExecutor.shutdownNow()
        }
    }

    @Test
    fun fullPlayerContextControlsTaskIdentity() {
        val context = YoutubeSessionPoTokenContext(
            "MWEB",
            "2.test",
            "test-user-agent",
            Localization("en", "US"),
            ContentCountry("US"),
            false,
            "credential-a",
        )

        assertNotEquals(context, context.copy(clientName = "WEB"))
        assertNotEquals(context, context.copy(clientVersion = "3.test"))
        assertEquals(
            context.prewarmContext(),
            context.copy(clientVersion = "3.test").prewarmContext(),
        )
        assertNotEquals(context, context.copy(userAgent = "other-user-agent"))
        assertNotEquals(context, context.copy(localization = Localization("zh", "CN")))
        assertNotEquals(context, context.copy(contentCountry = ContentCountry("CN")))
        assertNotEquals(context, context.copy(loggedIn = true))
        assertNotEquals(context, context.copy(credentialIdentity = "credential-b"))
    }
}
