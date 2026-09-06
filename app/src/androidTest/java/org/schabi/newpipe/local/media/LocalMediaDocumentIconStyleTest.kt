package org.schabi.newpipe.local.media

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class LocalMediaDocumentIconStyleTest {
    @Test
    fun mediaArtworkClearsDirectoryTintAndPaddingAcrossRowReuse() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val icon = ImageView(ContextThemeWrapper(baseContext, R.style.LightTheme))

        applyLocalMediaDocumentIconStyle(icon, isDirectory = true)
        assertNotNull(icon.imageTintList)
        assertTrue(icon.paddingLeft > 0)
        assertEquals(ImageView.ScaleType.FIT_CENTER, icon.scaleType)

        applyLocalMediaDocumentIconStyle(icon, isDirectory = false)
        assertNull(icon.imageTintList)
        assertEquals(0, icon.paddingLeft)
        assertEquals(0, icon.paddingTop)
        assertEquals(0, icon.paddingRight)
        assertEquals(0, icon.paddingBottom)
        assertEquals(ImageView.ScaleType.CENTER_CROP, icon.scaleType)

        applyLocalMediaDocumentIconStyle(icon, isDirectory = true)
        assertNotNull(icon.imageTintList)
        assertTrue(icon.paddingLeft > 0)
        assertEquals(ImageView.ScaleType.FIT_CENTER, icon.scaleType)
    }
}
