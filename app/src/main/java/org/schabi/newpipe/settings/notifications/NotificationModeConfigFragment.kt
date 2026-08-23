package org.schabi.newpipe.settings.notifications

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.databinding.DialogNotificationFilterBinding
import org.schabi.newpipe.databinding.FragmentChannelsNotificationsBinding
import org.schabi.newpipe.local.feed.notifications.NotificationKeywordFilter
import org.schabi.newpipe.local.subscription.SubscriptionManager

/**
 * [NotificationModeConfigFragment] is a settings fragment
 * which allows changing the [NotificationMode] of all subscribed channels.
 * The [NotificationMode] can either be changed one by one or toggled for all channels.
 */
class NotificationModeConfigFragment : Fragment() {
    private var _binding: FragmentChannelsNotificationsBinding? = null
    private val binding get() = _binding!!

    private val disposables = CompositeDisposable()
    private var loader: Disposable? = null
    private lateinit var adapter: NotificationModeConfigAdapter
    private lateinit var subscriptionManager: SubscriptionManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = NotificationModeConfigAdapter(::showNotificationConfigDialog)
        binding.recyclerView.adapter = adapter
        loader?.dispose()
        loader = subscriptionManager.subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(adapter::update)
    }

    override fun onDestroyView() {
        loader?.dispose()
        loader = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_notifications_channels, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_all -> {
                toggleAll()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAll() {
        val mode = adapter.currentList.firstOrNull()?.notificationMode ?: return
        val newMode = when (mode) {
            NotificationMode.DISABLED -> NotificationMode.ENABLED
            else -> NotificationMode.DISABLED
        }
        adapter.currentList.forEach { updateNotificationMode(it, newMode) }
    }

    private fun updateNotificationMode(item: SubscriptionItem, @NotificationMode mode: Int) {
        disposables.add(
            subscriptionManager.updateNotificationMode(item.serviceId, item.url, mode)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }

    private fun showNotificationConfigDialog(item: SubscriptionItem) {
        val dialogBinding = DialogNotificationFilterBinding.inflate(layoutInflater)
        dialogBinding.keywords.setText(item.notificationKeywords)
        when (item.notificationMode) {
            NotificationMode.ENABLED -> dialogBinding.modeAll.isChecked = true
            NotificationMode.KEYWORDS_ONLY -> dialogBinding.modeKeywords.isChecked = true
            else -> dialogBinding.modeDisabled.isChecked = true
        }

        fun updateKeywordVisibility() {
            dialogBinding.keywordsLayout.visibility = if (dialogBinding.modeKeywords.isChecked) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        dialogBinding.modeGroup.setOnCheckedChangeListener { _, _ ->
            dialogBinding.keywordsLayout.error = null
            updateKeywordVisibility()
        }
        updateKeywordVisibility()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val mode = when (dialogBinding.modeGroup.checkedRadioButtonId) {
                    R.id.mode_all -> NotificationMode.ENABLED
                    R.id.mode_keywords -> NotificationMode.KEYWORDS_ONLY
                    else -> NotificationMode.DISABLED
                }
                val normalizedKeywords = NotificationKeywordFilter.normalize(
                    dialogBinding.keywords.text?.toString().orEmpty()
                )
                if (
                    mode == NotificationMode.KEYWORDS_ONLY &&
                    !NotificationKeywordFilter.isValid(normalizedKeywords)
                ) {
                    dialogBinding.keywordsLayout.error =
                        getString(R.string.notification_keywords_invalid)
                    return@setOnClickListener
                }
                updateNotificationSettings(item, mode, normalizedKeywords)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updateNotificationSettings(
        item: SubscriptionItem,
        @NotificationMode mode: Int,
        keywords: String
    ) {
        disposables.add(
            subscriptionManager.updateNotificationSettings(
                item.serviceId,
                item.url,
                mode,
                keywords
            )
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }
}
