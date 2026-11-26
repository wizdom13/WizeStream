/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.text

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity

class TextViewLayoutCompletionTest {
    @Test
    fun onlyTheCurrentBindingCompletesOnceAfterTextIsMeasured() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var view: TextView
            val binding = CompositeDisposable()
            var completions = 0
            try {
                scenario.onActivity { activity ->
                    view = TextView(activity).apply { text = "Old binding" }
                    activity.setContentView(view)
                    TextViewLayoutCompletion.afterLayout(view, binding) {
                        view.text = "Stale binding must not win"
                        completions++
                    }
                    assertEquals(0, completions)
                    binding.clear()

                    view.text = "Current\nbinding"
                    TextViewLayoutCompletion.afterLayout(view, binding) {
                        assertNotNull(view.layout)
                        assertEquals(2, view.lineCount)
                        completions++
                    }
                    assertEquals(0, completions)
                    layout(view)
                    assertEquals("The callback waits until layout has returned", 0, completions)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(1, completions)
                    assertEquals("Current\nbinding", view.text.toString())

                    view.requestLayout()
                    layout(view)
                    assertEquals("A completed listener must be removed", 1, completions)

                    TextViewLayoutCompletion.afterLayout(view, binding) { completions++ }
                    assertEquals("An already valid layout completes immediately", 2, completions)

                    view.text = "Queued completion"
                    TextViewLayoutCompletion.afterLayout(view, binding) { completions++ }
                    layout(view)
                    assertEquals(2, completions)
                    binding.clear()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals("Clearing also cancels a completion queued after layout", 2, completions)
                }
            } finally {
                scenario.onActivity { binding.dispose() }
            }
        }
    }

    private fun layout(view: TextView) {
        val width = (320 * view.resources.displayMetrics.density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
