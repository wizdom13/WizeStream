package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.evernote.android.state.State
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.GroupieViewHolder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.GROUP_ALL_ID
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.feed.SavedSearchFeedManager
import org.schabi.newpipe.local.search.ContextualSearchHelper
import org.schabi.newpipe.local.search.ContextualSearchable
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCarouselItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem
import org.schabi.newpipe.local.subscription.item.GroupsHeader
import org.schabi.newpipe.local.subscription.item.Header
import org.schabi.newpipe.local.subscription.item.ImportSubscriptionsHintPlaceholderItem
import org.schabi.newpipe.local.subscription.item.SearchNoResultsPlaceholderItem
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.ExtractorImageCompat

class SubscriptionFragment : BaseStateFragment<SubscriptionState>(), ContextualSearchable {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var importExportHelper: SubscriptionsImportExportHelper
    private lateinit var savedSearchFeedManager: SavedSearchFeedManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val groupAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private lateinit var carouselAdapter: GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>
    private lateinit var feedGroupsCarousel: FeedGroupCarouselItem
    private lateinit var feedGroupsSortMenuItem: GroupsHeader
    private val subscriptionsSection = Section()
    private var contextualSearchQuery = ""

    @State
    @JvmField
    var itemsListState: Parcelable? = null

    @State
    @JvmField
    var feedGroupsCarouselState: Parcelable? = null

    init {
        setHasOptionsMenu(true)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(requireContext())
        importExportHelper = SubscriptionsImportExportHelper(this)
        savedSearchFeedManager = SavedSearchFeedManager(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_subscription, container, false)
    }

    override fun onPause() {
        super.onPause()
        itemsListState = binding.itemsList.layoutManager?.onSaveInstanceState()
        feedGroupsCarouselState = feedGroupsCarousel.onSaveInstanceState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    // ////////////////////////////////////////////////////////////////////////
    // Menu
    // ////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.tab_subscriptions)

        setClickListenerToMenuItem(menu.add(R.string.saved_search_feeds)) {
            showSavedSearchFeedsDialog()
        }.setIcon(R.drawable.ic_search)
        buildImportExportMenu(menu)
    }

    private fun showSavedSearchFeedsDialog() {
        disposables.add(
            savedSearchFeedManager.getAll()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { feeds ->
                        if (feeds.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                R.string.no_saved_search_feeds,
                                Toast.LENGTH_LONG
                            ).show()
                            return@subscribe
                        }

                        val labels = feeds.map { feed ->
                            getString(
                                R.string.saved_search_feed_item,
                                feed.name,
                                feed.query
                            )
                        }.toTypedArray()
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.saved_search_feeds)
                            .setItems(labels) { _, index ->
                                NavigationHelper.openSavedSearchFeed(fm, feeds[index])
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    },
                    {
                        Toast.makeText(
                            requireContext(),
                            R.string.saved_search_feed_load_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
        )
    }

    private fun buildImportExportMenu(menu: Menu) {
        // -- Import --
        val importSubMenu = menu.addSubMenu(R.string.import_from)

        addMenuItemToSubmenu(importSubMenu, R.string.previous_export) { importExportHelper.onImportPreviousSelected() }
            .setIcon(R.drawable.ic_backup)

        for (service in ServiceHelper.getVisibleServices()) {
            val subscriptionExtractor = service.subscriptionExtractor ?: continue

            val supportedSources = subscriptionExtractor.supportedSources
            if (supportedSources.isEmpty()) continue

            addMenuItemToSubmenu(importSubMenu, service.serviceInfo.name) {
                onImportFromServiceSelected(service.serviceId)
            }
                .setIcon(ServiceHelper.getIcon(service.serviceId))
        }

        // -- Export --
        val exportSubMenu = menu.addSubMenu(R.string.export_to)

        addMenuItemToSubmenu(exportSubMenu, R.string.file) { importExportHelper.onExportSelected() }
            .setIcon(R.drawable.ic_save)
    }

    private fun addMenuItemToSubmenu(
        subMenu: SubMenu,
        @StringRes title: Int,
        onClick: Runnable
    ): MenuItem {
        return setClickListenerToMenuItem(subMenu.add(title), onClick)
    }

    private fun addMenuItemToSubmenu(
        subMenu: SubMenu,
        title: String,
        onClick: Runnable
    ): MenuItem {
        return setClickListenerToMenuItem(subMenu.add(title), onClick)
    }

    private fun setClickListenerToMenuItem(
        menuItem: MenuItem,
        onClick: Runnable
    ): MenuItem {
        menuItem.setOnMenuItemClickListener {
            onClick.run()
            true
        }
        return menuItem
    }

    private fun onImportFromServiceSelected(serviceId: Int) {
        val fragmentManager = fm
        NavigationHelper.openSubscriptionsImportFragment(fragmentManager, serviceId)
    }

    private fun openReorderDialog() {
        FeedGroupReorderDialog().show(parentFragmentManager, null)
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        _binding = FragmentSubscriptionBinding.bind(rootView)

        val gridMode = SubscriptionViewModel.shouldUseGridForSubscription(requireContext())
        val minimumItemWidth = resources.getDimensionPixelSize(
            R.dimen.channel_item_grid_min_width
        )
        binding.itemsList.layoutManager = if (gridMode) {
            GridLayoutManagerHelper.create(binding.itemsList, minimumItemWidth) { spanCount ->
                groupAdapter.spanCount = spanCount
                groupAdapter.spanSizeLookup
            }
        } else {
            groupAdapter.spanCount = 1
            GridLayoutManager(requireContext(), 1).apply {
                spanSizeLookup = groupAdapter.spanSizeLookup
            }
        }
        binding.itemsList.adapter = groupAdapter
        binding.itemsList.itemAnimator = null

        viewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]
        viewModel.setFilterQuery(contextualSearchQuery)
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(this::handleResult) }
        viewModel.feedGroupsLiveData.observe(viewLifecycleOwner) {
            it?.let { (groups, listViewMode) ->
                handleFeedGroups(groups, listViewMode)
            }
        }

