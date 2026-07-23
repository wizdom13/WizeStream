package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.databinding.FragmentChannelTabBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.StreamListFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo>
        implements PlaylistControlViewHolder {

    // states must be protected and not private for State being able to access them
    @State
    protected ListLinkHandler tabHandler;
    @State
    protected String channelName;
    @State
    protected StreamListFilter selectedStreamFilter = StreamListFilter.NONE;

    private FragmentChannelTabBinding binding;
    private PlaylistControlBinding playlistControlBinding;
    private final List<InfoItem> unfilteredItems = new ArrayList<>();
    private final Map<String, StreamStateEntity> streamStates = new HashMap<>();
    private HistoryRecordManager historyRecordManager;
    private Disposable streamStateWorker;

    @NonNull
    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding = FragmentChannelTabBinding.bind(rootView);
        historyRecordManager = new HistoryRecordManager(requireContext());
        binding.streamFilterChips.getRoot().setVisibility(
                ChannelTabHelper.isStreamsTab(tabHandler) ? View.VISIBLE : View.GONE);
        if (selectedStreamFilter != StreamListFilter.NONE) {
            binding.streamFilterChips.streamFilterChipGroup
                    .check(selectedStreamFilter.getChipId());
        }
        binding.streamFilterChips.streamFilterChipGroup
                .setOnCheckedStateChangeListener((group, checkedIds) -> {
                    selectedStreamFilter = StreamListFilter.fromChipId(
                            checkedIds.isEmpty() ? View.NO_ID : checkedIds.get(0));
                    applyStreamFilter();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
            streamStateWorker = null;
        }
        binding = null;
        playlistControlBinding = null;
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        unfilteredItems.clear();
        streamStates.clear();
        super.startLoading(forceLoad);
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            playlistControlBinding = PlaylistControlBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            return playlistControlBinding::getRoot;
        }
        return null;
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage);
    }

    @Override
    public void setTitle(final String title) {
        // The channel name is displayed as title in the toolbar.
        // The title is always a description of the content of the tab fragment.
        // It should be unique for each channel because multiple channel tabs
        // can be added to the main page. Therefore, the channel name is used.
        // Using the title variable would cause the title to be the same for all channel tabs.
        super.setTitle(channelName);
    }

    @Override
    public void handleResult(@NonNull final ChannelTabInfo result) {
        super.handleResult(result);
        unfilteredItems.clear();
        unfilteredItems.addAll(result.getRelatedItems());
        refreshStreamStates();

        // Latest WizeStreamExtractor no longer uses raw-data-ready channel tab handlers;
        // keep the fetched ListLinkHandler from ChannelInfo as-is.

        if (playlistControlBinding != null) {
            // PlaylistControls should be visible only if there is some item in
            // infoListAdapter other than header
            if (infoListAdapter.getItemCount() > 1) {
                playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
            } else {
                playlistControlBinding.getRoot().setVisibility(View.GONE);
            }

            PlayButtonHelper.initPlaylistControlClickListener(
                    activity, playlistControlBinding, this);
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<InfoItem> result) {
        super.handleNextItems(result);
        unfilteredItems.addAll(result.getItems());
        refreshStreamStates();
    }

    private void refreshStreamStates() {
        if (!ChannelTabHelper.isStreamsTab(tabHandler)) {
            return;
        }
        final List<InfoItem> snapshot = new ArrayList<>(unfilteredItems);
        applyStreamFilter();
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
        }
        streamStateWorker = historyRecordManager.loadStreamStateBatch(snapshot)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (!unfilteredItems.equals(snapshot)) {
                        return;
                    }
                    streamStates.clear();
                    for (int i = 0; i < snapshot.size(); i++) {
                        streamStates.put(snapshot.get(i).getUrl(), states.get(i));
                    }
                    applyStreamFilter();
                }, throwable -> Log.w(TAG, "Unable to load channel stream states", throwable));
    }

    private void applyStreamFilter() {
        if (!ChannelTabHelper.isStreamsTab(tabHandler) || infoListAdapter == null) {
            return;
        }
        final List<InfoItem> displayedItems = unfilteredItems.stream()
                .filter(item -> selectedStreamFilter == StreamListFilter.NONE
                        || item instanceof StreamInfoItem
                        && StreamListFilter.matches(selectedStreamFilter,
                                (StreamInfoItem) item, streamStates.get(item.getUrl())))
                .collect(Collectors.toList());
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(displayedItems);
        showListFooter(hasMoreItems());
        if (displayedItems.isEmpty()) {
            showEmptyState();
        } else {
            hideLoading();
        }
        if (playlistControlBinding != null) {
            playlistControlBinding.getRoot().setVisibility(
                    displayedItems.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelTabPlayQueue(currentInfo.getServiceId(), tabHandler,
                currentInfo.getNextPage(), streamItems, 0);
    }
}
