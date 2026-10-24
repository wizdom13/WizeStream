package org.schabi.newpipe.local.subscription

import android.content.DialogInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.util.ServiceHelper

internal class SubscriptionBulkActions(
    private val fragment: Fragment,
    private val manager: SubscriptionManager
) {
    private val disposables = CompositeDisposable()
    private var dialog: AlertDialog? = null
    private var busy = false

    fun show(query: String) {
        if (busy || fragment.view == null) return
        busy = true
        disposables.add(
            manager.getSubscriptionsForScope(FeedScope(FeedScope.ALL_SERVICES, 0), filterQuery = query)
                .firstOrError()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ channels ->
                    busy = false
                    if (fragment.view != null) {
                        showPicker(channels.sortedBy { it.name.orEmpty().lowercase() })
                    }
                }, { error ->
                    busy = false
                    showError(error)
                })
        )
    }

    private fun showPicker(channels: List<SubscriptionEntity>) {
        if (channels.isEmpty()) {
            Toast.makeText(fragment.requireContext(), R.string.subscription_bulk_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = BooleanArray(channels.size)
        val labels = channels.map {
            fragment.getString(
                R.string.subscription_bulk_channel,
                it.name ?: it.url.orEmpty(),
                ServiceHelper.getNameOfServiceById(it.serviceId)
            )
        }.toTypedArray()
        dialog?.dismiss()
        val picker = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.subscription_bulk_unsubscribe)
            .setMultiChoiceItems(labels, selected) { dialogInterface, index, checked ->
                selected[index] = checked
                (dialogInterface as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
                    .isEnabled = selected.any { it }
            }
            .setPositiveButton(R.string.unsubscribe) { _, _ ->
                confirm(channels.filterIndexed { index, _ -> selected[index] })
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        picker.setOnShowListener {
            picker.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
        }
        dialog = picker
        picker.show()
    }

    private fun confirm(channels: List<SubscriptionEntity>) {
        if (channels.isEmpty() || fragment.view == null) return
        dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.subscription_bulk_unsubscribe)
            .setMessage(
                fragment.resources.getQuantityString(
                    R.plurals.subscription_bulk_confirm,
                    channels.size,
                    channels.size
                )
            )
            .setPositiveButton(R.string.unsubscribe) { _, _ ->
                busy = true
                disposables.add(
                    manager.deleteSubscriptions(channels.map { it.uid })
                        .subscribe({
                            busy = false
                            if (fragment.view != null) {
                                Toast.makeText(
                                    fragment.requireContext(),
                                    R.string.subscription_bulk_done,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }, { error ->
                            busy = false
                            showError(error)
                        })
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showError(error: Throwable) {
        if (fragment.view != null) {
            ErrorUtil.showSnackbar(
                fragment,
                ErrorInfo(error, UserAction.SUBSCRIPTION_CHANGE, "Removing selected subscriptions")
            )
        }
    }

    fun close() {
        dialog?.dismiss()
        dialog = null
        disposables.clear()
        busy = false
    }
}
