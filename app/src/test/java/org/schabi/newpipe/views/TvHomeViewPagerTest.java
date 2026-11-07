package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.navigationrail.NavigationRailView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.DeviceUtils;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TvHomeViewPagerTest {
    @Test
    public void leftAtContentEdgeReturnsToSelectedRailItemWithoutChangingTabs()
            throws Exception {
        final Field tvField = DeviceUtils.class.getDeclaredField("isTV");
        tvField.setAccessible(true);
        final Object originalTv = tvField.get(null);
        tvField.set(null, true);
        try (ActivityController<Activity> controller =
                     Robolectric.buildActivity(Activity.class).setup().visible()) {
            final Activity activity = controller.get();
            activity.setTheme(R.style.DarkTheme);
            final FrameLayout root = new FrameLayout(activity);
            final NavigationRailView rail = new NavigationRailView(activity);
            rail.setId(R.id.main_navigation_rail);
            rail.getMenu().add(Menu.NONE, 101, 0, "First").setCheckable(true);
            rail.getMenu().add(Menu.NONE, 102, 1, "Second").setCheckable(true);
            rail.setSelectedItemId(102);
            root.addView(rail, new FrameLayout.LayoutParams(100, 600));

            final TvHomeViewPager pager = new TvHomeViewPager(activity);
            final FrameLayout.LayoutParams pagerParams = new FrameLayout.LayoutParams(700, 600);
            pagerParams.leftMargin = 100;
            root.addView(pager, pagerParams);
            activity.setContentView(root);
            final View[] buttons = new View[2];
            pager.setAdapter(new PagerAdapter() {
                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public boolean isViewFromObject(@NonNull final View view,
                                                @NonNull final Object object) {
                    return view == object;
                }

                @NonNull
                @Override
                public Object instantiateItem(@NonNull final ViewGroup container,
                                              final int position) {
                    final FrameLayout page = new FrameLayout(activity);
                    final View button = new View(activity);
                    button.setFocusable(true);
                    page.addView(button, new FrameLayout.LayoutParams(80, 80));
                    buttons[position] = button;
                    container.addView(page);
                    return page;
                }

                @Override
                public void destroyItem(@NonNull final ViewGroup container, final int position,
                                        @NonNull final Object object) {
                    container.removeView((View) object);
                }
            });
            pager.setCurrentItem(1, false);
            root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 800, 600);
            assertTrue(buttons[1].requestFocus());

            assertTrue(pager.arrowScroll(View.FOCUS_LEFT));
            assertEquals(1, pager.getCurrentItem());
            assertTrue(rail.findViewById(102).hasFocus());
        } finally {
            tvField.set(null, originalTv);
        }
    }
}
