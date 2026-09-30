package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.text.TextUtils;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

public class DefaultKioskFragment extends KioskFragment {
    @State
    boolean youtubeMusicMode;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        updateSelectedDefaultKiosk();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceId != ServiceHelper.getSelectedServiceId(requireContext())
                || youtubeMusicMode != ServiceHelper.isYoutubeMusicMode(requireContext())) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
            reloadContent();
        }
    }

    private void updateSelectedDefaultKiosk() {
        try {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());
            youtubeMusicMode = ServiceHelper.isYoutubeMusicMode(requireContext());

            final KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
            kioskId = youtubeMusicMode
                    ? "trending_music" : kioskList.getDefaultKioskId();
            // Odysee supports search but exposes no discovery kiosk.
            url = TextUtils.isEmpty(kioskId) ? ""
                    : kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl();

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        if (TextUtils.isEmpty(kioskId)) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            currentInfo = null;
            currentNextPage = null;
            infoListAdapter.clearStreamItemList();
            showListFooter(false);
            showEmptyState();
            setEmptyStateMessage(R.string.main_bg_subtitle);
            return;
        }
        super.startLoading(forceLoad);
    }
}
