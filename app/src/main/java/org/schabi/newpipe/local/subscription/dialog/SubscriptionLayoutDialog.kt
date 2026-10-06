package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
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
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(context).apply { setText(R.string.subscription_arrange_help) })
            addView(
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = this@SubscriptionLayoutDialog.adapter
                    touchHelper.attachToRecyclerView(this)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300))
            )
            addView(
                Button(context).apply {
                    setText(R.string.subscription_separator_add)
                    setOnClickListener { if (loaded) editSeparator(null) }
                }
            )
            addView(
                Button(context).apply {
                    setText(R.string.subscription_order_reset)
                    setOnClickListener {
                        if (loaded) {
                            entries.clear()
                            entries.addAll(channels.keys.map { SubscriptionLayout.Entry(it) })
                            alphabetical = true
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            )
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.subscription_arrange).setView(content)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
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
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.subscription_separator_name)
            setSingleLine()
            setText(entry?.separator)
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.subscription_separator_name).setView(input)
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
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = getString(R.string.subscription_separator_name)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class LayoutAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount(): Int = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val row = LinearLayout(parent.context).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                minimumHeight = dp(56)
            }
            val label = TextView(parent.context)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val handle = androidx.appcompat.widget.AppCompatImageButton(parent.context).apply {
                setImageResource(R.drawable.ic_drag_handle)
                contentDescription = getString(R.string.subscription_order_drag)
            }
            row.addView(handle, LinearLayout.LayoutParams(dp(48), dp(48)))
            val holder = RowHolder(row, label)
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                    true
                } else {
                    false
                }
            }
            row.setOnClickListener {
                entries.getOrNull(holder.bindingAdapterPosition)?.takeIf { it.separator != null }?.let(::editSeparator)
            }
            return holder
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val entry = entries[position]
            holder.label.text = entry.separator ?: channels[entry.key]?.name.orEmpty()
            holder.label.setTypeface(null, if (entry.separator != null) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private class RowHolder(view: View, val label: TextView) : RecyclerView.ViewHolder(view)

    companion object {
        fun newInstance(profileId: String): SubscriptionLayoutDialog = SubscriptionLayoutDialog().apply {
            arguments = bundleOf("profileId" to profileId)
        }
    }
}
