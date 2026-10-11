package org.schabi.newpipe.learning

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentLearningDashboardBinding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningOverviewLayoutTest {
    @Test
    fun overviewAdaptsWithoutReplacingItsViewsOrContent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.LightTheme)
        val binding = FragmentLearningDashboardBinding.inflate(LayoutInflater.from(activity))
        binding.learningDashboardSummary.text = "Course progress"
        val overview = binding.learningDashboardOverview
        assertEquals(2, overview.childCount)
        LearningOverviewLayout.apply(overview, 1000)
        assertEquals(LinearLayout.HORIZONTAL, overview.orientation)
        assertEquals(1f, (overview.getChildAt(0).layoutParams as LinearLayout.LayoutParams).weight)
        LearningOverviewLayout.apply(overview, 400)
        assertEquals(LinearLayout.VERTICAL, overview.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, overview.getChildAt(0).layoutParams.width)
        assertEquals("Course progress", binding.learningDashboardSummary.text.toString())
        activity.finish()
    }
}
