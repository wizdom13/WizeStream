package org.schabi.newpipe.local.history

import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ToolbarLayoutBinding
import org.schabi.newpipe.util.KeyboardUtil

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w320dp-h640dp")
class IncognitoToolbarUiTest {
    @Test
    fun narrowSearchKeepsItsWidthAndFocusWhenIncognitoIsEnabled() = withScreen { activity, binding, _ ->
        val input = binding.toolbarSearchContainer.toolbarSearchEditText
        IncognitoMode.setEnabled(activity, false)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        val normalWidth = input.width
        assertTrue("The search input must remain measurable", normalWidth > 0)

        input.setText("existing query")
        IncognitoMode.setEnabled(activity, true)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        assertEquals(normalWidth, input.width)
        assertEquals(View.VISIBLE, binding.incognitoStatus.visibility)
        assertTrue(binding.incognitoStatus.top >= binding.toolbar.bottom)
        assertFalse(binding.incognitoStatus.isFocusable)
        assertEquals("existing query", input.text.toString())
        KeyboardUtil.showKeyboard(activity, input)
        assertTrue(input.hasFocus())
        assertTrue(binding.toolbar.menu.findItem(R.id.action_incognito).isChecked)
    }

    @Test
    fun longPrivacyLabelsWrapOutsideTheSearchRowAndTogglingRestoresNormalMode() = withScreen { activity, binding, _ ->
        IncognitoMode.setEnabled(activity, false)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        val input = binding.toolbarSearchContainer.toolbarSearchEditText
        val normalWidth = input.width
        val menuSize = binding.toolbar.menu.size()
        IncognitoMode.setEnabled(activity, true)
        binding.incognitoStatus.text = "Incognito is active. ".repeat(12)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        assertEquals(normalWidth, input.width)
        assertTrue(binding.incognitoStatus.lineCount > 1)
        assertEquals(menuSize, binding.toolbar.menu.size())

        IncognitoMode.setEnabled(activity, false)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        assertEquals(View.GONE, binding.incognitoStatus.visibility)
        assertFalse(binding.toolbar.menu.findItem(R.id.action_incognito).isChecked)
        assertEquals(normalWidth, input.width)
    }

    @Test
    fun pageContentReservesSpaceForWrappedStatusAndRestoresItsInsetWhenDisabled() = withScreen { activity, binding, content ->
        IncognitoMode.setEnabled(activity, true)
        binding.incognitoStatus.text = "Incognito is active. ".repeat(12)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        assertEquals(
            binding.toolbar.height + binding.incognitoStatus.height,
            (content.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        )
        assertTrue((content.layoutParams as ViewGroup.MarginLayoutParams).topMargin > binding.toolbar.height)

        IncognitoMode.setEnabled(activity, false)
        IncognitoToolbarUi.prepareMenu(activity, binding.toolbar.menu, binding.incognitoStatus)
        layout(binding)
        assertEquals(binding.toolbar.height, (content.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    }

    private fun withScreen(test: (TestActivity, ToolbarLayoutBinding, View) -> Unit) {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val binding = ToolbarLayoutBinding.inflate(activity.layoutInflater)
            binding.toolbarSearchContainer.root.visibility = View.VISIBLE
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            val content = View(activity)
            val root = FrameLayout(activity).apply {
                addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(binding.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            IncognitoToolbarUi.bindContentInset(binding, content)
            activity.setContentView(root)
            test(activity, binding, content)
        } finally {
            IncognitoMode.setEnabled(controller.get(), false)
            controller.pause().stop().destroy()
        }
    }

    private fun layout(binding: ToolbarLayoutBinding) {
        val density = binding.root.resources.displayMetrics.density
        val width = (320 * density).roundToInt()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((640 * density).roundToInt(), View.MeasureSpec.AT_MOST)
        )
        binding.root.layout(0, 0, width, binding.root.measuredHeight)
    }

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
        }
    }
}
