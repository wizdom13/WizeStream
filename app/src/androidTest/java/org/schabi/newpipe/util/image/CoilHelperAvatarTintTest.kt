package org.schabi.newpipe.util.image

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.Image

@RunWith(AndroidJUnit4::class)
class CoilHelperAvatarTintTest {
    @Test
    fun listAvatarLoadingClearsInheritedTintAndColorFilter() {
        checkAvatarTintCleared { target ->
            CoilHelper.loadAvatar(target, emptyList<Image>())
        }
    }

    @Test
    fun directAvatarLoadingClearsInheritedTintAndColorFilter() {
        checkAvatarTintCleared { target ->
            CoilHelper.loadAvatar(target, null as String?)
        }
    }

    @Test
    fun clearingAvatarRemovesInheritedTintAndColorFilter() {
        checkAvatarTintCleared(CoilHelper::clearAvatar)
    }

    private fun checkAvatarTintCleared(load: (ImageView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val target = ImageView(instrumentation.targetContext)
            ImageViewCompat.setImageTintList(target, ColorStateList.valueOf(Color.WHITE))
            target.setColorFilter(Color.WHITE)
            assertNotNull(ImageViewCompat.getImageTintList(target))
            assertNotNull(target.colorFilter)

            load(target)

            assertNull(ImageViewCompat.getImageTintList(target))
            assertNull(target.colorFilter)
            assertNotNull(target.drawable)
            CoilHelper.clearAvatar(target)
        }
    }
}
