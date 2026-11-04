package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import java.util.function.Consumer
import org.schabi.newpipe.R
import org.schabi.newpipe.util.KeyboardUtil

/** Registers with the fragment before creation; toolbar views are held only while bound. */
class VoiceSearchController(
    private val fragment: Fragment,
    private val submit: Consumer<String>
) {
    private var button: View? = null
    private var input: EditText? = null
    private var pendingQuery: String? = null
    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingQuery = recognizedQuery(result.resultCode, result.data)
        if (fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            submitPendingQuery()
        }
    }

    /** Called from onResume before the search screen restores its previous request. */
    fun submitPendingQuery(): Boolean {
        val query = pendingQuery ?: return false
        val editText = input ?: return false
        pendingQuery = null
        editText.setText(query)
        editText.setSelection(query.length)
        submit.accept(query)
        return true
    }

    fun bind(toolbar: View) {
        unbind()
        input = toolbar.findViewById(R.id.toolbar_search_edit_text)
        button = toolbar.findViewById<View>(R.id.toolbar_search_voice).apply {
            TooltipCompat.setTooltipText(this, context.getString(R.string.voice_search))
            setOnClickListener {
                KeyboardUtil.hideKeyboard(fragment.activity, input)
                try {
                    launcher.launch(recognitionIntent(context.getString(R.string.voice_search)))
                } catch (_: ActivityNotFoundException) {
                    showUnavailable()
                } catch (_: SecurityException) {
                    showUnavailable()
                }
            }
        }
    }

    fun unbind() {
        button?.setOnClickListener(null)
        button = null
        input = null
    }

    private fun showUnavailable() {
        fragment.context?.let { Toast.makeText(it, R.string.voice_search_unavailable, Toast.LENGTH_LONG).show() }
    }

    internal companion object {
        fun recognitionIntent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        fun recognizedQuery(resultCode: Int, data: Intent?): String? {
            if (resultCode != Activity.RESULT_OK) return null
            return data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.trim()
        }
    }
}
