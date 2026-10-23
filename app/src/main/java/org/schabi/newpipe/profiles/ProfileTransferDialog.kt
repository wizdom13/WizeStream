package org.schabi.newpipe.profiles

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R

object ProfileTransferDialog {
    @JvmStatic
    fun show(fragment: Fragment, disposables: CompositeDisposable, refresh: Runnable) {
        val context = fragment.requireContext()
        val source = ProfileManager.getActiveProfile(context)
        val targets = ProfileManager.getProfiles(context).filter { it.id != source.id }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.profile_transfer_destination)
            .setItems(targets.map { ProfileManager.getDisplayName(context, it) }.toTypedArray()) { _, index ->
                val target = targets[index]
                disposables.add(
                    ProfileTransfer.preview(context.applicationContext, source.id, target.id)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ counts ->
                            if (!fragment.isAdded) return@subscribe
                            val labels = context.resources.getStringArray(R.array.profile_transfer_types)
                            val summary = labels.indices.joinToString("\n") { i ->
                                context.getString(R.string.profile_transfer_count, labels[i], counts[i], counts[i + labels.size])
                            }
                            MaterialAlertDialogBuilder(context)
                                .setTitle(
                                    context.getString(
                                        R.string.profile_transfer_title,
                                        ProfileManager.getDisplayName(context, source),
                                        ProfileManager.getDisplayName(context, target)
                                    )
                                )
                                .setMessage(context.getString(R.string.profile_transfer_preview, summary))
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.profile_transfer_merge) { _, _ ->
                                    execute(fragment, disposables, source.id, target.id, false, refresh)
                                }
                                .setNeutralButton(R.string.profile_transfer_replace) { _, _ ->
                                    MaterialAlertDialogBuilder(context)
                                        .setTitle(R.string.profile_transfer_replace)
                                        .setMessage(context.getString(R.string.profile_transfer_replace_warning, ProfileManager.getDisplayName(context, target)))
                                        .setNegativeButton(R.string.cancel, null)
                                        .setPositiveButton(R.string.profile_transfer_replace) { _, _ ->
                                            execute(fragment, disposables, source.id, target.id, true, refresh)
                                        }.show()
                                }.show()
                        }, { error -> showError(fragment, error) })
                )
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun execute(fragment: Fragment, disposables: CompositeDisposable, source: String, destination: String, replace: Boolean, refresh: Runnable) {
        val context = fragment.requireContext()
        val progress = MaterialAlertDialogBuilder(context).setMessage(R.string.profile_transfer_working).setCancelable(false).show()
        disposables.add(
            ProfileTransfer.copy(context.applicationContext, source, destination, replace)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { progress.dismiss() }
                .subscribe({
                    if (fragment.isAdded) {
                        refresh.run()
                        MaterialAlertDialogBuilder(context).setMessage(R.string.profile_transfer_complete).setPositiveButton(android.R.string.ok, null).show()
                    }
                }, { error -> showError(fragment, error) })
        )
    }

    private fun showError(fragment: Fragment, error: Throwable) {
        if (fragment.isAdded) {
            MaterialAlertDialogBuilder(fragment.requireContext()).setTitle(R.string.profile_transfer_failed)
                .setMessage(error.localizedMessage).setPositiveButton(android.R.string.ok, null).show()
        }
    }
}
