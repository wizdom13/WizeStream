package org.schabi.newpipe.util;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;

/**
 * Creates grid layout managers whose span count follows the user's preference and content width.
 */
public final class GridLayoutManagerHelper {
    private static final int AUTOMATIC_SPAN_COUNT = 0;
    private static final int MINIMUM_CONFIGURED_SPAN_COUNT = 2;
    private static final int MAXIMUM_CONFIGURED_SPAN_COUNT = 4;

    private GridLayoutManagerHelper() {
    }

    @FunctionalInterface
    public interface SpanSizeLookupFactory {
        @NonNull
        GridLayoutManager.SpanSizeLookup create(int spanCount);
    }

    @NonNull
    public static GridLayoutManager create(@NonNull final RecyclerView recyclerView,
                                           final int minimumItemWidth) {
        return create(recyclerView, minimumItemWidth, AUTOMATIC_SPAN_COUNT, null);
    }

    @NonNull
    public static GridLayoutManager create(
            @NonNull final RecyclerView recyclerView,
            final int minimumItemWidth,
            @Nullable final SpanSizeLookupFactory spanSizeLookupFactory) {
        return create(recyclerView, minimumItemWidth, AUTOMATIC_SPAN_COUNT,
                spanSizeLookupFactory);
    }

    @NonNull
    public static GridLayoutManager create(
            @NonNull final RecyclerView recyclerView,
            final int minimumItemWidth,
            final int preferredSpanCount,
            @Nullable final SpanSizeLookupFactory spanSizeLookupFactory) {
        final int initialSpanCount = calculateSpanCount(
                getAvailableWidth(recyclerView), minimumItemWidth, preferredSpanCount);
        final GridLayoutManager layoutManager = new GridLayoutManager(
                recyclerView.getContext(), initialSpanCount);
        updateSpanSizeLookup(layoutManager, initialSpanCount, spanSizeLookupFactory);

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(final View view,
                                       final int left,
                                       final int top,
                                       final int right,
                                       final int bottom,
                                       final int oldLeft,
                                       final int oldTop,
                                       final int oldRight,
                                       final int oldBottom) {
                if (recyclerView.getLayoutManager() != layoutManager) {
                    recyclerView.removeOnLayoutChangeListener(this);
                    return;
                }
                if (right - left == oldRight - oldLeft) {
                    return;
                }

                final int spanCount = calculateSpanCount(
                        getAvailableWidth(recyclerView), minimumItemWidth, preferredSpanCount);
                if (spanCount != layoutManager.getSpanCount()) {
                    updateSpanSizeLookup(layoutManager, spanCount, spanSizeLookupFactory);
                    layoutManager.setSpanCount(spanCount);
                }
            }
        });
        return layoutManager;
    }

    public static int getPreferredSpanCount(@NonNull final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.grid_columns_key),
                        context.getString(R.string.grid_columns_auto_key));
        return parsePreferredSpanCount(value);
    }

    static int parsePreferredSpanCount(@Nullable final String value) {
        if (value == null) {
            return AUTOMATIC_SPAN_COUNT;
        }

        try {
            final int spanCount = Integer.parseInt(value);
            if (spanCount >= MINIMUM_CONFIGURED_SPAN_COUNT
                    && spanCount <= MAXIMUM_CONFIGURED_SPAN_COUNT) {
                return spanCount;
            }
        } catch (final NumberFormatException ignored) {
            // Automatic and unknown values both use responsive sizing.
        }
        return AUTOMATIC_SPAN_COUNT;
    }

    static int calculateSpanCount(final int availableWidth, final int minimumItemWidth) {
        return calculateSpanCount(availableWidth, minimumItemWidth, AUTOMATIC_SPAN_COUNT);
    }

    static int calculateSpanCount(final int availableWidth,
                                  final int minimumItemWidth,
                                  final int preferredSpanCount) {
        if (preferredSpanCount >= MINIMUM_CONFIGURED_SPAN_COUNT
                && preferredSpanCount <= MAXIMUM_CONFIGURED_SPAN_COUNT) {
            return preferredSpanCount;
        }
        if (availableWidth <= 0 || minimumItemWidth <= 0) {
            return 1;
        }
        return Math.max(1, Math.floorDiv(availableWidth, minimumItemWidth));
    }

    private static int getAvailableWidth(@NonNull final RecyclerView recyclerView) {
        final int measuredWidth = recyclerView.getWidth()
                - recyclerView.getPaddingStart() - recyclerView.getPaddingEnd();
        if (measuredWidth > 0) {
            return measuredWidth;
        }
        return recyclerView.getResources().getDisplayMetrics().widthPixels
                - recyclerView.getPaddingStart() - recyclerView.getPaddingEnd();
    }

    private static void updateSpanSizeLookup(
            @NonNull final GridLayoutManager layoutManager,
            final int spanCount,
            @Nullable final SpanSizeLookupFactory spanSizeLookupFactory) {
        if (spanSizeLookupFactory != null) {
            layoutManager.setSpanSizeLookup(spanSizeLookupFactory.create(spanCount));
        }
    }
}
