package org.schabi.newpipe.fragments.detail;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class TabAdapterRemovalTest {
    @Test
    public void removingDynamicTabPreservesOtherDestinations() {
        final var adapter = new TabAdapter(mock(FragmentManager.class));
        final var description = new Fragment();
        adapter.addFragment(new Fragment(), "comments");
        adapter.addFragment(new Fragment(), "chat");
        adapter.addFragment(description, "description");
        adapter.removeItemByTitle("chat");
        adapter.removeItemByTitle("absent");
        assertEquals(2, adapter.getCount());
        assertEquals("description", adapter.getItemTitle(1));
        assertSame(description, adapter.getItem(1));
    }
}
