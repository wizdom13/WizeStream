package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_BOTTOM;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_TOP;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.settings.tabs.HomeNavigationMode;
import org.schabi.newpipe.settings.tabs.HomeNavigationModeResolver;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment
        implements TabLayout.OnTabSelectedListener, BackPressable {
    private static final int BOTTOM_NAVIGATION_MAX_ITEM_COUNT = 5;
    private static final int BOTTOM_NAVIGATION_ITEM_ID_BASE = 10_000;
    private static final long CONTEXTUAL_SEARCH_DEBOUNCE_MILLIS = 250L;
    private static final String STATE_CONTEXTUAL_SEARCH_OPEN = "contextual_search_open";
    private static final String STATE_CONTEXTUAL_SEARCH_QUERY = "contextual_search_query";
    private static final String STATE_CONTEXTUAL_SEARCH_TAB = "contextual_search_tab";

    private FragmentMainBinding binding;
    private BottomNavigationView bottomNavigation;
    private SelectedTabsPagerAdapter pagerAdapter;

    private View contextualSearchContainer;
    private EditText contextualSearchEditText;
    private View contextualSearchClose;
    private View contextualGlobalSearchButton;
    private TextWatcher contextualSearchTextWatcher;
    private ContextualSearchable contextualSearchTarget;
    private final Handler contextualSearchHandler = new Handler(Looper.getMainLooper());
    private boolean contextualSearchOpen;
    private boolean tabsSetupInProgress;
    private String contextualSearchQuery = "";
    private int contextualSearchTabPosition = -1;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;
    private boolean mainTabsPositionBottom;
    private String mainTabsPositionKey;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            contextualSearchOpen = savedInstanceState
                    .getBoolean(STATE_CONTEXTUAL_SEARCH_OPEN, false);
            contextualSearchQuery = savedInstanceState
                    .getString(STATE_CONTEXTUAL_SEARCH_QUERY, "");
            contextualSearchTabPosition = savedInstanceState
                    .getInt(STATE_CONTEXTUAL_SEARCH_TAB, -1);
        }
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        mainTabsPositionKey = getString(R.string.main_tabs_position_key);
        mainTabsPositionBottom = prefs.getBoolean(mainTabsPositionKey, true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);
        bottomNavigation = requireActivity().findViewById(R.id.main_bottom_navigation);

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                if (contextualSearchOpen && !tabsSetupInProgress
                        && contextualSearchTabPosition != position) {
                    closeContextualSearch();
                }
                updateTitleForTab(position);
                updateBottomNavigationSelection(position);
                requireActivity().invalidateOptionsMenu();
            }
        });
        bottomNavigation.setOnItemSelectedListener(item -> {
            final int position = getBottomNavigationItemPosition(item.getItemId());
            if (position < 0 || position >= tabsList.size()) {
                return false;
            }
            if (binding.pager.getCurrentItem() != position) {
                binding.pager.setCurrentItem(position);
            }
            updateTitleForTab(position);
            return true;
        });
        bottomNavigation.setOnItemReselectedListener(item -> {
            final int position = getBottomNavigationItemPosition(item.getItemId());
            if (position >= 0 && position < tabsList.size()) {
                updateTitleForTab(position);
            }
        });

        setupTabs();
        initContextualSearchToolbar();
        if (contextualSearchOpen) {
            binding.pager.post(this::restoreContextualSearch);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

        final boolean newMainTabsPosition = prefs.getBoolean(mainTabsPositionKey, true);
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition;
        }
        updateMainNavigationMode();
    }

    @Override
    public void onPause() {
        setBottomNavigationRequestedVisibility(false);
        if (contextualSearchEditText != null) {
            KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putBoolean(STATE_CONTEXTUAL_SEARCH_OPEN, contextualSearchOpen);
        outState.putString(STATE_CONTEXTUAL_SEARCH_QUERY, contextualSearchQuery);
        outState.putInt(STATE_CONTEXTUAL_SEARCH_TAB, contextualSearchTabPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        setActivityContextualSearchToolbarActive(false);
        if (contextualSearchEditText != null && contextualSearchTextWatcher != null) {
            contextualSearchEditText.removeTextChangedListener(contextualSearchTextWatcher);
        }
        if (contextualSearchContainer != null) {
            contextualSearchContainer.setVisibility(View.GONE);
        }
        contextualSearchContainer = null;
        contextualSearchEditText = null;
        contextualSearchClose = null;
        contextualGlobalSearchButton = null;
        contextualSearchTextWatcher = null;
        contextualSearchTarget = null;
        if (bottomNavigation != null) {
            bottomNavigation.setOnItemSelectedListener(null);
            bottomNavigation.setOnItemReselectedListener(null);
            bottomNavigation.getMenu().clear();
            bottomNavigation.setTag(Boolean.FALSE);
            bottomNavigation.setAlpha(1.0f);
            bottomNavigation.setVisibility(View.GONE);
            bottomNavigation = null;
        }
        super.onDestroyView();
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchItem.setVisible(!contextualSearchOpen);
        }

        updateContextualSearchToolbar(false);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            final ContextualSearchable searchable = getCurrentContextualSearchable();
            if (searchable != null) {
                openContextualSearch(searchable);
                return true;
            }
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsSetupInProgress = true;
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        final boolean replacePagerAdapter = pagerAdapter == null
                || !pagerAdapter.sameTabs(tabsList);
        if (contextualSearchOpen && pagerAdapter != null && replacePagerAdapter) {
            closeContextualSearch();
        }
        if (replacePagerAdapter) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateBottomNavigationItems();
        updateMainNavigationMode();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
        tabsSetupInProgress = false;

        if (contextualSearchOpen
                && (contextualSearchTabPosition < 0
                || contextualSearchTabPosition >= tabsList.size())) {
            closeContextualSearch();
        }
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(getSafeTabIconRes(tab));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    private int getSafeTabIconRes(final Tab tab) {
        final int iconRes = tab.getTabIconRes(requireContext());
        return iconRes > 0 ? iconRes : R.drawable.ic_asterisk;
    }

    private void updateTitleForTab(final int tabPosition) {
        if (contextualSearchOpen || tabPosition < 0 || tabPosition >= tabsList.size()) {
            return;
        }
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contextual local search
    //////////////////////////////////////////////////////////////////////////*/

    private void initContextualSearchToolbar() {
        contextualSearchContainer = requireActivity()
                .findViewById(R.id.toolbar_contextual_search_container);
        contextualSearchEditText = requireActivity()
                .findViewById(R.id.contextual_search_edit_text);
        contextualSearchClose = requireActivity().findViewById(R.id.contextual_search_close);
        contextualGlobalSearchButton = requireActivity()
                .findViewById(R.id.contextual_global_search_button);

        contextualSearchClose.setOnClickListener(view -> closeContextualSearch());
        contextualGlobalSearchButton.setOnClickListener(view -> openGlobalSearchFromContext());
        contextualSearchEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEARCH) {
                dispatchContextualSearchQuery();
                KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
                return true;
            }
            return false;
        });

        contextualSearchTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence text, final int start,
                                          final int count, final int after) {
                // No-op.
            }

            @Override
            public void onTextChanged(final CharSequence text, final int start,
                                      final int before, final int count) {
                // Changes are normalized in afterTextChanged.
            }

            @Override
            public void afterTextChanged(final Editable text) {
                if (!contextualSearchOpen) {
                    return;
                }
                contextualSearchQuery = ContextualSearchHelper.normalizeQuery(text);
                contextualSearchHandler.removeCallbacksAndMessages(null);
                contextualSearchHandler.postDelayed(
                        MainFragment.this::dispatchContextualSearchQuery,
                        CONTEXTUAL_SEARCH_DEBOUNCE_MILLIS);
            }
        };
        contextualSearchEditText.addTextChangedListener(contextualSearchTextWatcher);
    }

    private void openContextualSearch(@NonNull final ContextualSearchable searchable) {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        contextualSearchTarget = searchable;
        contextualSearchOpen = true;
        contextualSearchQuery = "";
        contextualSearchTabPosition = binding.pager.getCurrentItem();

        contextualSearchEditText.setText("");
        updateContextualSearchToolbar(true);
        dispatchContextualSearchQuery();
        requireActivity().invalidateOptionsMenu();
    }

    private void restoreContextualSearch() {
        if (!contextualSearchOpen || binding == null
                || binding.pager.getCurrentItem() != contextualSearchTabPosition) {
            abandonContextualSearchRestore();
            return;
        }

        contextualSearchTarget = getCurrentContextualSearchable();
        if (contextualSearchTarget == null) {
            abandonContextualSearchRestore();
            return;
        }

        contextualSearchEditText.setText(contextualSearchQuery);
        contextualSearchEditText.setSelection(contextualSearchEditText.length());
        updateContextualSearchToolbar(false);
        dispatchContextualSearchQuery();
        requireActivity().invalidateOptionsMenu();
    }

    private void abandonContextualSearchRestore() {
        clearContextualSearchState();
        updateContextualSearchToolbar(false);
        if (binding != null && !tabsList.isEmpty()) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
        if (isAdded()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void updateContextualSearchToolbar(final boolean requestKeyboard) {
        if (contextualSearchContainer == null || contextualSearchEditText == null
                || contextualGlobalSearchButton == null) {
            return;
        }

        contextualSearchContainer.setVisibility(contextualSearchOpen ? View.VISIBLE : View.GONE);
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(!contextualSearchOpen);
        }
        setActivityContextualSearchToolbarActive(contextualSearchOpen);
        if (!contextualSearchOpen || binding == null || tabsList.isEmpty()) {
            return;
        }

        final int position = binding.pager.getCurrentItem();
        final String tabName = tabsList.get(position).getTabName(requireContext());
        contextualSearchEditText.setHint(getString(R.string.contextual_search_hint, tabName));

        final int serviceId = ServiceHelper.getSelectedServiceId(requireContext());
        final String serviceName = ServiceHelper.getNameOfServiceById(serviceId);
        final String globalSearchDescription =
                getString(R.string.search_service_instead, serviceName);
        contextualGlobalSearchButton.setContentDescription(globalSearchDescription);
        TooltipCompat.setTooltipText(contextualGlobalSearchButton, globalSearchDescription);

        if (requestKeyboard) {
            contextualSearchEditText.requestFocus();
            KeyboardUtil.showKeyboard(activity, contextualSearchEditText);
        }
    }

    private void setActivityContextualSearchToolbarActive(final boolean active) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setContextualSearchToolbarActive(active);
        }
    }

    private void dispatchContextualSearchQuery() {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        if (!contextualSearchOpen || contextualSearchTarget == null) {
            return;
        }
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(
                contextualSearchEditText == null
                        ? contextualSearchQuery : contextualSearchEditText.getText());
        contextualSearchTarget.setContextualSearchQuery(contextualSearchQuery);
    }

    private void openGlobalSearchFromContext() {
        final String query = ContextualSearchHelper.normalizeQuery(
                contextualSearchEditText == null ? "" : contextualSearchEditText.getText());
        closeContextualSearch();
        try {
            NavigationHelper.openSearchFragment(getFM(),
                    ServiceHelper.getSelectedServiceId(activity), query);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
        }
    }

    private void closeContextualSearch() {
        contextualSearchHandler.removeCallbacksAndMessages(null);
        if (contextualSearchTarget != null) {
            contextualSearchTarget.setContextualSearchQuery("");
        }
        clearContextualSearchState();

        if (contextualSearchEditText != null) {
            contextualSearchEditText.setText("");
            KeyboardUtil.hideKeyboard(activity, contextualSearchEditText);
        }
        updateContextualSearchToolbar(false);
        if (binding != null && !tabsList.isEmpty()) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
        if (isAdded()) {
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void clearContextualSearchState() {
        contextualSearchOpen = false;
        contextualSearchQuery = "";
        contextualSearchTabPosition = -1;
        contextualSearchTarget = null;
    }

    @Nullable
    private ContextualSearchable getCurrentContextualSearchable() {
        if (pagerAdapter == null) {
            return null;
        }
        final Fragment primaryFragment = pagerAdapter.getPrimaryFragment();
        return primaryFragment instanceof ContextualSearchable
                ? (ContextualSearchable) primaryFragment : null;
    }

    @Override
    public boolean onBackPressed() {
        if (!contextualSearchOpen) {
            return false;
        }
        closeContextualSearch();
        return true;
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    private void updateBottomNavigationItems() {
        if (bottomNavigation == null) {
            return;
        }

        final Menu menu = bottomNavigation.getMenu();
        menu.clear();
        if (tabsList.size() > BOTTOM_NAVIGATION_MAX_ITEM_COUNT) {
            return;
        }

        for (int i = 0; i < tabsList.size(); i++) {
            final Tab tab = tabsList.get(i);
            final String tabName = tab.getTabName(requireContext());
            final MenuItem item = menu.add(Menu.NONE, getBottomNavigationItemId(i), i,
                    getBottomNavigationDisplayLabel(tab, tabName));
            item.setIcon(getSafeTabIconRes(tab));
            item.setCheckable(true);
            MenuItemCompat.setContentDescription(item, tabName);
        }

        updateBottomNavigationSelection(binding.pager.getCurrentItem());
    }

    private String getBottomNavigationDisplayLabel(final Tab tab, final String tabName) {
        if (isLiveKioskTab(tab, tabName)) {
            return getString(R.string.duration_live);
        }
        if (tab.getTabId() == Tab.BookmarksTab.ID) {
            return getString(R.string.bottom_navigation_tab_bookmarks);
        }
        return tabName;
    }

    private boolean isLiveKioskTab(final Tab tab, final String tabName) {
        if (tab instanceof Tab.KioskTab) {
            return "live".equals(((Tab.KioskTab) tab).getKioskId());
        }
        return tab instanceof Tab.DefaultKioskTab
                && getString(R.string.recommended_lives).equals(tabName);
    }

    private void updateBottomNavigationSelection(final int position) {
        if (binding == null || bottomNavigation == null
                || tabsList.size() > BOTTOM_NAVIGATION_MAX_ITEM_COUNT
                || position < 0 || position >= tabsList.size()) {
            return;
        }
        final int itemId = getBottomNavigationItemId(position);
        if (bottomNavigation.getSelectedItemId() != itemId) {
            bottomNavigation.setSelectedItemId(itemId);
        }
    }

    private int getBottomNavigationItemId(final int position) {
        return BOTTOM_NAVIGATION_ITEM_ID_BASE + position;
    }

    private int getBottomNavigationItemPosition(final int itemId) {
        return itemId - BOTTOM_NAVIGATION_ITEM_ID_BASE;
    }

    private void updateMainNavigationMode() {
        if (binding == null || bottomNavigation == null) {
            return;
        }

        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean bottom = mainTabsPositionBottom;
        final HomeNavigationMode navigationMode = HomeNavigationModeResolver
                .resolveNavigationMode(tabsList.size(), bottom);
        final boolean showBottomNavigation = navigationMode
                == HomeNavigationMode.BOTTOM_NAVIGATION;
        final boolean showTabLayout = navigationMode == HomeNavigationMode.SCROLLABLE_TABS;

        final var tabParams = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final var pagerParams = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.removeRule(ALIGN_PARENT_TOP);
        tabParams.removeRule(ALIGN_PARENT_BOTTOM);
        tabParams.addRule(bottom ? ALIGN_PARENT_BOTTOM : ALIGN_PARENT_TOP);

        pagerParams.removeRule(BELOW);
        pagerParams.removeRule(ABOVE);
        pagerParams.bottomMargin = 0;
        if (showBottomNavigation) {
            pagerParams.bottomMargin = getResources()
                    .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
        } else if (showTabLayout) {
            pagerParams.addRule(bottom ? ABOVE : BELOW, R.id.main_tab_layout);
        }

        tabLayout.setSelectedTabIndicatorGravity(
                bottom ? INDICATOR_GRAVITY_TOP : INDICATOR_GRAVITY_BOTTOM);
        tabLayout.setVisibility(showTabLayout ? View.VISIBLE : View.GONE);
        setBottomNavigationRequestedVisibility(showBottomNavigation);

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);
    }

    private void setBottomNavigationRequestedVisibility(final boolean visible) {
        if (bottomNavigation == null) {
            return;
        }

        bottomNavigation.setTag(visible);
        bottomNavigation.setAlpha(1.0f);
        if (!visible) {
            bottomNavigation.setVisibility(View.GONE);
            return;
        }

        final View playerSheet = requireActivity().findViewById(R.id.fragment_player_holder);
        final int playerState = playerSheet == null
                ? BottomSheetBehavior.STATE_HIDDEN
                : BottomSheetBehavior.from(playerSheet).getState();
        final boolean playerCoversNavigation = playerState == BottomSheetBehavior.STATE_EXPANDED
                || playerState == BottomSheetBehavior.STATE_DRAGGING
                || playerState == BottomSheetBehavior.STATE_SETTLING
                || playerState == BottomSheetBehavior.STATE_HALF_EXPANDED;

        bottomNavigation.setVisibility(playerCoversNavigation ? View.INVISIBLE : View.VISIBLE);
        bottomNavigation.bringToFront();
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();
        private Fragment primaryFragment;

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public void setPrimaryItem(@NonNull final ViewGroup container, final int position,
                                   @NonNull final Object object) {
            super.setPrimaryItem(container, position, object);
            primaryFragment = (Fragment) object;
        }

        @Nullable
        public Fragment getPrimaryFragment() {
            return primaryFragment;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
