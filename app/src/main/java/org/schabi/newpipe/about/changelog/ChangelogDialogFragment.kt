package org.schabi.newpipe.about.changelog

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

class ChangelogDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val arguments = requireArguments()
        return MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.changelog_new_version_title, arguments.getString(VERSION)))
            .setView(changelogTextView(context, arguments.getString(HTML).orEmpty(), true))
            .setNegativeButton(R.string.close) { _, _ -> acknowledge() }
            .setPositiveButton(R.string.app_update_full_changelog) { _, _ ->
                acknowledge()
                startActivity(Intent(context, ChangelogActivity::class.java))
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        acknowledge()
        super.onCancel(dialog)
    }

    private fun acknowledge() {
        ChangelogPreferences(requireContext()).acknowledge(requireArguments().getInt(CODE))
    }

    companion object {
        const val TAG = "version-changelog"
        private const val VERSION = "version"
        private const val CODE = "code"
        private const val HTML = "html"

        internal fun newInstance(notice: ChangelogNotice): ChangelogDialogFragment = ChangelogDialogFragment().apply {
            arguments = bundleOf(VERSION to notice.version, CODE to notice.code, HTML to notice.html)
        }
    }
}
