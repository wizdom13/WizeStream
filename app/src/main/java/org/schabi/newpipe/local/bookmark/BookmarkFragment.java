package org.schabi.newpipe.local.bookmark;

import static org.schabi.newpipe.local.bookmark.MergedPlaylistManager.getMergedOrderedPlaylists;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.holder.LocalBookmarkPlaylistItemHolder;
import org.schabi.newpipe.local.holder.RemoteBookmarkPlaylistItemHolder;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.debounce.DebounceSavable;
import org.schabi.newpipe.util.debounce.DebounceSaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public final class BookmarkFragment extends BaseLocalListFragment<List<PlaylistLocalItem>, Void>
        implements DebounceSavable, ContextualSearchable {

    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    @State
    Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;
    private ItemTouchHelper itemTouchHelper;

    /* Have the bookmarked playlists been fully loaded from db */
    private AtomicBoolean isLoadingComplete;

    /* Gives enough time to avoid interrupting user sorting operations */
    @Nullable
    private DebounceSaver debounceSaver;

    private List<Pair<Long, LocalItem.LocalItemType>> deletedItems;
    private List<PlaylistLocalItem> completePlaylists = Collections.emptyList();
    private String contextualSearchQuery = "";
    private PlaylistCategories categories = new PlaylistCategories();
    @State
    public String selectedCategory = PlaylistCategories.ALL;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (activity == null) {
            return;
        }
        final AppDatabase database = NewPipeDatabase.getInstance(activity);
        localPlaylistManager = new LocalPlaylistManager(database,
                ProfileManager.getActiveProfileId(requireContext()));
        remotePlaylistManager = new RemotePlaylistManager(database,
                ProfileManager.getActiveProfileId(requireContext()));
        disposables = new CompositeDisposable();

        isLoadingComplete = new AtomicBoolean();
        debounceSaver = new DebounceSaver(3000, this);

        deletedItems = new ArrayList<>();
        try {
            final var preferences =
                    PreferenceManager.getDefaultSharedPreferences(requireContext());
            final String profileId = ProfileManager.getActiveProfileId(requireContext());
            final String categoryKey = PlaylistCategories.preferenceKey(profileId);
            String categoryJson = preferences.getString(categoryKey, null);
            if (categoryJson == null && ProfileManager.DEFAULT_PROFILE_ID.equals(profileId)) {
                categoryJson = preferences.getString(PlaylistCategories.PREFERENCE_KEY, "");
                if (categoryJson != null && !categoryJson.isEmpty()) {
                    preferences.edit().putString(categoryKey, categoryJson).apply();
                }
            }
            categories = PlaylistCategories.fromJson(categoryJson == null ? "" : categoryJson);
        } catch (final com.grack.nanojson.JsonParserException error) {
            Log.e("BookmarkFragment", "Could not read playlist categories", error);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {

        if (!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        if (!PlaylistCategories.ALL.equals(selectedCategory)
                && !PlaylistCategories.UNCATEGORIZED.equals(selectedCategory)
                && categories.name(selectedCategory) == null) {
            selectedCategory = PlaylistCategories.ALL;
        }
        rootView.findViewById(R.id.playlist_category_filter)
                .setOnClickListener(view -> chooseCategory(null));
        rootView.findViewById(R.id.playlist_category_manage)
                .setOnClickListener(view -> manageCategories());
        updateCategoryLabel();

        itemListAdapter.setUseItemHandle(true);
        itemListAdapter.setItemHandleEnabled(!isPlaylistListFiltered());
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final FragmentManager fragmentManager = getFM();

                if (selectedItem instanceof PlaylistMetadataEntry) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.getUid(),
                            entry.getOrderingName());

                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getOrderingName());
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (isContextualSearchActive()) {
                    return;
                }
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showLocalDialog((PlaylistMetadataEntry) selectedItem);
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    showRemoteDeleteDialog((PlaylistRemoteEntity) selectedItem);
                }
            }

            @Override
            public void drag(final LocalItem selectedItem,
                             final RecyclerView.ViewHolder viewHolder) {
                if (!isPlaylistListFiltered() && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        if (debounceSaver != null) {
            disposables.add(debounceSaver.getDebouncedSaver());
            debounceSaver.setNoChangesToSave();
        }
        isLoadingComplete.set(false);

        getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistsSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();

        // Save on exit
        saveImmediate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (disposables != null) {
            disposables.clear();
        }
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }

        databaseSubscription = null;
        itemTouchHelper = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debounceSaver != null) {
            debounceSaver.getDebouncedSaveSignal().onComplete();
        }
        if (disposables != null) {
            disposables.dispose();
        }

        debounceSaver = null;
        disposables = null;
        localPlaylistManager = null;
        remotePlaylistManager = null;
        itemsListState = null;

        isLoadingComplete = null;
        deletedItems = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistLocalItem>> getPlaylistsSubscriber() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();
                isLoadingComplete.set(false);

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<PlaylistLocalItem> subscriptions) {
                if (debounceSaver == null || !debounceSaver.getIsModified()) {
                    handleResult(subscriptions);
                    isLoadingComplete.set(true);
                }
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(new ErrorInfo(exception,
                        UserAction.REQUESTED_BOOKMARK, "Loading playlists"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistLocalItem> result) {
        super.handleResult(result);
        completePlaylists = new ArrayList<>(result);
        showFilteredPlaylists();
    }

    private void showFilteredPlaylists() {
        if (itemListAdapter == null) {
            return;
        }

        itemListAdapter.clearStreamItemList();
        itemListAdapter.setItemHandleEnabled(!isPlaylistListFiltered());
        setEmptyStateMessage(isPlaylistListFiltered()
                ? R.string.search_no_results : R.string.empty_list_subtitle);

        final List<PlaylistLocalItem> filteredPlaylists = ContextualSearchHelper.filter(
                completePlaylists.stream()
                        .filter(playlist -> categories.matches(categoryKey(playlist),
                                selectedCategory))
                        .collect(java.util.stream.Collectors.toList()),
                contextualSearchQuery,
                playlist -> new String[]{playlist.getOrderingName()});

        if (filteredPlaylists.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(filteredPlaylists);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        final String normalizedQuery = ContextualSearchHelper.normalizeQuery(query);
        if (!isPlaylistListFiltered() && ContextualSearchHelper.isActive(normalizedQuery)) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        contextualSearchQuery = normalizedQuery;
        showFilteredPlaylists();
    }

    private boolean isContextualSearchActive() {
        return ContextualSearchHelper.isActive(contextualSearchQuery);
    }

    private void captureCanonicalOrderFromAdapter() {
        if (itemListAdapter == null || isPlaylistListFiltered()) {
            return;
        }
        final List<PlaylistLocalItem> displayedOrder = new ArrayList<>();
        for (final LocalItem item : itemListAdapter.getItemsList()) {
            if (item instanceof PlaylistLocalItem) {
                displayedOrder.add((PlaylistLocalItem) item);
            }
        }
        completePlaylists = displayedOrder;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) {
            disposables.clear();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void changeLocalPlaylistName(final long id, final String name) {
        if (localPlaylistManager == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + id + "] "
                    + "with new name=[" + name + "] items");
        }

        final Disposable disposable = localPlaylistManager.renamePlaylist(id, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, throwable -> showError(
                        new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist name")));
        disposables.add(disposable);
    }

    private void deleteItem(final PlaylistLocalItem item) {
        if (itemListAdapter == null) {
            return;
        }

        if (item instanceof PlaylistRemoteEntity) {
            deleteRemoteBookmark((PlaylistRemoteEntity) item);
            return;
        }

        if (isPlaylistListFiltered()) {
            disposables.add(localPlaylistManager.updatePlaylists(Collections.emptyList(),
                            Collections.singletonList(item.getUid()))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> { }, throwable -> showError(new ErrorInfo(throwable,
                            UserAction.REQUESTED_BOOKMARK, "Deleting categorized playlist"))));
            return;
        }

        itemListAdapter.removeItem(item);

        if (item instanceof PlaylistMetadataEntry) {
            deletedItems.add(new Pair<>(item.getUid(),
                    LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM));
        }

        if (debounceSaver != null) {
            debounceSaver.setHasChangesToSave();
            saveImmediate();
        }
    }

    private void deleteRemoteBookmark(final PlaylistRemoteEntity item) {
        if (remotePlaylistManager == null || disposables == null) {
            return;
        }

        disposables.add(remotePlaylistManager.deletePlaylist(item.getUid())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                    // The database Flowable refreshes the list. Removing here also gives immediate
                    // feedback if that refresh is queued behind another emission.
                    if (itemListAdapter != null) {
                        itemListAdapter.removeItem(item);
                    }
                    completePlaylists = completePlaylists.stream()
                            .filter(playlist -> playlist.getUid() != item.getUid()
                                    || playlist.getLocalItemType()
                                    != LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM)
                            .collect(java.util.stream.Collectors.toList());
                }, throwable -> showError(new ErrorInfo(throwable,
                        UserAction.REQUESTED_BOOKMARK, "Deleting playlist bookmark"))));
    }

    @Override
    public void saveImmediate() {
        if (itemListAdapter == null || isPlaylistListFiltered()) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || debounceSaver == null
                || !isLoadingComplete.get() || !debounceSaver.getIsModified()) {
            return;
        }

        final List<LocalItem> items = itemListAdapter.getItemsList();
        final List<PlaylistMetadataEntry> localItemsUpdate = new ArrayList<>();
        final List<Long> localItemsDeleteUid = new ArrayList<>();
        final List<PlaylistRemoteEntity> remoteItemsUpdate = new ArrayList<>();
        final List<Long> remoteItemsDeleteUid = new ArrayList<>();

        // Calculate display index
        for (int i = 0; i < items.size(); i++) {
            final LocalItem item = items.get(i);

            if (item instanceof PlaylistMetadataEntry
                    && ((PlaylistMetadataEntry) item).getDisplayIndex() != i) {
                ((PlaylistMetadataEntry) item).setDisplayIndex((long) i);
                localItemsUpdate.add((PlaylistMetadataEntry) item);
            } else if (item instanceof PlaylistRemoteEntity
                    && ((PlaylistRemoteEntity) item).getDisplayIndex() != i) {
                ((PlaylistRemoteEntity) item).setDisplayIndex((long) i);
                remoteItemsUpdate.add((PlaylistRemoteEntity) item);
            }
        }

        // Find deleted items
        for (final Pair<Long, LocalItem.LocalItemType> item : deletedItems) {
            if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM)) {
                localItemsDeleteUid.add(item.first);
            } else if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM)) {
                remoteItemsDeleteUid.add(item.first);
            }
        }

        deletedItems.clear();

        // 1. Update local playlists
        // 2. Update remote playlists
        // 3. Set NoChangesToSave
        disposables.add(localPlaylistManager.updatePlaylists(localItemsUpdate, localItemsDeleteUid)
                .mergeWith(remotePlaylistManager.updatePlaylists(
                        remoteItemsUpdate, remoteItemsDeleteUid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                            if (debounceSaver != null) {
                                debounceSaver.setNoChangesToSave();
                            }
                        },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                ));

    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int getDragDirs(@NonNull final RecyclerView recyclerView,
                                   @NonNull final RecyclerView.ViewHolder viewHolder) {
                if (isPlaylistListFiltered()) {
                    return 0;
                }
                final int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return recyclerView.getLayoutManager() instanceof GridLayoutManager
                        ? directions | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT : directions;
            }

            @Override
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView,
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {

                // Allow swap LocalBookmarkPlaylistItemHolder and RemoteBookmarkPlaylistItemHolder.
                if (isPlaylistListFiltered() || itemListAdapter == null
                        || source.getItemViewType() != target.getItemViewType()
                        && !(
                        (
                                (source instanceof LocalBookmarkPlaylistItemHolder)
                                        || (source instanceof RemoteBookmarkPlaylistItemHolder)
                        )
                                && (
                                (target instanceof LocalBookmarkPlaylistItemHolder)
                                        || (target instanceof RemoteBookmarkPlaylistItemHolder)
                        ))
                ) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped && debounceSaver != null) {
                    debounceSaver.setHasChangesToSave();
                }
                return isSwapped;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                // Do nothing.
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void showRemoteDeleteDialog(final PlaylistRemoteEntity item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(new String[]{getString(R.string.playlist_move_to_category),
                    getString(R.string.delete)}, (dialog, which) -> {
                    if (which == 0) {
                        chooseCategory(item);
                    } else {
                        showDeleteDialog(item.getOrderingName(), item);
                    }
                }).show();
    }

    private void showLocalDialog(final PlaylistMetadataEntry selectedItem) {
        final String rename = getString(R.string.rename);
        final String delete = getString(R.string.delete);
        final String unsetThumbnail = getString(R.string.unset_playlist_thumbnail);
        final String moveToCategory = getString(R.string.playlist_move_to_category);
        final boolean isThumbnailPermanent = localPlaylistManager
                .getIsPlaylistThumbnailPermanent(selectedItem.getUid());

        final ArrayList<String> items = new ArrayList<>();
        items.add(rename);
        items.add(delete);
        items.add(moveToCategory);
        if (isThumbnailPermanent) {
            items.add(unsetThumbnail);
        }

        final DialogInterface.OnClickListener action = (d, index) -> {
            if (items.get(index).equals(rename)) {
                showRenameDialog(selectedItem);
            } else if (items.get(index).equals(delete)) {
                showDeleteDialog(selectedItem.getOrderingName(), selectedItem);
            } else if (items.get(index).equals(moveToCategory)) {
                chooseCategory(selectedItem);
            } else if (isThumbnailPermanent && items.get(index).equals(unsetThumbnail)) {
                final long thumbnailStreamId = localPlaylistManager
                        .getAutomaticPlaylistThumbnailStreamId(selectedItem.getUid());
                localPlaylistManager
                        .changePlaylistThumbnail(selectedItem.getUid(), thumbnailStreamId, false)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();
            }
        };

        new AlertDialog.Builder(activity)
                .setItems(items.toArray(new String[0]), action)
                .show();
    }

    private boolean isPlaylistListFiltered() {
        return !PlaylistCategories.allowsReordering(isContextualSearchActive(), selectedCategory);
    }

    private static String categoryKey(final PlaylistLocalItem playlist) {
        if (playlist instanceof PlaylistRemoteEntity) {
            final PlaylistRemoteEntity remote = (PlaylistRemoteEntity) playlist;
            return "remote:" + remote.getServiceId() + ":" + remote.getUrl();
        }
        return "local:" + playlist.getUid();
    }

    private void updateCategoryLabel() {
        if (getView() == null) {
            return;
        }
        final MaterialButton button = getView().findViewById(R.id.playlist_category_filter);
        button.setText(PlaylistCategories.ALL.equals(selectedCategory)
                ? getString(R.string.playlist_categories_all)
                : PlaylistCategories.UNCATEGORIZED.equals(selectedCategory)
                ? getString(R.string.playlist_uncategorized) : categories.name(selectedCategory));
    }

    private void setCategoryFilter(final String id) {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        selectedCategory = id;
        updateCategoryLabel();
        showFilteredPlaylists();
    }

    private void saveCategories() {
        final String profileId = ProfileManager.getActiveProfileId(requireContext());
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putString(PlaylistCategories.preferenceKey(profileId), categories.toJson())
                .apply();
        updateCategoryLabel();
        showFilteredPlaylists();
    }

    private void chooseCategory(@Nullable final PlaylistLocalItem playlist) {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        final List<String> ids = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        if (playlist == null) {
            ids.add(PlaylistCategories.ALL);
            labels.add(getString(R.string.playlist_categories_all));
        }
        ids.add(PlaylistCategories.UNCATEGORIZED);
        labels.add(getString(R.string.playlist_uncategorized));
        for (final String id : categories.ids()) {
            ids.add(id);
            labels.add(categories.name(id));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_categories)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (playlist == null) {
                        setCategoryFilter(ids.get(which));
                    } else {
                        categories.assign(categoryKey(playlist), ids.get(which));
                        saveCategories();
                    }
                })
                .setPositiveButton(R.string.playlist_category_create,
                        (dialog, which) -> editCategory(null, playlist))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void manageCategories() {
        if (!isPlaylistListFiltered()) {
            captureCanonicalOrderFromAdapter();
            saveImmediate();
        }
        final List<String> ids = categories.ids();
        final String[] names = ids.stream().map(categories::name).toArray(String[]::new);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_categories)
                .setItems(names, (dialog, which) -> {
                    final String id = ids.get(which);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(categories.name(id))
                            .setItems(new String[]{getString(R.string.rename),
                                getString(R.string.delete)}, (actionDialog, action) -> {
                                if (action == 0) {
                                    editCategory(id, null);
                                } else {
                                    deleteCategory(id);
                                }
                            }).show();
                })
                .setPositiveButton(R.string.playlist_category_create,
                        (dialog, which) -> editCategory(null, null))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void editCategory(@Nullable final String id,
                              @Nullable final PlaylistLocalItem playlist) {
        final DialogEditTextBinding input = DialogEditTextBinding.inflate(getLayoutInflater());
        input.dialogEditText.setHint(R.string.name);
        input.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (id != null) {
            input.dialogEditText.setText(categories.name(id));
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(id == null ? R.string.playlist_category_create : R.string.rename)
                .setView(input.getRoot())
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        final String name = input.dialogEditText.getText().toString();
                        if (id == null) {
                            final String created = categories.create(name);
                            if (playlist != null) {
                                categories.assign(categoryKey(playlist), created);
                            }
                        } else {
                            categories.rename(id, name);
                        }
                        saveCategories();
                        dialog.dismiss();
                    } catch (final IllegalArgumentException error) {
                        input.dialogEditText.setError(
                                getString(R.string.playlist_category_name_error));
                    }
                }));
        dialog.show();
    }

    private void deleteCategory(final String id) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(categories.name(id))
                .setMessage(R.string.playlist_category_delete_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    categories.delete(id);
                    if (id.equals(selectedCategory)) {
                        selectedCategory = PlaylistCategories.ALL;
                    }
                    saveCategories();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void showRenameDialog(final PlaylistMetadataEntry selectedItem) {
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setText(selectedItem.getOrderingName());

        new AlertDialog.Builder(activity)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.rename_playlist, (dialog, which) ->
                        changeLocalPlaylistName(
                                selectedItem.getUid(),
                                dialogBinding.dialogEditText.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(final String name, final PlaylistLocalItem item) {
        if (activity == null || disposables == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) -> deleteItem(item))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
