package org.schabi.newpipe.local.subscription.item

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xwray.groupie.Item
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R

@RunWith(AndroidJUnit4::class)
class ImportSubscriptionsHintPlaceholderItemTest {
    @Test
    fun importHintItemInflatesWithStaticMessage() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = ContextThemeWrapper(targetContext, R.style.LightTheme)
        val item = ImportSubscriptionsHintPlaceholderItem()
        val parent = FrameLayout(context)
        val root = LayoutInflater.from(context)
            .inflate(item.layout, parent, false) as LinearLayout
        val message = root.getChildAt(1) as TextView

        assertEquals(Item::class.java, item.javaClass.superclass)
        assertEquals(R.layout.list_empty_view_subscriptions, item.layout)
        assertEquals(context.getString(R.string.import_subscriptions_hint), message.text.toString())
    }
}
