package org.schabi.newpipe.sponsorblock

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FullVideoLabelsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun resetPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun matchesTheExactVideoAndUsesDisclosurePriority() {
        val json = """[{"videoID":"other","segments":[{"category":"sponsor"}]},{"videoID":"$ID","segments":[{"category":"selfpromo"},{"category":"exclusive_access"}]}]"""
        assertEquals(FullVideoLabel.EXCLUSIVE_ACCESS, FullVideoLabelRepository.parse(json, ID))
        assertEquals(FullVideoLabel.SPONSOR, FullVideoLabelRepository.parse("""[{"videoID":"$ID","segments":[{"category":"selfpromo"},{"category":"sponsor"}]}]""", ID))
        assertNull(FullVideoLabelRepository.parse("""[{"videoID":"$ID","segments":[{"category":"unknown"}]}]""", ID))
        assertNull(FullVideoLabelRepository.parse(json, "not-matching"))
    }

    @Test
    fun hashesRequestsAndCachesBothKnownAndAbsentLabels() {
        var calls = 0
        var time = 0L
        val prefixes = mutableListOf<String>()
        val repository = FullVideoLabelRepository(
            { prefix ->
                calls++
                prefixes.add(prefix)
                "[]"
            },
            { time }
        )
        assertFalse(repository.get(ID).blockingGet().isPresent)
        assertFalse(repository.get(ID).blockingGet().isPresent)
        assertEquals(1, calls)
        assertEquals(listOf(FullVideoLabelRepository.hashPrefix(ID)), prefixes)
        assertEquals(5, prefixes.single().length)
        assertFalse(prefixes.single().contains(ID))
        time = 6 * 60 * 60 * 1_000L
        repository.get(ID).blockingGet()
        assertEquals(2, calls)
        assertFalse(repository.get("invalid").blockingGet().isPresent)
        assertEquals(2, calls)
    }

    @Test
    fun disabledSponsorBlockOrLabelsSuppressesLookups() {
        assertFalse(FullVideoLabels.enabled(context))
        assertFalse(FullVideoLabels.get(context, 0, "https://youtube.com/watch?v=$ID").blockingGet().isPresent)
        enable()
        assertTrue(FullVideoLabels.enabled(context))
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sponsor_block_full_video_labels", false).commit()
        assertFalse(FullVideoLabels.enabled(context))
        assertFalse(FullVideoLabels.get(context, 0, "https://youtube.com/watch?v=$ID").blockingGet().isPresent)
    }

    @Test
    fun rebindingAndRecyclingCannotDisplayAStaleDisclosure() {
        enable()
        val old = SingleSubject.create<Optional<FullVideoLabel>>()
        val root = FrameLayout(context)
        val badge = FullVideoLabelBadge(root) { _, url -> if (url == "old") old else Single.just(Optional.of(FullVideoLabel.SELF_PROMOTION)) }
        badge.bind(0, "old")
        badge.bind(0, "new")
        shadowOf(Looper.getMainLooper()).idle()
        old.onSuccess(Optional.of(FullVideoLabel.SPONSOR))
        shadowOf(Looper.getMainLooper()).idle()
        val view = root.findViewById<View>(R.id.full_video_label_badge)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(context.getString(R.string.full_video_label_self_promotion), view.contentDescription)
        badge.clear()
        assertEquals(View.GONE, view.visibility)
        assertNull(view.contentDescription)
    }

    private fun enable() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(context.getString(R.string.sponsor_block_enable_key), true).commit()
    }

    companion object {
        private const val ID = "dQw4w9WgXcQ"
    }
}
