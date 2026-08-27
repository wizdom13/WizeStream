package org.schabi.newpipe.settings.notifications

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.databinding.DialogNotificationFilterBinding
import org.schabi.newpipe.local.feed.notifications.NotificationKeywordFilter

object NotificationConfigDialog {
    fun interface SaveListener {
        fun onSave(@NotificationMode mode: Int, keywords: String)
    }

    @JvmStatic
    fun show(
        fragment: Fragment,
        title: CharSequence,
        @NotificationMode currentMode: Int,
        currentKeywords: String?,
        saveListener: SaveListener
    ) {
        val binding = DialogNotificationFilterBinding.inflate(fragment.layoutInflater)
        binding.keywords.setText(currentKeywords.orEmpty())
        when (currentMode) {
            NotificationMode.ENABLED -> binding.modeAll.isChecked = true
            NotificationMode.KEYWORDS_ONLY -> binding.modeKeywords.isChecked = true
            else -> binding.modeDisabled.isChecked = true
        }

        fun updateKeywordVisibility() {
            binding.keywordsLayout.visibility = if (binding.modeKeywords.isChecked) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        binding.modeGroup.setOnCheckedChangeListener { _, _ ->
            binding.keywordsLayout.error = null
            updateKeywordVisibility()
        }
        updateKeywordVisibility()

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val mode = when (binding.modeGroup.checkedRadioButtonId) {
                    R.id.mode_all -> NotificationMode.ENABLED
                    R.id.mode_keywords -> NotificationMode.KEYWORDS_ONLY
                    else -> NotificationMode.DISABLED
                }
                val normalizedKeywords = NotificationKeywordFilter.normalize(
                    binding.keywords.text?.toString().orEmpty()
                )
                if (
                    mode == NotificationMode.KEYWORDS_ONLY &&
                    !NotificationKeywordFilter.isValid(normalizedKeywords)
                ) {
                    binding.keywordsLayout.error =
                        fragment.getString(R.string.notification_keywords_invalid)
                    return@setOnClickListener
                }
                saveListener.onSave(mode, normalizedKeywords)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
