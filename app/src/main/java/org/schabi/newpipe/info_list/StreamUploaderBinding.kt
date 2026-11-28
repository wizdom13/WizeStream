/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.info_list

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.LocalItemBuilder
import org.schabi.newpipe.local.LocalUploaderNavigation
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

/** One bounded channel action; the surrounding row and its metadata keep the video action. */
object StreamUploaderBinding {
    @JvmStatic
    fun bind(root: View, item: StreamInfoItem, listener: OnClickGesture<ChannelInfoItem>?) = bind(
        root,
        StreamUploaderNavigation.fromStream(item),
        item.uploaderName,
        ExtractorImageCompat.uploaderAvatarImages(item),
        listener?.let { selected -> { channel -> selected.selected(channel) } }
    )

    fun bind(
        root: View,
        channel: ChannelInfoItem?,
        uploaderName: String?,
        avatars: List<Image>,
        onSelected: ((ChannelInfoItem) -> Unit)?
    ) {
        val action: (() -> Unit)? = if (channel != null && onSelected != null) {
            { onSelected(channel) }
        } else {
            null
        }
        bindTarget(root, uploaderName, avatars, action)
    }

    @JvmStatic
    fun bind(root: View, item: StreamStatisticsEntry, builder: LocalItemBuilder) {
        val stream = item.streamEntity
        // History can resolve sparse uploader URLs from the video. Keep that existing lookup
        // and re-read the builder on click so contextual selection mode still owns its actions.
        val canOpen = LocalUploaderNavigation.canOpenChannel(stream) && builder.onUploaderSelectedListener != null
        val action: (() -> Unit)? = if (canOpen) {
            { builder.onUploaderSelectedListener?.accept(item) }
        } else {
            null
        }
        bindTarget(root, stream.uploader, ImageStrategy.dbUrlToImageList(stream.uploaderAvatarUrl), action)
    }

    private fun bindTarget(root: View, uploaderName: String?, avatars: List<Image>, onSelected: (() -> Unit)?) {
        // The mini layout deliberately retains its compact, text-only presentation.
        val target = root.findViewById<View>(R.id.itemUploaderLink) ?: return
        val avatar = root.findViewById<ImageView>(R.id.itemUploaderAvatarView)
        val name = root.findViewById<TextView>(R.id.itemUploaderView)
        val container = root.findViewById<View>(R.id.itemUploaderRoot)
        val hasAvatar = avatars.any { it.url.isNotBlank() }
        val label = uploaderName?.takeIf { it.isNotBlank() }
        val canOpen = onSelected != null

        clearInteraction(target)
        clearInteraction(container)
        clearInteraction(avatar)
        clearInteraction(name)
        container.contentDescription = null
        avatar.contentDescription = null
        name.contentDescription = null

        if (hasAvatar) {
            avatar.visibility = View.VISIBLE
            CoilHelper.loadAvatar(avatar, avatars)
        } else {
            CoilHelper.clearAvatar(avatar)
            avatar.visibility = View.GONE
        }
        name.text = label ?: if (canOpen) {
            root.context.getString(R.string.show_channel_details)
        } else {
            ""
        }
        target.visibility = if (name.text.isNotEmpty() || hasAvatar) View.VISIBLE else View.GONE
        alignStackedMetadata(root, target, avatar, name)

        target.isEnabled = canOpen
        target.isClickable = canOpen
        target.isFocusable = canOpen
        target.importantForAccessibility = if (canOpen) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        target.contentDescription = if (canOpen) {
            label?.let { root.context.getString(R.string.open_channel, it) }
                ?: root.context.getString(R.string.show_channel_details)
        } else {
            null
        }
        // Expose one accessible action for the complete target, without duplicate text/avatar stops.
        avatar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        name.importantForAccessibility = if (canOpen) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (onSelected != null) {
            target.setOnClickListener { onSelected() }
        }
    }

    @JvmStatic
    fun clear(root: View) {
        root.findViewById<View>(R.id.itemUploaderLink)?.let { target ->
            clearInteraction(target)
            target.isEnabled = false
            target.contentDescription = null
            target.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun clearInteraction(view: View) {
        view.cancelPendingInputEvents()
        view.cancelLongPress()
        view.isPressed = false
        view.setOnClickListener(null)
        view.isClickable = false
        view.isFocusable = false
    }

    private fun alignStackedMetadata(root: View, target: View, avatar: View, name: View) {
        val details = root.findViewById<View>(R.id.itemAdditionalDetails) ?: return
        val params = details.layoutParams as ConstraintLayout.LayoutParams
        if (params.startToStart != target.id) return
        val nameParams = name.layoutParams as ConstraintLayout.LayoutParams
        val indent = target.paddingStart + if (avatar.visibility == View.VISIBLE) {
            avatar.layoutParams.width + nameParams.marginStart
        } else {
            nameParams.goneStartMargin.coerceAtLeast(0)
        }
        if (params.marginStart != indent) {
            params.marginStart = indent
            details.layoutParams = params
        }
    }
}
