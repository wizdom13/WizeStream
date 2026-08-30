package org.schabi.newpipe.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Creates grid layout managers whose span count follows the measured content width. */
public final class GridLayoutManagerHelper {
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
        return create(recyclerView, minimumItemWidth, null);
    }

    @NonNull
    public static GridLayoutManager create(
            @NonNull final RecyclerView recyclerView,
            final int minimumItemWidth,
            @Nullable final SpanSizeLookupFactory spanSizeLookupFactory) {
        final int initialSpanCount = calculateSpanCount(
                getAvailableWidth(recyclerView), minimumItemWidth);
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
                        getAvailableWidth(recyclerView), minimumItemWidth);
                if (spanCount != layoutManager.getSpanCount()) {
                    updateSpanSizeLookup(layoutManager, spanCount, spanSizeLookupFactory);
                    layoutManager.setSpanCount(spanCount);
                }
            }
        });
        return layoutManager;
    }

    static int calculateSpanCount(final int availableWidth, final int minimumItemWidth) {
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
