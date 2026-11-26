package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.DialogSubscriptionLayoutBinding
import org.schabi.newpipe.databinding.DialogSubscriptionSeparatorBinding
import org.schabi.newpipe.databinding.ItemSubscriptionLayoutBinding
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.subscription.SubscriptionLayout
import org.schabi.newpipe.local.subscription.SubscriptionManager

/** Edits the whole profile so service selection and search cannot discard hidden positions. */
class SubscriptionLayoutDialog : DialogFragment() {
    private val disposables = CompositeDisposable()
    private val entries = mutableListOf<SubscriptionLayout.Entry>()
    private var channels = emptyMap<String, SubscriptionEntity>()
    private var restored = false
    private var alphabetical = false
    private var loaded = false
    private val adapter = LayoutAdapter()
    private lateinit var store: SubscriptionLayout
    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = source.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in entries.indices || to !in entries.indices) return false
            entries.add(to, entries.removeAt(from))
            alphabetical = false
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SubscriptionLayout(requireContext(), requireArguments().getString("profileId")!!)
        restored = savedInstanceState?.containsKey("entries") == true
        entries.addAll(if (restored) SubscriptionLayout.decode(savedInstanceState?.getString("entries")) else store.read())
        alphabetical = savedInstanceState?.getBoolean("alphabetical") ?: false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = DialogSubscriptionLayoutBinding.inflate(LayoutInflater.from(builder.context))
        binding.subscriptionEntries.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SubscriptionLayoutDialog.adapter
            touchHelper.attachToRecyclerView(this)
        }
        binding.subscriptionAddSeparator.setOnClickListener { if (loaded) editSeparator(null) }
        binding.subscriptionResetOrder.setOnClickListener {
            if (loaded) {
                entries.clear()
                entries.addAll(channels.keys.map { SubscriptionLayout.Entry(it) })
                alphabetical = true
                adapter.notifyDataSetChanged()
            }
        }
        binding.subscriptionMutedBottom.setOnClickListener { moveMutedChannelsToBottom() }
        return builder
            .setTitle(R.string.subscription_arrange).setView(binding.root)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
    }

    private fun moveMutedChannelsToBottom() {
        if (!loaded) return
        val mutedKeys = channels.filterValues { it.feedMuted }.keys
        val arranged = SubscriptionLayout.moveMutedChannelsToBottom(entries, mutedKeys)
        if (arranged == entries) return
        entries.clear()
        entries.addAll(arranged)
        alphabetical = false
        adapter.notifyDataSetChanged()
    }

    override fun onStart() {
        super.onStart()
        val save = (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        save.isEnabled = loaded
        save.setOnClickListener {
            store.save(if (alphabetical) emptyList() else entries.toList())
            dismiss()
        }
        disposables.add(
            SubscriptionManager(requireContext(), requireArguments().getString("profileId")!!)
                .getSubscriptionsForScope(FeedScope(FeedScope.ALL_SERVICES, 0)).firstOrError()
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ subscriptions ->
                    channels = subscriptions.associateBy(SubscriptionLayout::key)
                    val arranged = SubscriptionLayout.reconcile(entries, channels.keys.toList())
                    entries.clear()
                    entries.addAll(arranged)
                    loaded = true
                    save.isEnabled = true
                    adapter.notifyDataSetChanged()
                }, {
                    Toast.makeText(context, R.string.subscription_order_load_failed, Toast.LENGTH_LONG).show()
                })
        )
    }

    private fun editSeparator(entry: SubscriptionLayout.Entry?) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = DialogSubscriptionSeparatorBinding.inflate(LayoutInflater.from(builder.context))
        val input = binding.separatorName.apply { setText(entry?.separator) }
        builder.setTitle(R.string.subscription_separator_name).setView(binding.root)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
        if (entry != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                entries.removeAll { it.key == entry.key }
                alphabetical = false
                adapter.notifyDataSetChanged()
            }
        }
        val editor = builder.show()
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString().orEmpty().trim()
            if (name.isEmpty()) {
                binding.separatorNameLayout.error = getString(R.string.subscription_separator_name)
                return@setOnClickListener
            }
            val updated = SubscriptionLayout.Entry(entry?.key ?: "separator:${UUID.randomUUID()}", name)
            val index = entries.indexOfFirst { it.key == updated.key }
            if (index < 0) entries.add(updated) else entries[index] = updated
            alphabetical = false
            adapter.notifyDataSetChanged()
            editor.dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("entries", SubscriptionLayout.encode(entries))
        outState.putBoolean("alphabetical", alphabetical)
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    override fun onDestroyView() {
        touchHelper.attachToRecyclerView(null)
        super.onDestroyView()
    }

    private inner class LayoutAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount(): Int = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val binding = ItemSubscriptionLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = RowHolder(binding.root, binding.subscriptionLabel)
            binding.subscriptionDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                    true
                } else {
                    false
                }
            }
            binding.root.setOnClickListener {
                entries.getOrNull(holder.bindingAdapterPosition)?.takeIf { it.separator != null }?.let(::editSeparator)
            }
            return holder
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val entry = entries[position]
            holder.label.text = entry.separator ?: channels[entry.key]?.name.orEmpty()
            holder.label.setTextAppearance(
                if (entry.separator != null) {
                    com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
                } else {
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
                }
            )
            holder.label.setTypeface(holder.label.typeface, if (entry.separator != null) Typeface.BOLD else Typeface.NORMAL)
            holder.label.setTextColor(
                MaterialColors.getColor(
                    holder.label,
                    if (entry.separator != null) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface
                )
            )
            holder.itemView.isClickable = entry.separator != null
            holder.itemView.isFocusable = entry.separator != null
        }
    }

    private class RowHolder(view: View, val label: TextView) : RecyclerView.ViewHolder(view)

    companion object {
        fun newInstance(profileId: String): SubscriptionLayoutDialog = SubscriptionLayoutDialog().apply {
            arguments = bundleOf("profileId" to profileId)
        }
    }
}
