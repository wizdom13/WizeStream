package org.schabi.newpipe.local.subscription.item

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedGroupImportItemBinding

class FeedGroupImportItem : BindableItem<FeedGroupImportItemBinding>() {
    override fun getLayout(): Int = R.layout.feed_group_import_item
    override fun initializeViewBinding(view: View) = FeedGroupImportItemBinding.bind(view)
    override fun bind(viewBinding: FeedGroupImportItemBinding, position: Int) {
        // this is a static item, nothing to do here
    }
}
