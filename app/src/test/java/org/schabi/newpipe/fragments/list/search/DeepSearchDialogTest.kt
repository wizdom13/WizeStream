package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeepSearchDialogTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test
    fun customFormRejectsInvalidPageAndDelayLimits() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.get()
            activity.setTheme(R.style.LightTheme)
            controller.setup().visible()
            var selected: DeepSearchOptions.Request? = null
            DeepSearchDialog.show(activity, false) { selected = it }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val inputs = descendants(dialog.window!!.decorView).filterIsInstance<EditText>()
            inputs[0].setText("251")
            inputs[1].setText("0")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertNull(selected)
            assertNotNull(inputs[0].error)
            assertNotNull(inputs[1].error)
            inputs[0].setText("42")
            inputs[1].setText("2.5")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(42, selected!!.pages)
            assertEquals(2500L, selected!!.delayMillis)
            assertNull(selected!!.year)
        }
    }

    @Test
    fun rejectsInvalidLimitAndSubmitsMonthlySettings() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val activity = controller.get()
            activity.setTheme(R.style.LightTheme)
            controller.setup().visible()
            var request: DeepSearchOptions.Request? = null
            DeepSearchDialog.show(activity, true) { request = it }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val views = descendants(dialog.window!!.decorView)
            val inputs = views.filterIsInstance<EditText>()
            val results = inputs.single { it.contentDescription == activity.getString(R.string.search_deep_result_limit) }
            results.setText("20001")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertNull(request)
            assertNotNull(results.error)
            results.setText("10000")
            views.filterIsInstance<MaterialCheckBox>().single().isChecked = true
            inputs.single { it.contentDescription == activity.getString(R.string.search_deep_year) }.setText("2024")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(10000, request!!.results)
            assertEquals(2024, request!!.year)
        }
    }
}
