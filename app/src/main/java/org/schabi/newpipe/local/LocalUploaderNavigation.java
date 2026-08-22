package org.schabi.newpipe.local;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SparseItemUtil;

public final class LocalUploaderNavigation {
    private LocalUploaderNavigation() {
    }

    public static boolean canOpenChannel(final boolean localMedia,
                                         final String streamUrl,
                                         final String uploaderName) {
        return !localMedia
                && !isBlank(streamUrl)
                && !isBlank(uploaderName);
    }

    public static boolean canOpenChannel(@NonNull final StreamEntity stream) {
        return canOpenChannel(stream.isLocalMedia(), stream.getUrl(), stream.getUploader());
    }

    public static void openChannel(@NonNull final Fragment fragment,
                                   @NonNull final StreamEntity stream) {
        if (!canOpenChannel(stream)) {
            return;
        }

        final StreamInfoItem item = stream.toStreamInfoItem();
        SparseItemUtil.fetchUploaderUrlIfSparse(fragment.requireContext(), stream.getServiceId(),
                stream.getUrl(), stream.getUploaderUrl(), uploaderUrl -> {
                    if (fragment.isAdded() && !isBlank(uploaderUrl)) {
                        NavigationHelper.openChannelFragment(fragment, item, uploaderUrl);
                    }
                });
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
