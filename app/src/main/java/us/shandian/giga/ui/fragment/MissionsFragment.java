package us.shandian.giga.ui.fragment;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.ui.adapter.MissionAdapter;

public class MissionsFragment extends Fragment {

    private static final String TAG = "MissionsFragment";
    private static final int SPAN_SIZE = 2;
    private static final String STATE_SEARCH_QUERY = "search_query";
    private static final String STATE_STANDALONE_SEARCH_EXPANDED =
            "standalone_search_expanded";

    private SharedPreferences mPrefs;
    private boolean mLinear;
    private MenuItem mSearch = null;
    private MenuItem mSwitch;
    private MenuItem mClear = null;
    private MenuItem mStart = null;
    private MenuItem mPause = null;
    private SearchView mSearchView;

    private RecyclerView mList;
    private View mEmpty;
    private MissionAdapter mAdapter;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private Context mContext;

    private DownloadManagerBinder mBinder;
    private boolean bindingRegistered;
    private boolean serviceConnected;
    private boolean mForceUpdate;
    private String mSearchQuery = "";
    private boolean mStandaloneSearchExpanded;

    private DownloadMission unsafeMissionTarget = null;
    private final ActivityResultLauncher<Intent> requestDownloadSaveAsLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestDownloadSaveAsResult);
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (DownloadManagerBinder) binder;
            serviceConnected = true;
            mBinder.clearDownloadNotifications();

            if (getView() == null) {
                return;
            }

            mAdapter = new MissionAdapter(mContext, mBinder.getDownloadManager(), mEmpty, getView());
            mAdapter.setSearchQuery(mSearchQuery);

            mAdapter.setRecover(MissionsFragment.this::recoverMission);

            setAdapterButtons();

            if (mBinder != null) {
                mBinder.addMissionEventListener(mAdapter);
            }
            mBinder.enableNotifications(false);

            updateList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBinder = null;
            serviceConnected = false;
        }

        @Override
        public void onBindingDied(final ComponentName name) {
            mBinder = null;
            serviceConnected = false;
        }

        @Override
        public void onNullBinding(final ComponentName name) {
            mBinder = null;
            serviceConnected = false;
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.missions, container, false);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        mLinear = mPrefs.getBoolean("linear", false);

        // Bind the service
        bindingRegistered = mContext.bindService(new Intent(mContext, DownloadManagerService.class),
                mConnection, Context.BIND_AUTO_CREATE);

        // Views
        mEmpty = v.findViewById(R.id.list_empty_view);
        mList = v.findViewById(R.id.mission_recycler);

        // Init layouts managers
        mGridManager = new GridLayoutManager(getActivity(), SPAN_SIZE);
        mGridManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (mAdapter.getItemViewType(position)) {
                    case DownloadManager.SPECIAL_PENDING:
                    case DownloadManager.SPECIAL_FINISHED:
                        return SPAN_SIZE;
                    default:
                        return 1;
                }
            }
        });
        mLinearManager = new LinearLayoutManager(getActivity());

        return v;
    }

    @Override
    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null && requireActivity() instanceof DownloadActivity) {
            mSearchQuery = ContextualSearchHelper.normalizeQuery(
                    savedInstanceState.getString(STATE_SEARCH_QUERY));
            mStandaloneSearchExpanded = savedInstanceState.getBoolean(
                    STATE_STANDALONE_SEARCH_EXPANDED);
        }
        final MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(getMenuProvider(), getViewLifecycleOwner(),
                Lifecycle.State.RESUMED);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putString(STATE_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(STATE_STANDALONE_SEARCH_EXPANDED,
                mStandaloneSearchExpanded);
        super.onSaveInstanceState(outState);
    }

    /**
     * Added in API level 23.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // Bug: in api< 23 this is never called
        // so mActivity=null
        // so app crashes with null-pointer exception
        mContext = context;
    }

    /**
     * deprecated in API level 23,
     * but must remain to allow compatibility with api<23
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        mContext = activity;
    }


    @Override
    public void onDestroyView() {
        cleanupServiceBinding();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        cleanupServiceBinding();
        super.onDestroy();
    }

    private void cleanupServiceBinding() {
        if (mBinder != null) {
            if (mAdapter != null) {
                mBinder.removeMissionEventListener(mAdapter);
            }
            mBinder.enableNotifications(true);
        }

        if (bindingRegistered) {
            mContext.unbindService(mConnection);
            bindingRegistered = false;
        }
        serviceConnected = false;

        if (mAdapter != null) {
            mAdapter.onDestroy();
        }

        mBinder = null;
        mAdapter = null;
        mSearch = null;
        mSwitch = null;
        mClear = null;
        mStart = null;
        mPause = null;
        mSearchView = null;
        mList = null;
        mEmpty = null;
        mGridManager = null;
        mLinearManager = null;
    }

    private MenuProvider getMenuProvider() {
        return new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull final Menu menu,
                                     @NonNull final android.view.MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.download_menu, menu);
                configureStandaloneSearch(menu);
            }

            @Override
            public void onPrepareMenu(@NonNull final Menu menu) {
                mSearch = menu.findItem(R.id.search_downloads);
                mSwitch = menu.findItem(R.id.switch_mode);
                mClear = menu.findItem(R.id.clear_list);
                mStart = menu.findItem(R.id.start_downloads);
                mPause = menu.findItem(R.id.pause_downloads);

                if (mAdapter != null) {
                    setAdapterButtons();
                }
                updateStandaloneSearchMenuItems();
            }

            @Override
            public boolean onMenuItemSelected(@NonNull final MenuItem item) {
                return handleMenuItemSelected(item);
            }
        };
    }

    private void configureStandaloneSearch(@NonNull final Menu menu) {
        mSearch = menu.findItem(R.id.search_downloads);
        final boolean isStandalone = requireActivity() instanceof DownloadActivity;
        mSearch.setVisible(isStandalone);
        if (!isStandalone) {
            return;
        }

        mSearchView = (SearchView) mSearch.getActionView();
        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mSearchView.setQueryHint(getString(R.string.search));
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                setSearchQuery(query);
                updateStandaloneSearchMenuItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                setSearchQuery(newText);
                updateStandaloneSearchMenuItems();
                return true;
            }
        });
        mSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull final MenuItem item) {
                mStandaloneSearchExpanded = true;
                updateStandaloneSearchMenuItems();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull final MenuItem item) {
                mStandaloneSearchExpanded = false;
                setSearchQuery("");
                updateStandaloneSearchMenuItems();
                return true;
            }
        });

        if (mStandaloneSearchExpanded) {
            mSearch.expandActionView();
            mSearchView.setQuery(mSearchQuery, false);
        }
    }

    private void updateStandaloneSearchMenuItems() {
        if (!(requireActivity() instanceof DownloadActivity)) {
            return;
        }

        if (mSwitch != null) {
            mSwitch.setVisible(!mStandaloneSearchExpanded);
        }
        if (mAdapter != null) {
            mAdapter.setMenuActionsSuppressed(mStandaloneSearchExpanded);
        } else if (mStandaloneSearchExpanded) {
            setMenuItemVisible(mClear, false);
            setMenuItemVisible(mStart, false);
            setMenuItemVisible(mPause, false);
        }
    }

    private static void setMenuItemVisible(final MenuItem item, final boolean visible) {
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private boolean handleMenuItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.switch_mode) {
            mLinear = !mLinear;
            updateList();
            return true;
        } else if (itemId == R.id.clear_list) {
            showClearDownloadHistoryPrompt();
            return true;
        } else if (itemId == R.id.start_downloads) {
            if (mBinder != null) {
                mBinder.getDownloadManager().startAllMissions();
            }
            return true;
        } else if (itemId == R.id.pause_downloads) {
            if (mBinder != null) {
                mBinder.getDownloadManager().pauseAllMissions(false);
            }
            if (mAdapter != null) {
                mAdapter.refreshMissionItems(); // update items view
            }
            return true;
        }
        return false;
    }

    public void showClearDownloadHistoryPrompt() {
        // ask the user whether he wants to just clear history or instead delete files on disk
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.clear_download_history)
                .setMessage(R.string.confirm_prompt)
                // Intentionally misusing buttons' purpose in order to achieve good order
                .setNegativeButton(R.string.clear_download_history, (dialog, which) ->
                        mAdapter.clearFinishedDownloads(false))
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_downloaded_files, (dialog, which) ->
                        showDeleteDownloadedFilesConfirmationPrompt())
                .show();
    }

    public void showDeleteDownloadedFilesConfirmationPrompt() {
        // make sure the user confirms once more before deleting files on disk
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.delete_downloaded_files_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        mAdapter.clearFinishedDownloads(true))
                .show();
    }

    private void updateList() {
        if (mList == null || mAdapter == null) {
            return;
        }

        if (mLinear) {
            mList.setLayoutManager(mLinearManager);
        } else {
            mList.setLayoutManager(mGridManager);
        }

        // destroy all created views in the recycler
        mList.setAdapter(null);
        mAdapter.notifyDataSetChanged();

        // re-attach the adapter in grid/lineal mode
        mAdapter.setLinear(mLinear);
        mList.setAdapter(mAdapter);

        if (mSwitch != null) {
            mSwitch.setIcon(mLinear
                            ? R.drawable.ic_apps
                            : R.drawable.ic_list);
            mSwitch.setTitle(mLinear ? R.string.grid : R.string.list);
            mPrefs.edit().putBoolean("linear", mLinear).apply();
        }
    }

    private void setAdapterButtons() {
        if (mClear == null || mStart == null || mPause == null || mAdapter == null) {
            return;
        }

        mAdapter.setClearButton(mClear);
        mAdapter.setMasterButtons(mStart, mPause);
        mAdapter.setMenuActionsSuppressed(mStandaloneSearchExpanded);
    }

    public void setSearchQuery(@NonNull final String query) {
        mSearchQuery = ContextualSearchHelper.normalizeQuery(query);
        if (mAdapter != null) {
            mAdapter.setSearchQuery(mSearchQuery);
        }
    }

    private void recoverMission(@NonNull DownloadMission mission) {
        unsafeMissionTarget = mission;

        final Uri initialPath;
        if (NewPipeSettings.useStorageAccessFramework(mContext)) {
            initialPath = null;
        } else {
            final File initialSavePath;
            if (DownloadManager.TAG_AUDIO.equals(mission.storage.getType())) {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC);
            } else {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES);
            }
            initialPath = Uri.parse(initialSavePath.getAbsolutePath());
        }

        NoFileManagerSafeGuard.launchSafe(
                requestDownloadSaveAsLauncher,
                StoredFileHelper.getNewPicker(mContext, mission.storage.getName(),
                        mission.storage.getType(), initialPath),
                TAG,
                mContext
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mAdapter != null) {
            mAdapter.onResume();

            if (mForceUpdate) {
                mForceUpdate = false;
                mAdapter.forceUpdate();
            }

            if (mBinder != null) {
                mBinder.addMissionEventListener(mAdapter);
            }
            if (mStart != null && mPause != null) {
                mAdapter.checkMasterButtonsVisibility();
            }
        }
        if (mBinder != null) mBinder.enableNotifications(false);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mForceUpdate = true;
            if (mBinder != null) {
                mBinder.removeMissionEventListener(mAdapter);
            }
            mAdapter.onPaused();
        }

        if (mBinder != null) mBinder.enableNotifications(true);
    }

    private void requestDownloadSaveAsResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }

        if (unsafeMissionTarget == null || result.getData() == null) {
            return;
        }

        try {
            Uri fileUri = result.getData().getData();
            if (fileUri.getAuthority() != null && FilePickerActivityHelper.isOwnFileUri(mContext, fileUri)) {
                fileUri = Uri.fromFile(Utils.getFileForUri(fileUri));
            }

            String tag = unsafeMissionTarget.storage.getTag();
            unsafeMissionTarget.storage = new StoredFileHelper(mContext, null, fileUri, tag);
            mAdapter.recoverMission(unsafeMissionTarget);
        } catch (IOException e) {
            Toast.makeText(mContext, R.string.general_error, Toast.LENGTH_LONG).show();
        }
    }
}
