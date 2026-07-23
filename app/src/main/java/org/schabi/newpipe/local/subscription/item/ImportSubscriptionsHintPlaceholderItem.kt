package org.schabi.newpipe.local.subscription.item

import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import org.schabi.newpipe.R

/**
 * When there are no subscriptions, show a hint to the user about how to import subscriptions
 */
class ImportSubscriptionsHintPlaceholderItem : Item<GroupieViewHolder>() {
    override fun getLayout(): Int = R.layout.list_empty_view_subscriptions

    override fun bind(viewHolder: GroupieViewHolder, position: Int) = Unit

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount
}
