package org.schabi.newpipe.util.external_communication

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TubeBeamUtilsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun showsOnlyForYouTubeWithAnEnabledExportedSendActivity() {
        assertFalse(TubeBeamUtils.shouldShowSendToTv(context, ServiceList.YouTube.serviceId))
        val activity = ActivityInfo().apply {
            packageName = "dev.tubebeam"
            name = "dev.tubebeam.share.ShareReceiverActivity"
            enabled = true
            exported = true
            applicationInfo = ApplicationInfo().apply { enabled = true }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            TubeBeamUtils.buildIntent(context, ""),
            ResolveInfo().apply { activityInfo = activity }
        )
        assertTrue(TubeBeamUtils.shouldShowSendToTv(context, ServiceList.YouTube.serviceId))
        assertFalse(TubeBeamUtils.shouldShowSendToTv(context, ServiceList.SoundCloud.serviceId))
        activity.exported = false
        assertFalse(TubeBeamUtils.shouldShowSendToTv(context, ServiceList.YouTube.serviceId))
    }

    @Test
    fun sendsAnExplicitTextIntentWithoutQueueExtras() {
        var sent: Intent? = null
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                sent = intent
            }
        }
        assertTrue(TubeBeamUtils.sendToTv(receiver, "https://www.youtube.com/watch?v=abc", 42_500))
        val intent = sent!!
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("dev.tubebeam", intent.component?.packageName)
        assertEquals("dev.tubebeam.share.ShareReceiverActivity", intent.component?.className)
        assertEquals("https://www.youtube.com/watch?v=abc&t=42", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(setOf(Intent.EXTRA_TEXT), intent.extras?.keySet())
    }

    @Test
    fun launchFailuresAreReported() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val receiver = object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    throw failure
                }
            }
            assertFalse(TubeBeamUtils.sendToTv(receiver, "https://www.youtube.com/watch?v=abc"))
            assertEquals(context.getString(R.string.tubebeam_launch_failed), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun replacesOldPositionsAndPreservesOtherParameters() {
        val url = "https://www.youtube.com/watch?v=abc&t=12&start=10&time_continue=9&list=PL123"
        val result = Uri.parse(TubeBeamUtils.withPosition(url, 95_999))
        assertEquals(listOf("95"), result.getQueryParameters("t"))
        assertEquals("abc", result.getQueryParameter("v"))
        assertEquals("PL123", result.getQueryParameter("list"))
        assertFalse(result.queryParameterNames.contains("start"))
        assertFalse(result.queryParameterNames.contains("time_continue"))
        assertEquals(url, TubeBeamUtils.withPosition(url, null))
        assertEquals(url, TubeBeamUtils.withPosition(url, -1))
        assertEquals("0", Uri.parse(TubeBeamUtils.withPosition(url, 0)).getQueryParameter("t"))
    }
}
