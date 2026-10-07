package org.schabi.newpipe.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.schabi.newpipe.R;

import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BlockedContentDialogTest {
    private ActivityController<Activity> controller;
    private Activity activity;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class);
        activity = controller.get();
        activity.setTheme(R.style.LightTheme);
        controller.setup();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void filteredRowsKeepSelectionsAndSavingRetainsHiddenKeywords() {
        final AtomicReference<String> saved = new AtomicReference<>();
        final AlertDialog dialog = showKeywords("Cars\nMusic\nMovies", saved);
        final EditText search = dialog.findViewById(R.id.blocked_content_search);
        final ListView list = dialog.findViewById(R.id.blocked_content_list);
        search.setText("MUSIC");
        assertEquals(1, list.getAdapter().getCount());
        list.performItemClick(null, 0, list.getAdapter().getItemId(0));
        search.setText("cars");
        search.setText("music");
        final CheckedTextView row =
                (CheckedTextView) list.getAdapter().getView(0, null, list);
        assertFalse(row.isChecked());
        search.setText("not found");
        assertEquals(View.VISIBLE,
                dialog.findViewById(R.id.blocked_content_no_results).getVisibility());
        click(dialog, AlertDialog.BUTTON_POSITIVE);
        assertEquals("Cars\nMovies", saved.get());
    }

    @Test
    public void bulkEditingRefreshesSearchWithoutDroppingHiddenKeywords() {
        final AtomicReference<String> saved = new AtomicReference<>();
        final AlertDialog dialog = showKeywords("Cars\nMusic", saved);
        final EditText search = dialog.findViewById(R.id.blocked_content_search);
        search.setText("music");
        click(dialog, AlertDialog.BUTTON_NEUTRAL);
        final AlertDialog editor = (AlertDialog) ShadowDialog.getLatestDialog();
        final EditText input = findEditText(editor.getWindow().getDecorView());
        assertEquals("Cars\nMusic", input.getText().toString());
        input.setText("Cars,Music, NEWS,news");
        click(editor, AlertDialog.BUTTON_POSITIVE);
        search.setText("news");
        final ListView list = dialog.findViewById(R.id.blocked_content_list);
        assertEquals(1, list.getAdapter().getCount());
        click(dialog, AlertDialog.BUTTON_POSITIVE);
        assertEquals("Cars\nMusic\nNEWS", saved.get());
    }

    @Test
    public void cancellingAfterBulkEditDoesNotSave() {
        final AtomicReference<String> saved = new AtomicReference<>("Original");
        final AlertDialog dialog = showKeywords("Original", saved);
        click(dialog, AlertDialog.BUTTON_NEUTRAL);
        final AlertDialog editor = (AlertDialog) ShadowDialog.getLatestDialog();
        findEditText(editor.getWindow().getDecorView()).setText("Replacement");
        click(editor, AlertDialog.BUTTON_POSITIVE);
        click(dialog, AlertDialog.BUTTON_NEGATIVE);
        assertEquals("Original", saved.get());
    }

    private AlertDialog showKeywords(final String keywords, final AtomicReference<String> saved) {
        final AlertDialog dialog =
                BlockedContentDialog.showKeywords(activity, keywords, saved::set);
        shadowOf(Looper.getMainLooper()).idle();
        return dialog;
    }

    private static void click(final AlertDialog dialog, final int button) {
        dialog.getButton(button).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static EditText findEditText(final View root) {
        if (root instanceof EditText) {
            return (EditText) root;
        }
        if (root instanceof android.view.ViewGroup) {
            final android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                final EditText found = findEditText(group.getChildAt(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
