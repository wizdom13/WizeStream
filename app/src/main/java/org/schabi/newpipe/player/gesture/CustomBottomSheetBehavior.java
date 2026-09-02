package org.schabi.newpipe.player.gesture;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigationrail.NavigationRailView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {

    private final Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private int playerPeekHeight;
    private int bottomSystemBarInset;
    @Nullable
    private FrameLayout bottomSheetView;
    private final List<Integer> skipInterceptionOfElements = List.of(
            R.id.detail_content_root_layout, R.id.relatedItemsLayout,
            R.id.itemsListPanel, R.id.view_pager, R.id.tab_layout, R.id.bottomControls,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton);

    private final BottomSheetCallback bottomNavigationCallback = new BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
            if (newState == STATE_COLLAPSED) {
                restorePhoneOrientationAfterFullscreenCollapse(bottomSheet);
            }
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
        bottomSheetView = child;
        applyPlayerPeekHeight(child, isBottomNavigationRequested(child));
        final boolean handled = super.onLayoutChild(parent, child, layoutDirection);
        updateBottomNavigation(child, getState(), null);
        return handled;
    }

    @Override
    public void setPeekHeight(final int peekHeight) {
        playerPeekHeight = peekHeight;
        if (bottomSheetView == null) {
            super.setPeekHeight(peekHeight);
        } else {
            applyPlayerPeekHeight(
                    bottomSheetView, isBottomNavigationRequested(bottomSheetView));
        }
    }

    public void onBottomNavigationVisibilityChanged() {
        if (bottomSheetView != null) {
            updateBottomNavigation(bottomSheetView, getState(), null);
        }
    }

    public void setBottomSystemBarInset(final int inset) {
        final int safeInset = Math.max(inset, 0);
        if (bottomSystemBarInset == safeInset) {
            return;
        }
        bottomSystemBarInset = safeInset;
        if (bottomSheetView != null) {
            applyPlayerPeekHeight(
                    bottomSheetView, isBottomNavigationRequested(bottomSheetView));
            bottomSheetView.requestLayout();
        }
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

    private void restorePhoneOrientationAfterFullscreenCollapse(@NonNull final View bottomSheet) {
        final Activity activity = findActivity(bottomSheet.getContext());
        if (activity == null
                || DeviceUtils.isTablet(activity)
                || DeviceUtils.isTv(activity)
                || DeviceUtils.isDesktopMode(activity)) {
            return;
        }

        if (PlayerHolder.getInstance().exitMainPlayerFullscreenForMiniPlayer()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Nullable
    private static Activity findActivity(@NonNull final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            final Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return null;
    }

    private void updateBottomNavigation(@NonNull final FrameLayout bottomSheet,
                                        final int state,
                                        @Nullable final Float slideOffset) {
        final View bottomNavigation = findRequestedNavigation(bottomSheet);
        if (bottomNavigation == null) {
            setPlayerStartMargin(bottomSheet, 0);
            applyPlayerPeekHeight(bottomSheet, false);
            return;
        }

        bottomNavigation.bringToFront();
        applyPlayerPeekHeight(bottomSheet, true);

        if (state == STATE_HIDDEN) {
            updateBottomNavigationAppearance(bottomSheet, bottomNavigation, 0.0f);
            return;
        }

        final float expandedFraction = PlayerSheetTransitionCalculator
                .expandedFractionForState(state, slideOffset);
        updateBottomNavigationAppearance(bottomSheet, bottomNavigation, expandedFraction);
    }

    private void applyPlayerPeekHeight(@NonNull final FrameLayout bottomSheet,
                                       final boolean bottomNavigationVisible) {
        final View bottomNavigation = findRequestedNavigation(bottomSheet);
        final int navigationHeight = bottomNavigation == null
                ? bottomSheet.getResources()
                .getDimensionPixelSize(R.dimen.main_bottom_navigation_height)
                : getBottomNavigationHeight(bottomNavigation);
        final int adjustedPeekHeight = PlayerSheetTransitionCalculator.adjustedPeekHeight(
                playerPeekHeight, navigationHeight, bottomNavigationVisible,
                bottomSystemBarInset);
        if (super.getPeekHeight() != adjustedPeekHeight) {
            super.setPeekHeight(adjustedPeekHeight);
        }
    }

    private void updateBottomNavigationAppearance(@NonNull final View bottomSheet,
                                                  @NonNull final View bottomNavigation,
                                                  final float expandedFraction) {
        final float clampedFraction = PlayerSheetTransitionCalculator
                .clampExpandedFraction(expandedFraction);
        if (bottomNavigation instanceof NavigationRailView) {
            setPlayerStartMargin(bottomSheet,
                    PlayerSheetTransitionCalculator.navigationRailPlayerMargin(
                            getNavigationRailWidth(bottomNavigation), clampedFraction));
        } else {
            setPlayerStartMargin(bottomSheet, 0);
        }
        if (clampedFraction >= 1.0f) {
            bottomNavigation.setAlpha(0.0f);
            if (bottomNavigation instanceof NavigationRailView) {
                bottomNavigation.setTranslationX(-getNavigationRailWidth(bottomNavigation));
            } else {
                bottomNavigation.setTranslationY(getBottomNavigationHeight(bottomNavigation));
            }
            bottomNavigation.setVisibility(View.INVISIBLE);
            return;
        }

        bottomNavigation.setVisibility(View.VISIBLE);
        bottomNavigation.setAlpha(1.0f - clampedFraction);
        if (bottomNavigation instanceof NavigationRailView) {
            bottomNavigation.setTranslationX(
                    -getNavigationRailWidth(bottomNavigation) * clampedFraction);
            bottomNavigation.setTranslationY(0.0f);
        } else {
            bottomNavigation.setTranslationX(0.0f);
            bottomNavigation.setTranslationY(
                    PlayerSheetTransitionCalculator.bottomNavigationTranslation(
                            getBottomNavigationHeight(bottomNavigation), clampedFraction));
        }
    }

    private static int getBottomNavigationHeight(@NonNull final View view) {
        if (view instanceof NavigationRailView) {
            return 0;
        }
        return view.getHeight() > 0
                ? view.getHeight()
                : view.getResources()
                .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
    }

    private static int getNavigationRailWidth(@NonNull final View view) {
        return view.getWidth() > 0
                ? view.getWidth()
                : view.getResources().getDimensionPixelSize(R.dimen.main_navigation_rail_width);
    }

    private static void setPlayerStartMargin(@NonNull final View playerSheet,
                                             final int margin) {
        if (!(playerSheet.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)
                || params.getMarginStart() == margin) {
            return;
        }
        params.setMarginStart(margin);
        playerSheet.setLayoutParams(params);
    }

    private static boolean isBottomNavigationRequested(@NonNull final View bottomSheet) {
        return findRequestedNavigation(bottomSheet) != null;
    }

    @Nullable
    private static View findRequestedNavigation(@NonNull final View bottomSheet) {
        final View root = bottomSheet.getRootView();
        final View navigationRail = root.findViewById(R.id.main_navigation_rail);
        if (navigationRail != null && Boolean.TRUE.equals(navigationRail.getTag())) {
            return navigationRail;
        }
        final View bottomNavigation = root.findViewById(R.id.main_bottom_navigation);
        return bottomNavigation != null && Boolean.TRUE.equals(bottomNavigation.getTag())
                ? bottomNavigation : null;
    }
}
