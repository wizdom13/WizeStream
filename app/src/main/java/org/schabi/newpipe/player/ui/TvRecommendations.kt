package org.schabi.newpipe.player.ui

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.InfoListAdapter
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.util.ContentBlockingHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture

object TvRecommendations {
    @JvmStatic
    fun show(activity: AppCompatActivity, info: StreamInfo): Boolean {
        val streams = info.relatedItems.filterIsInstance<StreamInfoItem>()
        if (streams.isEmpty() || activity.isFinishing || activity.supportFragmentManager.isStateSaved) return false
        val dialog = BottomSheetDialog(activity)
        val infoAdapter = InfoListAdapter(activity, ContentBlockingHelper.Target.RELATED_ITEMS).apply {
            setItemViewMode(ItemViewMode.GRID)
            addInfoItemList(streams)
            setOnStreamSelectedListener(object : OnClickGesture<StreamInfoItem> {
                override fun selected(item: StreamInfoItem) {
                    dialog.dismiss()
                    NavigationHelper.openVideoDetailFragment(activity, activity.supportFragmentManager, item.serviceId, item.url, item.name, null, false)
                }
            })
        }
        if (infoAdapter.itemCount == 0) return false
        val list = RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, 3)
            adapter = infoAdapter
            contentDescription = activity.getString(R.string.tv_recommended_videos)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.6f).toInt())
        }
        dialog.setContentView(list)
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialog.dismiss()
            }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(observer)
            list.adapter = null
        }
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
        dialog.show()
        return true
    }
}
