package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedGroupImportGridItemBinding

class FeedGroupImportGridItem : BindableItem<FeedGroupImportGridItemBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_import_grid_item
    override fun initializeViewBinding(view: View) = FeedGroupImportGridItemBinding.bind(view)
    override fun bind(viewBinding: FeedGroupImportGridItemBinding, position: Int) {
        // this is a static item, nothing to do here
    }
}
