package org.schabi.newpipe.error

import android.app.Application
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousApiException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InvidiousApiErrorInfoTest {
    @Test
    fun deniedSearchExplainsInstanceSwitchingWithoutOfferingBugReportsOrRetry() {
        for (status in listOf(401, 403)) {
            val error = error(InvidiousApiException(status, ": private server detail"))
            assertMessage(error, R.string.invidious_api_access_denied, status)
            assertFalse(error.isReportable)
            assertFalse(error.isRetryable)
            assertFalse(error.getMessage(RuntimeEnvironment.getApplication()).contains("private server detail"))
        }
    }

    @Test
    fun temporaryFailuresExplainTheProblemAndAllowRetry() {
        for (status in listOf(429, 500, 503)) {
            val error = error(InvidiousApiException(status, ""))
            val resource = if (status == 429) R.string.invidious_api_rate_limited else R.string.invidious_api_unavailable
            assertMessage(error, resource, status)
            assertFalse(error.isReportable)
            assertTrue(error.isRetryable)
        }
    }

    @Test
    fun wrappedDenialsKeepTheSameMessageAndActions() {
        val error = error(RuntimeException("search", IOException(InvidiousApiException(403, ""))))
        assertMessage(error, R.string.invidious_api_access_denied, 403)
        assertFalse(error.isReportable)
        assertFalse(error.isRetryable)
    }

    @Test
    fun otherRejectedRequestsShowTheirStatusInsteadOfAParsingError() {
        val error = error(InvidiousApiException(404, ""))
        assertMessage(error, R.string.invidious_api_rejected, 404)
        assertFalse(error.isReportable)
        assertFalse(error.isRetryable)
        assertTrue(error(InvidiousApiException(408, "")).isRetryable)
    }

    @Test
    fun malformedSuccessfulResponsesRemainReportableParsingErrors() {
        val error = error(ExtractionException("Invalid Invidious response"))
        val context = RuntimeEnvironment.getApplication()
        assertEquals(context.getString(R.string.parsing_error), error.getMessage(context).toString())
        assertTrue(error.isReportable)
        assertTrue(error.isRetryable)
    }

    @Test
    fun unrelatedCyclicCausesDoNotHangActionClassification() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)
        assertTrue(ErrorInfo.isReportable(first))
        assertTrue(ErrorInfo.isRetryable(first))
    }

    private fun error(cause: Throwable) = ErrorInfo(cause, UserAction.SEARCHED, "gaming", ServiceList.YouTube.serviceId)

    private fun assertMessage(error: ErrorInfo, resource: Int, status: Int) {
        val context = RuntimeEnvironment.getApplication()
        // Formatted ErrorInfo text passes through HTML, which adds paragraph-ending newlines.
        assertEquals(context.getString(resource, status.toString()), error.getMessage(context).toString().trimEnd())
    }
}
