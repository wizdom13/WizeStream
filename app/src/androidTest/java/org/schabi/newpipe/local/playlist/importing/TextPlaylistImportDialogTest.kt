package org.schabi.newpipe.local.playlist.importing

import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.schabi.newpipe.about.AboutActivity

class TextPlaylistImportDialogTest {
    @Test fun rotationKeepsPreviewDestinationNameAndDuplicateChoice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                TextPlaylistImportDialog.show(activity.supportFragmentManager)
                activity.supportFragmentManager.executePendingTransactions()
                val dialog = activity.supportFragmentManager.findFragmentByTag("text-playlist-import") as TextPlaylistImportDialog
                val model = ViewModelProvider(dialog)[TextPlaylistImportModel::class.java]
                model.state.value = TextPlaylistState(
                    preview = TextPlaylistPreview(listOf(TextVideoLink(1, "https://www.youtube.com/watch?v=abcdefghijk")), emptyList()),
                    destinations = listOf(TextPlaylistDestination(42, "Existing")),
                    message = "Preview"
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = activity.supportFragmentManager.findFragmentByTag("text-playlist-import") as TextPlaylistImportDialog
                val views = descendants(dialog.requireDialog().window!!.decorView)
                views.filterIsInstance<TextInputEditText>().single().setText("Watch later")
                views.filterIsInstance<MaterialCheckBox>().single().isChecked = false
                views.filterIsInstance<Spinner>().single().setSelection(1)
            }
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = activity.supportFragmentManager.findFragmentByTag("text-playlist-import") as TextPlaylistImportDialog
                val model = ViewModelProvider(dialog)[TextPlaylistImportModel::class.java]
                assertEquals(42L, model.destinationId)
                assertEquals(1, model.state.value.preview!!.links.size)
                val views = descendants(dialog.requireDialog().window!!.decorView)
                assertEquals("Watch later", views.filterIsInstance<TextInputEditText>().single().text.toString())
                assertFalse(views.filterIsInstance<MaterialCheckBox>().single().isChecked)
                assertEquals(1, views.filterIsInstance<Spinner>().single().selectedItemPosition)
                dialog.dismissNow()
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
