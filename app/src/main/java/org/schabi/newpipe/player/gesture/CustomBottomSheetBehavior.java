package org.schabi.newpipe.player.gesture;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.schabi.newpipe.R;

import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {

    private final Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private final List<Integer> skipInterceptionOfElements = List.of(
            R.id.detail_content_root_layout, R.id.relatedItemsLayout,
            R.id.itemsListPanel, R.id.view_pager, R.id.tab_layout, R.id.bottomControls,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton);

    private final BottomSheetCallback bottomNavigationCallback = new BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
            if (newState == STATE_COLLAPSED || newState == STATE_EXPANDED
                    || newState == STATE_HIDDEN) {
                updateBottomNavigation((FrameLayout) bottomSheet, newState, null);
            }
        }

        @Override
        public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
            updateBottomNavigation((FrameLayout) bottomSheet, getState(), slideOffset);
        }
    };

    public CustomBottomSheetBehavior(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs) {
        super(context, attrs);
        addBottomSheetCallback(bottomNavigationCallback);
    }

    @Override
    public boolean onLayoutChild(@NonNull final CoordinatorLayout parent,
                                 @NonNull final FrameLayout child,
                                 final int layoutDirection) {
        final boolean handled = super.onLayoutChild(parent, child, layoutDirection);
        updateBottomNavigation(child, getState(), null);
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final FrameLayout child,
                                         @NonNull final MotionEvent event) {
        // Drop following when action ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL
                || event.getAction() == MotionEvent.ACTION_UP) {
            skippingInterception = false;
        }

        // Found that user still swiping, continue following
        if (skippingInterception || getState() == BottomSheetBehavior.STATE_SETTLING) {
            return false;
        }

        // The interception listens for the child view with the id "fragment_player_holder",
        // so the following two-finger gesture will be triggered only for the player view on
        // portrait and for the top controls (visible) on landscape.
        setSkipCollapsed(event.getPointerCount() == 2);
        if (event.getPointerCount() == 2) {
            return super.onInterceptTouchEvent(parent, child, event);
        }

        // Don't need to do anything if bottomSheet isn't expanded
        if (getState() == BottomSheetBehavior.STATE_EXPANDED
                && event.getAction() == MotionEvent.ACTION_DOWN) {
            // Without overriding scrolling will not work when user touches these elements
            for (final int element : skipInterceptionOfElements) {
                final View view = child.findViewById(element);
                if (view != null) {
                    final boolean visible = view.getGlobalVisibleRect(globalRect);
                    if (visible
                            && globalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        // Makes bottom part of the player draggable in portrait when
                        // playbackControlRoot is hidden
                        if (element == R.id.bottomControls
                                && child.findViewById(R.id.playbackControlRoot)
                                .getVisibility() != View.VISIBLE) {
                            return super.onInterceptTouchEvent(parent, child, event);
                        }
                        skippingInterception = true;
                        return false;
                    }
                }
            }
        }

        return super.onInterceptTouchEvent(parent, child, event);
    }

    private void updateBottomNavigation(@NonNull final FrameLayout bottomSheet,
                                        final int state,
                                        @Nullable final Float slideOffset) {
        final View bottomNavigation = bottomSheet.getRootView()
                .findViewById(R.id.main_bottom_navigation);
        if (bottomNavigation == null || !Boolean.TRUE.equals(bottomNavigation.getTag())) {
            bottomSheet.setTranslationY(0.0f);
            return;
        }

        bottomNavigation.bringToFront();

        if (state == STATE_HIDDEN) {
            bottomSheet.setTranslationY(0.0f);
            showBottomNavigation(bottomNavigation, 1.0f);
            return;
        }

        final int navigationHeight = bottomNavigation.getHeight() > 0
                ? bottomNavigation.getHeight()
                : bottomSheet.getResources()
                .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);

        final float expandedFraction;
        if (slideOffset != null) {
            expandedFraction = Math.max(0.0f, Math.min(1.0f, slideOffset));
        } else {
            expandedFraction = state == STATE_EXPANDED ? 1.0f : 0.0f;
        }

        final float collapsedFraction = 1.0f - expandedFraction;
        bottomSheet.setTranslationY(-navigationHeight * collapsedFraction);
        showBottomNavigation(bottomNavigation, collapsedFraction);
    }

    private static void showBottomNavigation(@NonNull final View bottomNavigation,
                                             final float visibleFraction) {
        if (visibleFraction <= 0.0f) {
            bottomNavigation.setAlpha(0.0f);
            bottomNavigation.setVisibility(View.INVISIBLE);
            return;
        }

        bottomNavigation.setVisibility(View.VISIBLE);
        bottomNavigation.setAlpha(visibleFraction);
    }
}
