/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.detail

import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.local.radio.secureRadioUrl
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.DirectMediaNavigation

/** Keeps restoration and every retry on the same direct-media path as an explicit selection. */
internal object DirectMediaDetailState {
    @JvmStatic
    fun load(
        fragment: VideoDetailFragment,
        savedItem: PlayQueueItem?,
        worker: Disposable?,
        loading: AtomicBoolean,
        binding: FragmentVideoDetailBinding?
    ): Boolean {
        val queue = DirectMediaNavigation.resolveQueue(fragment.serviceId, fragment.url, fragment.title, fragment.playQueue, savedItem)
        val item = DirectMediaNavigation.item(fragment.serviceId, fragment.url, queue)
        if (item != null) {
            worker?.dispose()
            loading.set(false)
            fragment.setInitialData(item.serviceId, item.url, item.title, queue)
            if (binding != null) fragment.prepareAndHandleLocalMedia(item, false)
            return true
        }
        if (fragment.serviceId < 0) {
            // A missing local descriptor or an unknown direct source cannot be repaired by an
            // online extractor. In particular, -1 is also the unselected-service sentinel.
            worker?.dispose()
            loading.set(false)
            fragment.setInitialData(fragment.serviceId, fragment.url, fragment.title, null)
            if (binding != null) fragment.showTextError(fragment.getString(R.string.content_not_available))
            return true
        }
        return false
    }

    @JvmStatic
    fun isRadio(serviceId: Int, url: String?, queue: PlayQueue?): Boolean = if (queue != null) {
        DirectMediaNavigation.item(serviceId, url, queue)?.isRadio == true
    } else {
        serviceId == PlayQueueItem.RADIO_SERVICE_ID && secureRadioUrl(url.orEmpty()) != null
    }

    @JvmStatic
    fun isRadio(queue: PlayQueue?): Boolean = queue?.item?.let {
        isRadio(it.serviceId, it.url, queue)
    } == true

    @JvmStatic
    fun needsRefresh(current: PlayQueueItem?, incoming: PlayQueueItem?): Boolean = incoming?.isDirectMedia == true && incoming !== current

    @JvmStatic
    fun sheetState(state: Int, radio: Boolean): Int = if (radio && state != BottomSheetBehavior.STATE_HIDDEN) {
        BottomSheetBehavior.STATE_COLLAPSED
    } else {
        state
    }

    @JvmStatic
    fun updateSheet(binding: FragmentVideoDetailBinding?, behavior: BottomSheetBehavior<FrameLayout>?, radio: Boolean) {
        if (behavior == null) return
        behavior.isDraggable = !radio
        val state = sheetState(behavior.state, radio)
        if (state != behavior.state) behavior.state = state
        val canExpand = !radio && state == BottomSheetBehavior.STATE_COLLAPSED
        binding?.let {
            it.overlayThumbnail.isClickable = canExpand
            it.overlayThumbnail.isLongClickable = canExpand
            it.overlayMetadataLayout.isClickable = canExpand
            it.overlayMetadataLayout.isLongClickable = canExpand
            it.overlayButtonsLayout.isClickable = canExpand
        }
    }
}
