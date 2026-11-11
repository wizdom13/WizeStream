package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.content.DialogInterface
import android.os.Looper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.schabi.newpipe.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubscriptionSelectionDialogTest {
    @Test
    fun selectAllAndClearStayOpenAndOnlyConfirmationReturnsTheFinalSelection() {
        val activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.LightTheme)
        activity.setup().visible()
        var confirmed: BooleanArray? = null
        val dialog = SubscriptionSelectionDialog.create(activity.get(), arrayOf("One", "Two", "Three")) { confirmed = it }
        try {
            dialog.show()
            shadowOf(Looper.getMainLooper()).idle()
            val toggle = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            val confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            assertFalse(confirm.isEnabled)
            toggle.performClick()
            assertTrue(dialog.isShowing)
            assertNull(confirmed)
            assertTrue((0..2).all { dialog.listView.isItemChecked(it) })
            assertEquals(activity.get().getString(R.string.subscription_clear_selection), toggle.text.toString())
            toggle.performClick()
            assertFalse(confirm.isEnabled)
            assertTrue((0..2).none { dialog.listView.isItemChecked(it) })
            toggle.performClick()
            dialog.listView.performItemClick(null, 1, 1)
            assertEquals(activity.get().getString(R.string.subscription_select_all), toggle.text.toString())
            confirm.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertArrayEquals(booleanArrayOf(true, false, true), confirmed)
        } finally {
            dialog.dismiss()
            activity.pause().stop().destroy()
        }
    }
}
