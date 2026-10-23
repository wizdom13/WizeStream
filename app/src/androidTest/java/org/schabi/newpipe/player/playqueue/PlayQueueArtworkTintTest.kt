package org.schabi.newpipe.player.playqueue

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.ImageViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class PlayQueueArtworkTintTest {
    @Test
    fun queueArtworkDoesNotInheritPlayerIconTint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (baseTheme in listOf(R.style.LightTheme, R.style.DarkTheme)) {
                val context = ContextThemeWrapper(instrumentation.targetContext, baseTheme)
                context.theme.applyStyle(R.style.WhiteTintTheme, true)
                context.theme.applyStyle(R.style.PlayQueueItemTextTheme, true)
                val row = LayoutInflater.from(context).inflate(
                    R.layout.play_queue_item,
                    FrameLayout(context),
                    false
                )
                val artwork = row.findViewById<ImageView>(R.id.itemThumbnailView)
                val handle = row.findViewById<ImageView>(R.id.itemHandle)

                // Artwork and its loading/error placeholder retain their original colors.
                assertNull(ImageViewCompat.getImageTintList(artwork))
                assertNotNull(artwork.drawable)
                // Queue controls still use the theme's foreground color.
                assertNotNull(ImageViewCompat.getImageTintList(handle))
            }
        }
    }
}
