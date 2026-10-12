package org.schabi.newpipe.local.playlist;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Looper;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import org.schabi.newpipe.R;

import java.util.Collections;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocalPlaylistDeleteDialogTest {
    private LocalPlaylistFragment fragment;
    private LocalPlaylistManager manager;
    private MenuItem deleteItem;

    @Before
    public void setUp() {
        final Context context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.LightTheme);
        fragment = spy(LocalPlaylistFragment.getInstance(42L, "Empty playlist"));
        doReturn(context).when(fragment).getContext();
        manager = mock(LocalPlaylistManager.class);
        ReflectionHelpers.setField(fragment, "playlistManager", manager);
        ReflectionHelpers.setField(fragment, "disposables", new CompositeDisposable());
        deleteItem = mock(MenuItem.class);
        when(deleteItem.getItemId()).thenReturn(R.id.menu_item_delete_playlist);
    }

    @Test
    public void openingAndCancellingDoesNotDeletePlaylist() {
        fragment.onOptionsItemSelected(deleteItem);
        final AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        verifyNoInteractions(manager);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        verifyNoInteractions(manager);
    }

    @Test
    public void confirmingDeletesEmptyPlaylistWithoutLoadingStreams() {
        when(manager.updatePlaylists(Collections.emptyList(), Collections.singletonList(42L)))
                .thenReturn(Completable.complete());
        fragment.onOptionsItemSelected(deleteItem);
        final AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        verify(manager).updatePlaylists(Collections.emptyList(), Collections.singletonList(42L));
        org.mockito.Mockito.verifyNoMoreInteractions(manager);
    }
}
