package us.shandian.giga.ui.adapter;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DownloadFileIntentsTest {
    @Test
    public void chooserDelegatesOnlyExactReadAccessForDownloadedDocuments() {
        final Uri uri = Uri.parse("content://com.android.providers.downloads.documents/"
                + "document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fvideo.webm");
        final Intent chooser = DownloadFileIntents.viewChooser(uri, "video/webm");
        final Intent view = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(view);
        assertEquals(Intent.ACTION_VIEW, view.getAction());
        assertEquals(uri, view.getData());
        assertEquals("video/webm", view.getType());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, view.getFlags());
        assertEquals(uri, chooser.getClipData().getItemAt(0).getUri());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION,
                chooser.getFlags());
    }

    @Test
    public void revokedDocumentPermissionDoesNotCrashLaunch() {
        final Context context = new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public void startActivity(final Intent intent) {
                throw new SecurityException("Document permission revoked");
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                DownloadFileIntents.open(context, DownloadFileIntents.viewChooser(
                        Uri.parse("content://downloads/document/1"), "video/webm")));
    }
}
