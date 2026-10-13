package org.schabi.newpipe.learning

import android.widget.EditText
import java.util.function.LongConsumer
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.util.external_communication.ShareUtils

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoteTextActionsTest {
    @Test
    fun editingLinksRequiresLongPressAndTimestampsSeekOnlyOnLongPress() {
        val text = EditText(RuntimeEnvironment.getApplication())
        val seek = mock(LongConsumer::class.java)
        NoteTextActions.install(text, seek, null)
        text.setText("See https://example.com/page and 1:02")
        text.setSelection(10)
        mockStatic(ShareUtils::class.java).use { links ->
            text.performClick()
            links.verifyNoInteractions()
            text.performLongClick()
            links.verify { ShareUtils.openUrlInBrowser(text.context, "https://example.com/page") }
        }
        text.setSelection(text.text.length - 2)
        text.performClick()
        verify(seek, never()).accept(62_000)
        text.performLongClick()
        verify(seek).accept(62_000)
    }

    @Test
    fun unsupportedSchemesAndTimesInsideUrlsAreNotSeekActions() {
        assertNull(NoteTextActions.urlAt("javascript:alert(1)", 5))
        assertNull(NoteTextActions.timestampAt("https://example.com/1:02", 21))
        assertNull(NoteTextActions.timestampAt("Bad 1:99", 6))
    }
}
