package org.schabi.newpipe.fragments.list.search;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SearchFilterDialog {
    interface Listener {
        void onSearchFiltersApplied(@NonNull String contentFilter,
                                    @NonNull List<Integer> sortFilters);
    }

    private final Context context;
    private final StreamingService service;
    private final Listener listener;
    private final LinearLayout content;
    private final LinearLayout sortContent;
    private final List<FilterItem> contentFilters;
    private final Set<Integer> selectedSortFilters;
    private FilterItem selectedContentFilter;
    private RadioGroup contentFilterGroup;

    private SearchFilterDialog(@NonNull final Context context,
                               @NonNull final StreamingService service,
                               @NonNull final String[] currentContentFilters,
                               @NonNull final int[] currentSortFilters,
                               @NonNull final Listener listener) {
        this.context = context;
        this.service = service;
        this.listener = listener;
        contentFilters = flatten(service.getSearchQHFactory().getAvailableContentFilter());
        selectedContentFilter = findByName(contentFilters,
                currentContentFilters.length == 0 ? null : currentContentFilters[0]);
        if (selectedContentFilter == null && !contentFilters.isEmpty()) {
            selectedContentFilter = contentFilters.get(0);
        }
        selectedSortFilters = new LinkedHashSet<>();
        for (final int currentSortFilter : currentSortFilters) {
            selectedSortFilters.add(currentSortFilter);
        }

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(8));
        sortContent = new LinearLayout(context);
        sortContent.setOrientation(LinearLayout.VERTICAL);
    }

    static void show(@NonNull final Context context,
                     @NonNull final StreamingService service,
                     @NonNull final String[] currentContentFilters,
                     @NonNull final int[] currentSortFilters,
                     @NonNull final Listener listener) {
        final SearchFilterDialog controller = new SearchFilterDialog(
                context, service, currentContentFilters, currentSortFilters, listener);
        controller.show();
    }

    static boolean hasFilters(@Nullable final StreamingService service) {
        if (service == null) {
            return false;
        }
        final Filter content = service.getSearchQHFactory().getAvailableContentFilter();
        final Filter sort = service.getSearchQHFactory().getAvailableSortFilter();
        return !flatten(content).isEmpty() || !flatten(sort).isEmpty();
    }

    private void show() {
        buildContentFilters();
        rebuildSortFilters(false);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.addView(content);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.search_filters)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset, null)
                .setPositiveButton(R.string.search_filter_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view -> reset());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                listener.onSearchFiltersApplied(selectedContentFilter == null
                                ? "" : selectedContentFilter.getName(),
                        new ArrayList<>(selectedSortFilters));
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void buildContentFilters() {
        if (contentFilters.isEmpty()) {
            content.addView(sortContent);
            return;
        }
        addHeading(content, context.getString(R.string.search_filter_content_type));
        contentFilterGroup = new RadioGroup(context);
        contentFilterGroup.setOrientation(LinearLayout.VERTICAL);
        for (final FilterItem filter : contentFilters) {
            final MaterialRadioButton radioButton = new MaterialRadioButton(context);
            radioButton.setId(View.generateViewId());
            radioButton.setText(translated(filter.getName()));
            radioButton.setTag(filter);
            radioButton.setChecked(filter == selectedContentFilter);
            contentFilterGroup.addView(radioButton);
        }
        contentFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            final View checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof FilterItem) {
                selectedContentFilter = (FilterItem) checked.getTag();
                rebuildSortFilters(false);
            }
        });
        content.addView(contentFilterGroup);
        content.addView(sortContent);
    }

    private void rebuildSortFilters(final boolean contentTypeChanged) {
        sortContent.removeAllViews();
        final Filter sortVariant = selectedContentFilter == null
                ? service.getSearchQHFactory().getAvailableSortFilter()
                : service.getSearchQHFactory().getContentFilterSortFilterVariant(
                        selectedContentFilter.getIdentifier());
        final List<FilterGroup> groups = groups(sortVariant);
        normalizeSortFilters(groups, selectedSortFilters, contentTypeChanged);

        for (final FilterGroup group : groups) {
            if (group.filterItems.length == 0) {
                continue;
            }
            addHeading(sortContent, translated(group.groupName));
            if (group.onlyOneCheckable) {
                addExclusiveGroup(group);
            } else {
                addMultiChoiceGroup(group);
            }
        }
    }

    private void addExclusiveGroup(@NonNull final FilterGroup group) {
        final RadioGroup radioGroup = new RadioGroup(context);
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        for (final FilterItem filter : group.filterItems) {
            final MaterialRadioButton radioButton = new MaterialRadioButton(context);
            radioButton.setId(View.generateViewId());
            radioButton.setText(translated(filter.getName()));
            radioButton.setTag(filter);
            radioButton.setChecked(selectedSortFilters.contains(filter.getIdentifier()));
            radioGroup.addView(radioButton);
        }
        radioGroup.setOnCheckedChangeListener((view, checkedId) -> {
            for (final FilterItem filter : group.filterItems) {
                selectedSortFilters.remove(filter.getIdentifier());
            }
            final View checked = view.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof FilterItem) {
                selectedSortFilters.add(((FilterItem) checked.getTag()).getIdentifier());
            }
        });
        sortContent.addView(radioGroup);
    }

    private void addMultiChoiceGroup(@NonNull final FilterGroup group) {
        for (final FilterItem filter : group.filterItems) {
            final MaterialCheckBox checkBox = new MaterialCheckBox(context);
            checkBox.setText(translated(filter.getName()));
            checkBox.setChecked(selectedSortFilters.contains(filter.getIdentifier()));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    selectedSortFilters.add(filter.getIdentifier());
                } else {
                    selectedSortFilters.remove(filter.getIdentifier());
                }
            });
            sortContent.addView(checkBox);
        }
    }

    private void reset() {
        selectedContentFilter = contentFilters.isEmpty() ? null : contentFilters.get(0);
        selectedSortFilters.clear();
        if (contentFilterGroup != null && contentFilterGroup.getChildCount() > 0) {
            ((MaterialRadioButton) contentFilterGroup.getChildAt(0)).setChecked(true);
        }
        rebuildSortFilters(true);
    }

    static void normalizeSortFilters(@NonNull final List<FilterGroup> groups,
                                     @NonNull final Set<Integer> selected,
                                     final boolean contentTypeChanged) {
        final Set<Integer> normalized = new LinkedHashSet<>();
        for (final FilterGroup group : groups) {
            if (group.filterItems.length == 0) {
                continue;
            }
            if (group.onlyOneCheckable) {
                FilterItem selectedItem = null;
                for (final FilterItem filter : group.filterItems) {
                    if (selected.contains(filter.getIdentifier())) {
                        selectedItem = filter;
                        break;
                    }
                }
                normalized.add((selectedItem == null || contentTypeChanged
                        ? group.filterItems[0] : selectedItem).getIdentifier());
            } else {
                for (final FilterItem filter : group.filterItems) {
                    if (selected.contains(filter.getIdentifier())) {
                        normalized.add(filter.getIdentifier());
                    }
                }
            }
        }
        selected.clear();
        selected.addAll(normalized);
    }

    private void addHeading(@NonNull final LinearLayout parent, @NonNull final String text) {
        final TextView heading = new TextView(context);
        heading.setText(text);
        heading.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.setTypeface(heading.getTypeface(), Typeface.BOLD);
        heading.setPadding(0, dp(16), 0, dp(4));
        parent.addView(heading);
    }

    @NonNull
    private String translated(@Nullable final String name) {
        return name == null ? "" : ServiceHelper.getTranslatedFilterString(name, context);
    }

    private int dp(final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static List<FilterGroup> groups(@Nullable final Filter filter) {
        if (filter == null || filter.getFilterGroups() == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(filter.getFilterGroups());
    }

    @NonNull
    private static List<FilterItem> flatten(@Nullable final Filter filter) {
        final List<FilterItem> items = new ArrayList<>();
        for (final FilterGroup group : groups(filter)) {
            items.addAll(Arrays.asList(group.filterItems));
        }
        return items;
    }

    @Nullable
    private static FilterItem findByName(@NonNull final List<FilterItem> items,
                                         @Nullable final String name) {
        for (final FilterItem item : items) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
