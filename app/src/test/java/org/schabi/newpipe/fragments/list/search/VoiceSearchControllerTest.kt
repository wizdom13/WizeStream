package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceSearchControllerTest {
    @Test
    fun microphoneLaunchesRecognizerAndSubmitsTrimmedSpeech() = withScreen { activity, fragment ->
        fragment.requireView().findViewById<View>(R.id.toolbar_search_voice).performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, request.intent.action)
        assertTrue(request.intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, speech("  music live  "))
        assertEquals(listOf("music live"), fragment.queries)
        assertEquals("music live", fragment.input().text.toString())
    }

    @Test
    fun cancelAndEmptyResultsPreserveTypedSearch() = withScreen { activity, fragment ->
        fragment.input().setText("existing query")
        listOf(Activity.RESULT_CANCELED to speech("ignored"), Activity.RESULT_OK to speech("  "), Activity.RESULT_OK to null)
            .forEach { (code, data) ->
                fragment.requireView().findViewById<View>(R.id.toolbar_search_voice).performClick()
                val request = shadowOf(activity).nextStartedActivityForResult
                activity.activityResultRegistry.dispatchResult(request.requestCode, code, data)
            }
        assertTrue(fragment.queries.isEmpty())
        assertEquals("existing query", fragment.input().text.toString())
    }

    @Test
    fun pendingResultUsesRecreatedViewAndOldButtonIsUnbound() = withScreen { activity, fragment ->
        val oldButton = fragment.requireView().findViewById<View>(R.id.toolbar_search_voice)
        oldButton.performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        assertFalse(oldButton.hasOnClickListeners())
        activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, speech("new view"))
        assertTrue(fragment.queries.isEmpty())
        activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        assertEquals(listOf("new view"), fragment.queries)
        assertEquals("new view", fragment.input().text.toString())
    }

    @Test
    fun unavailableOrBlockedRecognizerShowsMessageWithoutChangingQuery() = withScreen { activity, fragment ->
        fragment.input().setText("keep me")
        listOf(ActivityNotFoundException(), SecurityException()).forEach { failure ->
            activity.launchFailure = failure
            fragment.requireView().findViewById<View>(R.id.toolbar_search_voice).performClick()
            assertEquals(activity.getString(R.string.voice_search_unavailable), ShadowToast.getTextOfLatestToast())
            assertEquals("keep me", fragment.input().text.toString())
            assertTrue(fragment.queries.isEmpty())
        }
    }

    private fun withScreen(test: (TestActivity, TestFragment) -> Unit) {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = TestFragment()
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            test(activity, fragment)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun speech(query: String) = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(query))

    class TestActivity : FragmentActivity() {
        var launchFailure: RuntimeException? = null

        override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
            launchFailure?.let { throw it }
            super.startActivityForResult(intent, requestCode, options)
        }
    }

    class TestFragment : Fragment() {
        val queries = mutableListOf<String>()
        private val voiceSearch = VoiceSearchController(this) { queries.add(it) }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext()).apply {
            addView(EditText(context).apply { id = R.id.toolbar_search_edit_text })
            addView(View(context).apply { id = R.id.toolbar_search_voice })
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            voiceSearch.bind(view)
        }

        override fun onResume() {
            super.onResume()
            voiceSearch.submitPendingQuery()
        }

        override fun onDestroyView() {
            voiceSearch.unbind()
            super.onDestroyView()
        }

        fun input(): EditText = requireView().findViewById(R.id.toolbar_search_edit_text)
    }
}
