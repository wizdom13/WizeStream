package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentGroupChannelsBinding
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture

class GroupChannelsFragment : Fragment() {
    private var _binding: FragmentGroupChannelsBinding? = null
    private val binding get() = _binding!!
    private val adapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var channelActions: SubscriptionChannelActions
    private lateinit var viewModel: GroupChannelsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_group_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentGroupChannelsBinding.bind(view)
        channelActions = SubscriptionChannelActions(this, SubscriptionManager(requireContext()))
        val groupId = arguments?.getLong(KEY_GROUP_ID) ?: FeedGroupEntity.GROUP_ALL_ID
        val groupName = arguments?.getString(KEY_GROUP_NAME).orEmpty()

        requireActivity().title = if (groupName.isBlank()) {
            getString(R.string.channels)
        } else {
            groupName
        }

        val gridMode = SubscriptionViewModel.shouldUseGridForSubscription(requireContext())
        binding.itemsList.layoutManager = if (gridMode) {
            GridLayoutManagerHelper.create(
                binding.itemsList,
                resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width),
                SubscriptionGridColumns.get(requireContext())
            ) { spanCount ->
                adapter.spanCount = spanCount
                adapter.spanSizeLookup
            }
        } else {
            adapter.spanCount = 1
            GridLayoutManager(requireContext(), 1).apply {
                spanSizeLookup = adapter.spanSizeLookup
            }
        }
        binding.itemsList.adapter = adapter

        viewModel = ViewModelProvider(this)[GroupChannelsViewModel::class.java]
        binding.errorPanel.errorRetryButton.apply {
            isVisible = true
            setOnClickListener { viewModel.load(groupId) }
        }
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.loadingProgressBar.isVisible = state.loading
            binding.itemsList.isVisible = !state.loading && state.error == null && state.channels.isNotEmpty()
            binding.emptyStateView.root.isVisible = !state.loading && state.error == null && state.channels.isEmpty()
            binding.errorPanel.root.isVisible = state.error != null
            adapter.update(
                state.channels.map { channel ->
                    ChannelItem(
                        channel,
                        -1,
                        if (gridMode) ChannelItem.ItemVersion.GRID else ChannelItem.ItemVersion.MINI
                    ).apply {
                        gesturesListener = object : OnClickGesture<ChannelInfoItem> {
                            override fun selected(
                                selectedItem: ChannelInfoItem
                            ) = NavigationHelper.openChannelFragment(
                                parentFragmentManager,
                                selectedItem.serviceId,
                                selectedItem.url,
                                selectedItem.name
                            )

                            override fun held(selectedItem: ChannelInfoItem) = channelActions.show(selectedItem)
                        }
                    }
                }
            )
        }
        viewModel.load(groupId)
    }

    override fun onDestroyView() {
        channelActions.close()
        binding.itemsList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_GROUP_NAME = "group_name"

        fun newInstance(groupId: Long, groupName: String): GroupChannelsFragment {
            return GroupChannelsFragment().apply {
                arguments = bundleOf(KEY_GROUP_ID to groupId, KEY_GROUP_NAME to groupName)
            }
        }
    }
}
