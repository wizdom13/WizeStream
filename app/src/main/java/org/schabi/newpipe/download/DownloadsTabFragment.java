package org.schabi.newpipe.download;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

import java.util.Map;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.MissionsFragment;

public class DownloadsTabFragment extends Fragment {
    private static final String MISSIONS_FRAGMENT_TAG = "missions_fragment";

    private View downloadsContainerView;
    private View permissionRequiredView;
    private View grantPermissionButton;
    private boolean permissionRequestInFlight;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new RequestMultiplePermissions(), this::onPermissionsResult);

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        downloadsContainerView = view.findViewById(R.id.downloads_tab_container);
        permissionRequiredView = view.findViewById(R.id.downloads_permission_required);
        grantPermissionButton = view.findViewById(R.id.downloads_permission_grant_button);
        grantPermissionButton.setOnClickListener(v -> requestStoragePermission());
        updatePermissionState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        downloadsContainerView = null;
        permissionRequiredView = null;
        grantPermissionButton = null;
    }

    private void requestStoragePermission() {
        if (permissionRequestInFlight || PermissionHelper.hasStoragePermissions(requireContext())) {
            updatePermissionState();
            return;
        }
        permissionRequestInFlight = true;
        requestPermissionsLauncher.launch(PermissionHelper.getStoragePermissions());
    }

    private void onPermissionsResult(final Map<String, Boolean> permissions) {
        permissionRequestInFlight = false;
        updatePermissionState();
    }

    private void updatePermissionState() {
        if (!isAdded() || getView() == null) {
            return;
        }
        if (PermissionHelper.hasStoragePermissions(requireContext())) {
            showDownloadsUi();
        } else {
            showPermissionRequiredState();
        }
    }

    private void showDownloadsUi() {
        permissionRequiredView.setVisibility(View.GONE);
        downloadsContainerView.setVisibility(View.VISIBLE);
        requireContext().startService(new Intent(requireContext(), DownloadManagerService.class));
        if (getChildFragmentManager().findFragmentByTag(MISSIONS_FRAGMENT_TAG) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.downloads_tab_container, new MissionsFragment(),
                            MISSIONS_FRAGMENT_TAG)
                    .commit();
        }
    }

    private void showPermissionRequiredState() {
        downloadsContainerView.setVisibility(View.GONE);
        permissionRequiredView.setVisibility(View.VISIBLE);
        final Fragment missionsFragment = getChildFragmentManager()
                .findFragmentByTag(MISSIONS_FRAGMENT_TAG);
        if (missionsFragment != null) {
            getChildFragmentManager().beginTransaction()
                    .remove(missionsFragment)
                    .commit();
        }
    }
}
