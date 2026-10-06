package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.view.ViewCompat
import com.xwray.groupie.viewbinding.BindableItem
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.SubscriptionHeaderBinding
import org.schabi.newpipe.local.subscription.SubscriptionLayout

class SubscriptionSeparatorItem(val entry: SubscriptionLayout.Entry) : BindableItem<SubscriptionHeaderBinding>() {
    override fun getLayout(): Int = R.layout.subscription_header

    override fun bind(viewBinding: SubscriptionHeaderBinding, position: Int) {
        viewBinding.root.text = entry.separator
        ViewCompat.setAccessibilityHeading(viewBinding.root, true)
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun initializeViewBinding(view: View) = SubscriptionHeaderBinding.bind(view)
}
