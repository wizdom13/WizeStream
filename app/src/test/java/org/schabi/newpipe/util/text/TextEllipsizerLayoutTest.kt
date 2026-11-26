/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.util.text

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.CommentTextSizeHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TextEllipsizerLayoutTest {
    private lateinit var host: ActivityController<TestActivity>
    private val activity get() = host.get()
    private val bindings = CompositeDisposable()

    @Before
    fun setUp() {
        RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        host = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        settle()
    }

    @After
    fun tearDown() {
        bindings.dispose()
        try {
            host.pause().stop().destroy()
        } finally {
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun collapsedCommentRemeasuresItsTextActionsAndRowAfterEveryExpansion() {
        val row = commentRow()
        val text = row.findViewById<TextView>(R.id.itemCommentContentView)
        val ellipsizer = TextEllipsizer(text, 2, null)
        activity.content.addView(row)
        ellipsizer.setContent(Description(LONG_COMMENT, Description.PLAIN_TEXT))
        ellipsizer.ellipsize()
        settle()
        assertCollapsedGeometry(row)
        val collapsedHeight = row.height

        repeat(2) {
            ellipsizer.toggle()
            settle()
            assertEquals(LONG_COMMENT, text.text.toString())
            assertTrue("Expansion must increase the actual row height", row.height > collapsedHeight)

            ellipsizer.toggle()
            settle()
            assertCollapsedGeometry(row)
            assertEquals("Collapsing must restore the compact row height", collapsedHeight, row.height)
        }

        ellipsizer.setContent(Description("A short replacement comment", Description.PLAIN_TEXT))
        ellipsizer.ellipsize()
        settle()
        assertEquals("A short replacement comment", text.text.toString())
        assertCompactGeometry(row)
    }

    @Test
    fun clearingABindingCancelsBothUnmeasuredAndAlreadyQueuedCompletions() {
        val text = attachedText("Old binding")
        val binding = CompositeDisposable().also(bindings::add)
        var completions = 0
        TextViewLayoutCompletion.afterLayout(text, binding) { completions++ }
        binding.clear()

        text.text = "Measured but cancelled\nbinding"
        TextViewLayoutCompletion.afterLayout(text, binding) {
            text.text = "Stale completion"
            completions++
        }
        layout(text)
        assertEquals("A layout listener must not mutate text during layout", 0, completions)
        binding.clear()

        text.text = "Current\nbinding"
        TextViewLayoutCompletion.afterLayout(text, binding) { completions++ }
        layout(text)
        settle()
        assertEquals("Only the current binding completes", 1, completions)
        assertEquals("Current\nbinding", text.text.toString())
        text.requestLayout()
        layout(text)
        settle()
        assertEquals("The completion is one-shot", 1, completions)
    }

    @Test
    fun disposingABindingCancelsACompletionAlreadyQueuedByLayout() {
        val text = attachedText("Dispose after measurement")
        val binding = CompositeDisposable().also(bindings::add)
        var completions = 0
        TextViewLayoutCompletion.afterLayout(text, binding) { completions++ }
        layout(text)
        binding.dispose()
        settle()
        assertEquals(0, completions)
        TextViewLayoutCompletion.afterLayout(text, binding) { completions++ }
        assertEquals(0, completions)
    }

    @Test
    fun aValidLayoutCompletesImmediatelyExceptWhileItsParentIsLayingOut() {
        val parent = FrameLayout(activity)
        val text = TextView(activity).apply { this.text = "Already measured" }
        parent.addView(text, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.content.addView(parent, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120))
        settle()
        var completions = 0
        TextViewLayoutCompletion.afterLayout(text, bindings) { completions++ }
        assertEquals("A valid layout outside traversal remains synchronous", 1, completions)

        var observedParentLayout = false
        parent.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
                parent.removeOnLayoutChangeListener(this)
                observedParentLayout = true
                assertTrue(text.isLaidOut)
                assertFalse(text.isLayoutRequested)
                assertTrue(text.isInLayout)
                TextViewLayoutCompletion.afterLayout(text, bindings) {
                    assertFalse("Mutating completion runs after the entire layout returns", text.isInLayout)
                    completions++
                }
                assertEquals(1, completions)
            }
        })
        parent.layoutParams = parent.layoutParams.apply { height = 160 }
        settle()
        assertTrue(observedParentLayout)
        assertEquals(2, completions)
    }

    private fun attachedText(value: String): TextView = TextView(activity).apply {
        text = value
        activity.content.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun commentRow(): View = LayoutInflater.from(activity).inflate(R.layout.list_comment_item, activity.content, false).apply {
        findViewById<TextView>(R.id.itemTitleView).text = "Comment author · 3 days ago"
        findViewById<TextView>(R.id.detail_thumbs_up_count_view).text = "6"
        findViewById<View>(R.id.detail_pinned_view).visibility = View.GONE
        findViewById<View>(R.id.detail_heart_image_view).visibility = View.GONE
        findViewById<View>(R.id.replies_button).visibility = View.GONE
        CommentTextSizeHelper.applyCommentTextSize(findViewById(R.id.itemCommentContentView))
    }

    private fun assertCollapsedGeometry(row: View) {
        val text = row.findViewById<TextView>(R.id.itemCommentContentView)
        assertTrue(text.text.toString().endsWith("…"))
        assertEquals(2, text.maxLines)
        assertTrue(text.lineCount <= 2)
        assertCompactGeometry(row)
    }

    private fun assertCompactGeometry(row: View) {
        val text = row.findViewById<TextView>(R.id.itemCommentContentView)
        val reference = commentRow()
        val referenceText = reference.findViewById<TextView>(R.id.itemCommentContentView)
        referenceText.text = text.text
        referenceText.maxLines = 2
        layout(reference, row.width)
        assertTrue("The actual comment row must be measured", text.height > 0)
        assertEquals("The comment body must release the expanded text height", referenceText.height, text.height)
        assertEquals(
            "Like/reply actions must move up with the shortened text",
            reference.findViewById<View>(R.id.comment_actions_row).top,
            row.findViewById<View>(R.id.comment_actions_row).top
        )
        assertEquals("The outer row must release the same space", reference.height, row.height)
    }

    private fun layout(view: View, width: Int = 360) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

    class TestActivity : AppCompatActivity() {
        lateinit var content: FrameLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            content = FrameLayout(this)
            setContentView(content)
        }
    }

    companion object {
        private val LONG_COMMENT = List(10) { "This is a longer comment line with enough text to wrap." }.joinToString("\n")
    }
}