        setupInitialLayout()
    }

    private fun setupInitialLayout() {
        Section().apply {
            carouselAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()

            carouselAdapter.setOnItemClickListener { item, _ ->
                when (item) {
                    is FeedGroupCardItem ->
                        NavigationHelper.openFeedFragment(fm, item.groupId, item.name)

                    is FeedGroupCardGridItem ->
                        NavigationHelper.openFeedFragment(fm, item.groupId, item.name)

                    is FeedGroupAddNewItem ->
                        FeedGroupDialog.newInstance().show(fm, null)

                    is FeedGroupAddNewGridItem ->
                        FeedGroupDialog.newInstance().show(fm, null)

                    is FeedGroupImportItem, is FeedGroupImportGridItem ->
                        importExportHelper.onImportPreviousSelected()
                }
            }
            carouselAdapter.setOnItemLongClickListener { item, _ ->
                if ((item is FeedGroupCardItem && item.groupId == GROUP_ALL_ID) ||
                    (item is FeedGroupCardGridItem && item.groupId == GROUP_ALL_ID)
                ) {
                    return@setOnItemLongClickListener false
                }

                when (item) {
                    is FeedGroupCardItem ->
                        FeedGroupDialog.newInstance(item.groupId).show(fm, null)

                    is FeedGroupCardGridItem ->
                        FeedGroupDialog.newInstance(item.groupId).show(fm, null)
                }
                return@setOnItemLongClickListener true
            }

            feedGroupsCarousel = FeedGroupCarouselItem(
                carouselAdapter = carouselAdapter,
                listViewMode = viewModel.getListViewMode()
            )

            feedGroupsSortMenuItem = GroupsHeader(
                title = getString(R.string.feed_groups_header_title),
                onSortClicked = ::openReorderDialog,
                onToggleListViewModeClicked = ::toggleListViewMode,
                listViewMode = viewModel.getListViewMode()
            )

            add(Section(feedGroupsSortMenuItem, listOf(feedGroupsCarousel)))
            groupAdapter.clear()
            groupAdapter.add(this)
        }

        subscriptionsSection.setPlaceholder(ImportSubscriptionsHintPlaceholderItem())
        subscriptionsSection.setHideWhenEmpty(true)

        groupAdapter.add(
            Section(
                Header(getString(R.string.tab_subscriptions)),
                listOf(subscriptionsSection)
            )
        )
    }

    override fun setContextualSearchQuery(query: String) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query)
        subscriptionsSection.setPlaceholder(
            if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                SearchNoResultsPlaceholderItem()
            } else {
                ImportSubscriptionsHintPlaceholderItem()
            }
        )
        if (::viewModel.isInitialized) {
            viewModel.setFilterQuery(contextualSearchQuery)
        }
    }

    private fun toggleListViewMode() {
        viewModel.setListViewMode(!viewModel.getListViewMode())
    }

    private fun showLongTapDialog(selectedItem: ChannelInfoItem) {
        val commands = arrayOf(
            getString(R.string.share),
            getString(R.string.open_in_browser),
            getString(R.string.unsubscribe)
        )

        val actions = DialogInterface.OnClickListener { _, i ->
            when (i) {
                0 -> ShareUtils.shareText(
                    requireContext(),
                    selectedItem.name,
                    selectedItem.url,
                    ExtractorImageCompat.thumbnailImages(selectedItem)
                )

                1 -> ShareUtils.openUrlInBrowser(requireContext(), selectedItem.url)

                2 -> deleteChannel(selectedItem)
            }
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(requireContext()))
        dialogTitleBinding.root.isSelected = true
        dialogTitleBinding.itemTitleView.text = selectedItem.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands, actions)
            .show()
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteSubscription(selectedItem.serviceId, selectedItem.url).subscribe {
                Toast.makeText(requireContext(), getString(R.string.channel_unsubscribed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun doInitialLoadLogic() = Unit
    override fun startLoading(forceLoad: Boolean) = Unit

    private val listenerChannelItem = object : OnClickGesture<ChannelInfoItem> {
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(
            fm,
            selectedItem.serviceId,
            selectedItem.url,
            selectedItem.name
        )

        override fun held(selectedItem: ChannelInfoItem) = showLongTapDialog(selectedItem)
    }

    override fun handleResult(result: SubscriptionState) {
        super.handleResult(result)

        when (result) {
            is SubscriptionState.LoadedState -> {
                result.subscriptions.forEach {
                    if (it is ChannelItem) {
                        it.gesturesListener = listenerChannelItem
                        it.itemVersion = if (SubscriptionViewModel.shouldUseGridForSubscription(requireContext())) {
                            ChannelItem.ItemVersion.GRID
                        } else {
                            ChannelItem.ItemVersion.MINI
                        }
                    }
                }

                subscriptionsSection.update(result.subscriptions)
                subscriptionsSection.setHideWhenEmpty(false)

                if (itemsListState != null) {
                    binding.itemsList.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
                }
            }

            is SubscriptionState.ErrorState -> {
                result.error?.let {
                    showError(ErrorInfo(result.error, UserAction.SOMETHING_ELSE, "Subscriptions"))
                }
            }
        }
    }

    private fun handleFeedGroups(groups: List<Group>, listViewMode: Boolean) {
        if (feedGroupsCarouselState != null) {
            feedGroupsCarousel.onRestoreInstanceState(feedGroupsCarouselState)
            feedGroupsCarouselState = null
        }

        binding.itemsList.post {
            if (context == null) {
                // since this part was posted to the next UI cycle, the fragment might have been
                // removed in the meantime
                return@post
            }

            feedGroupsCarousel.listViewMode = listViewMode
            feedGroupsSortMenuItem.showSortButton = groups.size > 1
            feedGroupsSortMenuItem.listViewMode = listViewMode
            feedGroupsCarousel.notifyChanged(FeedGroupCarouselItem.PAYLOAD_UPDATE_LIST_VIEW_MODE)
            feedGroupsSortMenuItem.notifyChanged(GroupsHeader.PAYLOAD_UPDATE_ICONS)

            // update items here to prevent flickering
            carouselAdapter.apply {
                clear()
                if (listViewMode) {
                    add(FeedGroupAddNewItem())
                    add(FeedGroupImportItem())
                    add(FeedGroupCardItem(GROUP_ALL_ID, getString(R.string.all), FeedGroupIcon.WHATS_NEW))
                } else {
                    add(FeedGroupAddNewGridItem())
                    add(FeedGroupImportGridItem())
                    add(FeedGroupCardGridItem(GROUP_ALL_ID, getString(R.string.all), FeedGroupIcon.WHATS_NEW))
                }
                addAll(groups)
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Contract
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        binding.itemsList.animate(false, 100)
    }

    override fun hideLoading() {
        super.hideLoading()
        binding.itemsList.animate(true, 200)
    }

    companion object {
        val JSON_MIME_TYPE = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension("json") ?: "application/octet-stream"
    }
}
