package org.schabi.newpipe.settings.preferencesearch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.SettingsPreferencesearchFragmentBinding;

import java.util.List;

/**
 * Displays the search results.
 */
public class PreferenceSearchFragment extends Fragment {
    public static final String NAME = PreferenceSearchFragment.class.getSimpleName();

    private PreferenceSearcher searcher;
    private String query = "";

    private SettingsPreferencesearchFragmentBinding binding;
    private PreferenceSearchAdapter adapter;

    public void setSearcher(final PreferenceSearcher searcher) {
        this.searcher = searcher;
    }

    public boolean hasSearcher() {
        return searcher != null;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = SettingsPreferencesearchFragmentBinding.inflate(inflater, container, false);

        binding.searchResults.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new PreferenceSearchAdapter();
        adapter.setOnItemClickListener(this::onItemClicked);
        binding.searchResults.setAdapter(adapter);

        updateSearchResults(query);
        return binding.getRoot();
    }

    public void updateSearchResults(final String keyword) {
        query = keyword;
        if (binding == null || searcher == null) {
            return;
        }

        final List<PreferenceSearchItem> results = searcher.searchFor(keyword);
        adapter.submitList(results);
        binding.emptyStateText.setText(keyword.trim().isEmpty()
                ? R.string.settings_search_hint : R.string.settings_search_no_results);
        setEmptyViewShown(results.isEmpty());
    }

    private void setEmptyViewShown(final boolean shown) {
        binding.emptyStateView.setVisibility(shown ? View.VISIBLE : View.GONE);
        binding.searchResults.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        binding.searchResults.setAdapter(null);
        binding = null;
        adapter = null;
        // Rebuild when returning from a result, as device mode and list labels may have changed.
        searcher = null;
        super.onDestroyView();
    }

    public void onItemClicked(final PreferenceSearchItem item) {
        if (!(getActivity() instanceof PreferenceSearchResultListener)) {
            throw new ClassCastException(
                getActivity().toString() + " must implement SearchPreferenceResultListener");
        }

        ((PreferenceSearchResultListener) getActivity()).onSearchResultClicked(item);
    }
}
