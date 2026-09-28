package org.schabi.newpipe.util

import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsThumbnailPolicyTest {
    @Test
    fun shortsUseFitCenterToAvoidPortraitCropping() {
        assertEquals(
            ImageView.ScaleType.FIT_CENTER,
            ShortsThumbnailPolicy.scaleType(
                "https://www.youtube.com/shorts/example",
                45
            )
        )
    }

    @Test
    fun regularLongVideosKeepCenterCrop() {
        assertEquals(
            ImageView.ScaleType.CENTER_CROP,
            ShortsThumbnailPolicy.scaleType(
                "https://www.youtube.com/watch?v=example",
                600
            )
        )
    }
}
