package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ListEmptyViewBinding

class SearchNoResultsPlaceholderItem : BindableItem<ListEmptyViewBinding>() {
    override fun getLayout(): Int = R.layout.list_empty_view

    override fun bind(viewBinding: ListEmptyViewBinding, position: Int) {
        viewBinding.emptyStateMessage.setText(R.string.search_no_results)
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun initializeViewBinding(view: View) = ListEmptyViewBinding.bind(view)
}
