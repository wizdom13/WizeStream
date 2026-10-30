package org.schabi.newpipe.player.ui

import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TvMiniPlayerFocusTest {
    @Test
    fun collapsedPlayerAllowsRemoteReturnButBlocksHiddenDetails() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val details = FrameLayout(context)
        val overlay = FrameLayout(context)
        val hiddenControl = View(context).apply { isFocusableInTouchMode = true }
        val reopen = View(context).apply { isFocusableInTouchMode = true }
        details.addView(hiddenControl)
        overlay.addView(reopen)
        val content = FrameLayout(context)
        val relatedItem = View(context).apply { isFocusableInTouchMode = true }
        content.addView(details)
        content.addView(relatedItem)
        root.addView(content)
        root.addView(overlay)
        var reopened = false
        reopen.setOnClickListener { reopened = true }

        repeat(2) {
            TvMiniPlayerFocus.apply(root, details, overlay, BottomSheetBehavior.STATE_COLLAPSED)
            assertFalse(hiddenControl.requestFocus())
            assertFalse(relatedItem.requestFocus())
            assertTrue(reopen.requestFocus())
            reopen.performClick()
            assertTrue(reopened)

            TvMiniPlayerFocus.apply(root, details, overlay, BottomSheetBehavior.STATE_EXPANDED)
            assertFalse(reopen.requestFocus())
            assertTrue(hiddenControl.requestFocus())

            TvMiniPlayerFocus.apply(root, details, overlay, BottomSheetBehavior.STATE_HIDDEN)
            assertFalse(reopen.requestFocus())
            assertFalse(hiddenControl.requestFocus())
            assertFalse(relatedItem.requestFocus())
        }
    }
}
