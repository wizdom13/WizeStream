package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EdgeToEdgeHelperTest {
    @Test
    public void drawerLayoutInsetsAreDistributedToSafeContentEdges() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final View source = new FrameLayout(context);
        final View content = new FrameLayout(context);
        final View drawer = new FrameLayout(context);
        final View header = new FrameLayout(context);
        content.setPadding(1, 2, 3, 4);
        drawer.setPadding(5, 6, 7, 8);
        header.setPadding(9, 10, 11, 12);

        EdgeToEdgeHelper.applyDrawerLayoutSystemBarPadding(
                source, content, drawer, header);
        final WindowInsetsCompat result = ViewCompat.dispatchApplyWindowInsets(
                source,
                new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(),
                                Insets.of(2, 30, 3, 48))
                        .build());

        assertPadding(content, 3, 32, 6, 52);
        assertPadding(drawer, 7, 6, 10, 56);
        assertPadding(header, 9, 40, 11, 12);
        assertEquals(Insets.NONE,
                result.getInsets(WindowInsetsCompat.Type.systemBars()));

        ViewCompat.dispatchApplyWindowInsets(source, new WindowInsetsCompat.Builder().build());
        assertPadding(content, 1, 2, 3, 4);
        assertPadding(drawer, 5, 6, 7, 8);
        assertPadding(header, 9, 10, 11, 12);
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    public void displayCutoutExpandsTheSafeSystemBarInsets() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final View source = new FrameLayout(context);
        final View content = new FrameLayout(context);
        final View drawer = new FrameLayout(context);
        final View header = new FrameLayout(context);

        EdgeToEdgeHelper.applyDrawerLayoutSystemBarPadding(
                source, content, drawer, header);
        final WindowInsetsCompat result = ViewCompat.dispatchApplyWindowInsets(
                source,
                new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(),
                                Insets.of(1, 10, 1, 20))
                        .setInsets(WindowInsetsCompat.Type.displayCutout(),
                                Insets.of(2, 30, 3, 0))
                        .build());

        assertPadding(content, 2, 30, 3, 20);
        assertPadding(drawer, 2, 0, 3, 20);
        assertPadding(header, 0, 30, 0, 0);
        assertEquals(Insets.NONE,
                result.getInsets(WindowInsetsCompat.Type.displayCutout()));
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    public void consumingSystemBarsPreservesImeInsets() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final View source = new FrameLayout(context);

        EdgeToEdgeHelper.applySystemBarPadding(source);
        final WindowInsetsCompat result = ViewCompat.dispatchApplyWindowInsets(
                source,
                new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(),
                                Insets.of(0, 24, 0, 48))
                        .setInsets(WindowInsetsCompat.Type.ime(),
                                Insets.of(0, 0, 0, 220))
                        .build());

        assertEquals(Insets.NONE,
                result.getInsets(WindowInsetsCompat.Type.systemBars()));
        assertEquals(Insets.of(0, 0, 0, 220),
                result.getInsets(WindowInsetsCompat.Type.ime()));
    }

    private static void assertPadding(final View view,
                                      final int left,
                                      final int top,
                                      final int right,
                                      final int bottom) {
        assertEquals(left, view.getPaddingLeft());
        assertEquals(top, view.getPaddingTop());
        assertEquals(right, view.getPaddingRight());
        assertEquals(bottom, view.getPaddingBottom());
    }
}
