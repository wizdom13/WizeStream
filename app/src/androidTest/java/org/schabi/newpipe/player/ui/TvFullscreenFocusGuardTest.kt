package org.schabi.newpipe.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding

class TvFullscreenFocusGuardTest {
    @Test
    fun fullscreenExcludesDetailsAndRestoresFocusAfterRepeatedTransitions() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val context = ContextThemeWrapper(activity, R.style.DarkTheme)
                val binding = FragmentVideoDetailBinding.inflate(LayoutInflater.from(context))
                val guard = TvFullscreenFocusGuard(binding)
                val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                activity.setContentView(root)

                // Exercise the real guarded containers independently of app-bar measurement,
                // fragment loading and the emulator's current touch mode.
                val groups = listOfNotNull(
                    binding.detailContentRootLayout,
                    binding.viewPager,
                    binding.relatedItemsLayout,
                    binding.overlayLayout
                )
                val targets = groups.map { group ->
                    (group.parent as? ViewGroup)?.removeView(group)
                    group.removeAllViews()
                    group.visibility = View.VISIBLE
                    root.addView(group, LinearLayout.LayoutParams(300, 80))
                    if (group === binding.viewPager) {
                        // ViewPager enumerates page children only when an adapter owns them.
                        group.isFocusableInTouchMode = true
                        group
                    } else {
                        Button(context).apply {
                            text = "Detail action"
                            isFocusableInTouchMode = true
                            group.addView(this)
                        }
                    }
                }
                val play = Button(context).apply {
                    text = "Play"
                    isFocusableInTouchMode = true
                    root.addView(this)
                }

                fun focusables(direction: Int): List<View> = ArrayList<View>().also {
                    root.addFocusables(it, direction, View.FOCUSABLES_ALL)
                }

                targets.forEach { assertTrue("Baseline detail focus", it.requestFocus()) }
                repeat(3) {
                    guard.update(true, true)
                    guard.update(true, true)
                    targets.forEach {
                        assertFalse("Fullscreen must block detail focus", it.requestFocus())
                        assertFalse(focusables(View.FOCUS_DOWN).contains(it))
                        assertFalse(focusables(View.FOCUS_UP).contains(it))
                    }
                    assertTrue("Player focus remains available", play.requestFocus())
                    assertTrue(focusables(View.FOCUS_DOWN).contains(play))

                    guard.update(false, true)
                    targets.forEach {
                        assertTrue("Exiting fullscreen restores detail focus", it.requestFocus())
                        assertTrue(focusables(View.FOCUS_UP).contains(it))
                    }
                }
                guard.update(true, false)
                targets.forEach { assertTrue("Non-TV focus is unchanged", it.requestFocus()) }
            }
        }
    }
}
