package org.schabi.newpipe.settings.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.ItemNotificationConfigBinding
import org.schabi.newpipe.local.feed.notifications.NotificationKeywordFilter
import org.schabi.newpipe.settings.notifications.NotificationModeConfigAdapter.SubscriptionHolder

/**
 * This [RecyclerView.Adapter] is used in the [NotificationModeConfigFragment].
 * The adapter holds all subscribed channels and their [NotificationMode]s
 * and provides the needed data structures and methods for this task.
 */
class NotificationModeConfigAdapter(
    private val listener: ConfigureListener
) : ListAdapter<SubscriptionItem, SubscriptionHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, i: Int): SubscriptionHolder {
        return SubscriptionHolder(
            ItemNotificationConfigBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SubscriptionHolder, position: Int) {
        holder.bind(currentList[position])
    }

    fun update(newData: List<SubscriptionEntity>) {
        val items = newData.map {
            SubscriptionItem(
                it.uid,
                it.name!!,
                it.notificationMode,
                it.notificationKeywords,
                it.serviceId,
                it.url!!
            )
        }
        submitList(items)
    }

    inner class SubscriptionHolder(
        private val itemBinding: ItemNotificationConfigBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onConfigure(currentList[position])
                }
            }
        }

        fun bind(data: SubscriptionItem) {
            itemBinding.title.text = data.title
            itemBinding.summary.text = when (data.notificationMode) {
                NotificationMode.ENABLED -> itemView.context.getString(
                    R.string.notification_mode_all_uploads
                )

                NotificationMode.KEYWORDS_ONLY -> itemView.context.getString(
                    R.string.notification_keywords_summary,
                    NotificationKeywordFilter.terms(data.notificationKeywords).joinToString(", ")
                )

                else -> itemView.context.getString(R.string.notification_mode_disabled)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SubscriptionItem>() {
        override fun areItemsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: SubscriptionItem, newItem: SubscriptionItem): Any? {
            return if (
                oldItem.notificationMode != newItem.notificationMode ||
                oldItem.notificationKeywords != newItem.notificationKeywords
            ) {
                newItem
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }

    fun interface ConfigureListener {
        /**
         * Triggered when the UI representation of a notification mode is changed.
         */
        fun onConfigure(item: SubscriptionItem)
    }
}

data class SubscriptionItem(
    val id: Long,
    val title: String,
    @NotificationMode
    val notificationMode: Int,
    val notificationKeywords: String,
    val serviceId: Int,
    val url: String
)
