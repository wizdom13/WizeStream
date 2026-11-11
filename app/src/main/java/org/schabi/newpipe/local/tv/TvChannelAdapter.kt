package org.schabi.newpipe.local.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.util.image.CoilHelper

internal class TvChannelAdapter(private val play: (TvChannel) -> Unit, private val actions: (TvChannel) -> Unit) :
    ListAdapter<TvChannel, TvChannelAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_tv_channel, parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = getItem(position)
        holder.name.text = channel.name
        holder.group.text = channel.group
        holder.group.isVisible = channel.group.isNotBlank()
        CoilHelper.loadThumbnail(holder.logo, channel.logo)
        holder.itemView.setOnClickListener { play(channel) }
        holder.itemView.setOnLongClickListener {
            actions(channel)
            true
        }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_channel_title)
        val group: TextView = view.findViewById(R.id.tv_channel_details)
        val logo: ImageView = view.findViewById(R.id.tv_channel_logo)
    }
    private object Diff : DiffUtil.ItemCallback<TvChannel>() {
        override fun areItemsTheSame(old: TvChannel, new: TvChannel): Boolean = old.url == new.url
        override fun areContentsTheSame(old: TvChannel, new: TvChannel): Boolean = old == new
    }
}
