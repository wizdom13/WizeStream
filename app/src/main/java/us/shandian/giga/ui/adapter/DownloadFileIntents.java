package us.shandian.giga.ui.adapter;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.external_communication.ShareUtils;

final class DownloadFileIntents {
    private DownloadFileIntents() {
    }

    static Intent viewChooser(final Uri uri, final String mimeType) {
        final Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType);
        view.setClipData(ClipData.newRawUri("", uri));
        // A downloaded document needs an exact read grant, not access to descendant URIs.
        // ACTION_CREATE_DOCUMENT grants cannot necessarily be delegated as prefix grants.
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static void open(final Context context, final Intent intent) {
        try {
            ShareUtils.openIntentInApp(context, intent);
        } catch (final SecurityException exception) {
            // Storage grants can be revoked after a download has completed.
            Toast.makeText(context, R.string.invalid_file, Toast.LENGTH_LONG).show();
        }
    }
}
