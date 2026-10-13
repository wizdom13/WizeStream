package org.schabi.newpipe.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "mdpi")
public class MiniPlayerCornersTest {
    @Test
    public void collapsedOutlineRoundsTopWhileKeepingVisibleBottomSquare() {
        final View view = view();
        final MiniPlayerCorners corners = new MiniPlayerCorners(view, true);
        corners.setExpansionFraction(0f);
        final Outline outline = outline(view);
        final Rect bounds = new Rect();
        assertTrue(outline.getRect(bounds));
        assertTrue(outline.canClip());
        assertTrue(view.getClipToOutline());
        assertTrue(outline.getRadius() > 0f);
        assertTrue(bounds.bottom - outline.getRadius() >= view.getHeight());
    }

    @Test
    public void expansionAndDisabledPreferenceRemoveClipping() {
        final View view = view();
        final MiniPlayerCorners corners = new MiniPlayerCorners(view, true);
        corners.setExpansionFraction(0f);
        final float collapsedRadius = outline(view).getRadius();
        corners.setExpansionFraction(0.5f);
        assertEquals(collapsedRadius / 2f, outline(view).getRadius(), 0.01f);
        corners.setExpansionFraction(1f);
        assertFalse(view.getClipToOutline());
        assertEquals(0f, outline(view).getRadius(), 0.01f);
        corners.setExpansionFraction(0f);
        corners.setEnabled(false);
        assertFalse(view.getClipToOutline());
        assertEquals(0f, outline(view).getRadius(), 0.01f);
    }

    @Test
    public void releaseRestoresOriginalContainerOutline() {
        final View view = view();
        final ViewOutlineProvider original = view.getOutlineProvider();
        final MiniPlayerCorners corners = new MiniPlayerCorners(view, true);
        corners.setExpansionFraction(0f);
        corners.release();
        assertSame(original, view.getOutlineProvider());
        assertFalse(view.getClipToOutline());
    }

    private static View view() {
        final View view = new View(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 200, 100);
        return view;
    }

    private static Outline outline(final View view) {
        final Outline outline = new Outline();
        view.getOutlineProvider().getOutline(view, outline);
        return outline;
    }
}
