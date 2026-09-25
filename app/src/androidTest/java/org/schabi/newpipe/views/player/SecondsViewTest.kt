package org.schabi.newpipe.views.player

import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class SecondsViewTest {
    @Test
    fun zeroSecondResetStaysOutOfPluralFormatting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                R.style.LightTheme
            )
            val view = SecondsView(context, null)

            view.seconds = 10
            assertTrue(view.binding.tvSeconds.text.isNotEmpty())

            view.seconds = 0
            assertEquals(0, view.seconds)
            assertEquals("", view.binding.tvSeconds.text.toString())

            view.seconds = 5
            assertTrue(view.binding.tvSeconds.text.isNotEmpty())
        }
    }
}
