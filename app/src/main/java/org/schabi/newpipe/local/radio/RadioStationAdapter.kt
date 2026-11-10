package org.schabi.newpipe.local.radio

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

internal class RadioStationAdapter(
    private val play: (RadioStation) -> Unit,
    private val showActions: (RadioStation) -> Unit
) : ListAdapter<RadioStation, RadioStationAdapter.Holder>(StationDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_radio_station, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val station = getItem(position)
        holder.title.text = station.name
        holder.details.text = station.details
        holder.details.isVisible = station.details.isNotBlank()
        CoilHelper.loadRadioLogo(holder.logo, station.logoUrl)
        holder.itemView.setOnClickListener { play(station) }
        holder.itemView.setOnLongClickListener {
            showActions(station)
            true
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.radio_station_logo)
        val title: TextView = view.findViewById(R.id.radio_station_title)
        val details: TextView = view.findViewById(R.id.radio_station_details)
    }

    private object StationDiff : DiffUtil.ItemCallback<RadioStation>() {
        override fun areItemsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean = oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: RadioStation, newItem: RadioStation): Boolean = oldItem == newItem
    }
}
