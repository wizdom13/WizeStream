package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    @Test
    fun customFormRejectsInvalidLimitsAndStartsWithValidatedValues() {
        val activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.LightTheme)
        activity.setup()
        var selected: Pair<Int, Long>? = null
        try {
            DeepSearchDialog.show(activity.get()) { pages, delay -> selected = pages to delay }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val inputs = inputs(dialog.window!!.decorView)
            assertEquals(2, inputs.size)
            inputs[0].setText("251")
            inputs[1].setText("0")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(dialog.isShowing)
            assertNotNull(inputs[0].error)
            assertNotNull(inputs[1].error)
            inputs[0].setText("42")
            inputs[1].setText("2.5")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(42 to 2500L, selected)
            assertFalse(dialog.isShowing)
        } finally {
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.dismiss()
            activity.pause().stop().destroy()
        }
    }

    private fun inputs(view: View): List<EditText> {
        if (view is EditText) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { inputs(view.getChildAt(it)) }
    }
}
