package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchFragmentLifecycleGuardTest {

    @Test
    public void allowsSearchOnlyWhileUiIsAttached() {
        assertTrue(SearchFragment.isSearchUiReady(true, true, true));
        assertFalse(SearchFragment.isSearchUiReady(false, true, true));
        assertFalse(SearchFragment.isSearchUiReady(true, false, true));
        assertFalse(SearchFragment.isSearchUiReady(true, true, false));
    }
}
