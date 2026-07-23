package org.schabi.newpipe.local.subscription.item

import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import org.schabi.newpipe.R

class SearchNoResultsPlaceholderItem : Item<GroupieViewHolder>() {
    override fun getLayout(): Int = R.layout.list_search_no_results

    override fun bind(viewHolder: GroupieViewHolder, position: Int) = Unit

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount
}
