package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture

class GroupChannelsFragment : Fragment() {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!
    private val adapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var viewModel: GroupChannelsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_subscription, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSubscriptionBinding.bind(view)
        val groupId = arguments?.getLong(KEY_GROUP_ID) ?: FeedGroupEntity.GROUP_ALL_ID
        val groupName = arguments?.getString(KEY_GROUP_NAME).orEmpty()

        requireActivity().title = if (groupName.isBlank()) {
            getString(R.string.channels)
        } else {
            groupName
        }

        binding.itemsList.layoutManager = GridLayoutManager(requireContext(), 1)
        binding.itemsList.adapter = adapter

        viewModel = ViewModelProvider(this)[GroupChannelsViewModel::class.java]
        viewModel.channels.observe(viewLifecycleOwner) { channels ->
            adapter.update(
                channels.map { channel ->
                    ChannelItem(channel, -1, ChannelItem.ItemVersion.MINI).apply {
                        gesturesListener = object : OnClickGesture<ChannelInfoItem> {
                            override fun selected(
                                selectedItem: ChannelInfoItem
                            ) = NavigationHelper.openChannelFragment(
                                parentFragmentManager,
                                selectedItem.serviceId,
                                selectedItem.url,
                                selectedItem.name
                            )
                        }
                    }
                }
            )
        }
        viewModel.load(groupId)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_GROUP_NAME = "group_name"

        fun newInstance(groupId: Long, groupName: String): GroupChannelsFragment =
            GroupChannelsFragment().apply {
                arguments = bundleOf(KEY_GROUP_ID to groupId, KEY_GROUP_NAME to groupName)
            }
    }
}
