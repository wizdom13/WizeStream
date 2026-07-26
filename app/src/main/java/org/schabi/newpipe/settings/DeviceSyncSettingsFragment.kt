/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogDevicePairingBinding
import org.schabi.newpipe.sync.DeviceSyncManager
import org.schabi.newpipe.sync.DeviceSyncSummary
import org.schabi.newpipe.sync.StructuredPreferenceCategory
import org.schabi.newpipe.sync.TrustedPeer

class DeviceSyncSettingsFragment : BasePreferenceFragment() {
    private val scanPairingCode = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::pairWithCode)
    }

    private val syncManager: DeviceSyncManager
        get() = DeviceSyncManager.get(requireContext())

    private lateinit var identityPreference: Preference
    private lateinit var statusPreference: Preference
    private lateinit var syncNowPreference: Preference
    private lateinit var trustedDevicesPreference: Preference
    private lateinit var showPairingCodePreference: Preference
    private lateinit var scanPairingCodePreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        identityPreference = requirePreference(R.string.device_sync_identity_key)
        statusPreference = requirePreference(R.string.device_sync_status_key)
        syncNowPreference = requirePreference(R.string.device_sync_sync_now_key)
        trustedDevicesPreference = requirePreference(R.string.device_sync_trusted_devices_key)
        showPairingCodePreference = requirePreference(R.string.device_sync_show_code_key)
        scanPairingCodePreference = requirePreference(R.string.device_sync_scan_code_key)

        syncNowPreference.setOnPreferenceClickListener {
            syncData()
            true
        }
        showPairingCodePreference.setOnPreferenceClickListener {
            createPairingCode()
            true
        }
        scanPairingCodePreference.setOnPreferenceClickListener {
            launchScanner()
            true
        }
        requirePreference<Preference>(R.string.device_sync_clear_devices_key)
            .setOnPreferenceClickListener {
                confirmClearTrustedDevices()
                true
            }
    }

    override fun onResume() {
        super.onResume()
        updateState()
        startListening()
    }

    private fun updateState() {
        identityPreference.summary = syncManager.peerId
        val peers = syncManager.trustedPeers
        syncNowPreference.isEnabled = peers.isNotEmpty()
        statusPreference.summary = statusSummary(peers)
        trustedDevicesPreference.summary = if (peers.isEmpty()) {
            getString(R.string.device_sync_no_trusted_devices)
        } else {
            peers.joinToString(separator = "\n\n", transform = ::trustedPeerSummary)
        }
    }

    private fun startListening() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.startListening()
                }
            } catch (error: Exception) {
                statusPreference.summary = error.message ?: getString(R.string.general_error)
            }
        }
    }

    private fun syncData() {
        setActionsEnabled(false)
        syncNowPreference.summary = getString(R.string.device_sync_sync_in_progress)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    syncManager.sync()
                }
                updateState()
                showSyncSummary(summary)
            } catch (error: Exception) {
                showError(R.string.device_sync_sync_failed, error)
            } finally {
                syncNowPreference.setSummary(R.string.device_sync_sync_now_summary)
                setActionsEnabled(true)
                updateState()
            }
        }
    }

    private fun createPairingCode() {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val code = withContext(Dispatchers.IO) {
                    syncManager.createPairingCode()
                }
                showPairingCodeDialog(code)
            } catch (error: Exception) {
                showError(R.string.device_sync_pairing_failed, error)
            } finally {
                setActionsEnabled(true)
            }
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.device_sync_scan_prompt))
            .setBeepEnabled(false)
            .setCaptureActivity(DeviceSyncCaptureActivity::class.java)
            .setOrientationLocked(false)
        scanPairingCode.launch(options)
    }

    private fun pairWithCode(code: String) {
        setActionsEnabled(false)
        scanPairingCodePreference.summary = getString(R.string.device_sync_pairing_in_progress)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val peer = withContext(Dispatchers.IO) {
                    syncManager.pair(code)
                }
                updateState()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_sync_pairing_succeeded, peer.deviceName),
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                showError(R.string.device_sync_pairing_failed, error)
            } finally {
                scanPairingCodePreference.setSummary(R.string.device_sync_scan_code_summary)
                setActionsEnabled(true)
            }
        }
    }

    private fun showPairingCodeDialog(code: String) {
        val binding = DialogDevicePairingBinding.inflate(layoutInflater)
        val qrSize = minOf(
            resources.displayMetrics.widthPixels -
                (PAIRING_DIALOG_HORIZONTAL_MARGIN_DP * resources.displayMetrics.density).toInt(),
            (MAX_QR_SIZE_DP * resources.displayMetrics.density).toInt()
        )
        binding.devicePairingQr.setImageBitmap(
            BarcodeEncoder().encodeBitmap(
                code,
                BarcodeFormat.QR_CODE,
                qrSize,
                qrSize
            )
        )
        binding.devicePairingQr.setBackgroundColor(Color.WHITE)
        binding.devicePairingPeerId.text = getString(
            R.string.device_sync_pairing_peer_id,
            abbreviatePeerId(syncManager.peerId)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_show_code_title)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun confirmClearTrustedDevices() {
        if (syncManager.trustedPeers.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.device_sync_no_trusted_devices,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_clear_devices_title)
            .setMessage(R.string.device_sync_clear_devices_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                clearTrustedDevices()
            }
            .show()
    }

    private fun clearTrustedDevices() {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.clearTrustedPeers()
                }
            } catch (error: Exception) {
                showError(R.string.device_sync_clear_devices_failed, error)
            } finally {
                setActionsEnabled(true)
                updateState()
            }
        }
    }

    private fun showSyncSummary(summary: DeviceSyncSummary) {
        val details = summary.attempts.joinToString(separator = "\n") { attempt ->
            val subscriptionDetails = categorySyncSummary(
                getString(R.string.device_sync_category_subscriptions),
                attempt.result?.sentChanges,
                attempt.result?.receivedChanges,
                attempt.error
            )
            val playlistDetails = categorySyncSummary(
                getString(R.string.device_sync_category_playlists),
                attempt.playlistResult?.sentChanges,
                attempt.playlistResult?.receivedChanges,
                attempt.playlistError
            )
            val watchHistoryDetails = categorySyncSummary(
                getString(R.string.device_sync_category_watch_history),
                attempt.watchHistoryResult?.sentChanges,
                attempt.watchHistoryResult?.receivedChanges,
                attempt.watchHistoryError,
                attempt.watchHistorySkipped
            )
            val searchHistoryDetails = categorySyncSummary(
                getString(R.string.device_sync_category_search_history),
                attempt.searchHistoryResult?.sentChanges,
                attempt.searchHistoryResult?.receivedChanges,
                attempt.searchHistoryError,
                attempt.searchHistorySkipped
            )
            val structuredPreferenceDetails =
                StructuredPreferenceCategory.entries.joinToString("\n") { category ->
                    val result = attempt.structuredPreferenceResults[category]
                    categorySyncSummary(
                        structuredPreferenceCategoryName(category),
                        result?.sentChanges,
                        result?.receivedChanges,
                        attempt.structuredPreferenceErrors[category]
                    )
                }
            "${attempt.peer.deviceName}\n$subscriptionDetails\n$playlistDetails" +
                "\n$watchHistoryDetails\n$searchHistoryDetails" +
                "\n$structuredPreferenceDetails"
        }
        val summaryText = getString(
            R.string.device_sync_sync_complete_summary,
            summary.succeeded,
            summary.failed,
            summary.sentChanges,
            summary.receivedChanges
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_sync_complete_title)
            .setMessage("$summaryText\n\n$details")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun categorySyncSummary(
        category: String,
        sentChanges: Int?,
        receivedChanges: Int?,
        error: String?,
        disabled: Boolean = false
    ): String {
        return if (disabled) {
            getString(
                R.string.device_sync_sync_category_disabled,
                category
            )
        } else if (sentChanges != null && receivedChanges != null) {
            getString(
                R.string.device_sync_sync_category_succeeded,
                category,
                sentChanges,
                receivedChanges
            )
        } else {
            getString(
                R.string.device_sync_sync_category_failed,
                category,
                error ?: getString(R.string.general_error)
            )
        }
    }

    private fun structuredPreferenceCategoryName(
        category: StructuredPreferenceCategory
    ): String {
        return getString(
            when (category) {
                StructuredPreferenceCategory.FEED_GROUPS ->
                    R.string.device_sync_category_feed_groups

                StructuredPreferenceCategory.HOME_TABS ->
                    R.string.device_sync_category_home_tabs

                StructuredPreferenceCategory.CHANNEL_PROFILES ->
                    R.string.device_sync_category_channel_profiles

                StructuredPreferenceCategory.FILTERS ->
                    R.string.device_sync_category_filters

                StructuredPreferenceCategory.SETTINGS ->
                    R.string.device_sync_category_settings

                StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                    R.string.device_sync_category_completed_downloads
            }
        )
    }

    private fun statusSummary(peers: List<TrustedPeer>): CharSequence {
        if (peers.isEmpty()) {
            return getString(R.string.device_sync_status_no_devices)
        }
        val errors = peers.count { it.lastSyncError != null }
        if (errors > 0) {
            return getString(R.string.device_sync_status_errors, errors)
        }
        val latestSync = peers.mapNotNull(TrustedPeer::lastSyncAtEpochMillis).maxOrNull()
        return if (latestSync == null) {
            getString(R.string.device_sync_status_ready, peers.size)
        } else {
            getString(
                R.string.device_sync_status_last_sync,
                relativeTime(latestSync)
            )
        }
    }

    private fun trustedPeerSummary(peer: TrustedPeer): String {
        val identity = getString(
            R.string.device_sync_trusted_device_summary,
            peer.deviceName,
            abbreviatePeerId(peer.peerId)
        )
        return when {
            peer.lastSyncError != null -> getString(
                R.string.device_sync_trusted_device_error,
                identity,
                peer.lastSyncError
            )

            peer.lastSyncAtEpochMillis != null -> getString(
                R.string.device_sync_trusted_device_last_sync,
                identity,
                relativeTime(peer.lastSyncAtEpochMillis)
            )

            else -> getString(
                R.string.device_sync_trusted_device_never_synced,
                identity
            )
        }
    }

    private fun relativeTime(epochMillis: Long): CharSequence {
        return DateUtils.getRelativeTimeSpanString(
            epochMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
    }

    private fun setActionsEnabled(enabled: Boolean) {
        showPairingCodePreference.isEnabled = enabled
        scanPairingCodePreference.isEnabled = enabled
        syncNowPreference.isEnabled = enabled && syncManager.trustedPeers.isNotEmpty()
    }

    private fun showError(title: Int, error: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(error.message ?: getString(R.string.general_error))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun abbreviatePeerId(peerId: String): String {
        if (peerId.length <= ABBREVIATED_PEER_ID_LENGTH) {
            return peerId
        }
        return "${peerId.take(PEER_ID_EDGE_LENGTH)}…${peerId.takeLast(PEER_ID_EDGE_LENGTH)}"
    }

    companion object {
        private const val MAX_QR_SIZE_DP = 420
        private const val PAIRING_DIALOG_HORIZONTAL_MARGIN_DP = 64
        private const val PEER_ID_EDGE_LENGTH = 8
        private const val ABBREVIATED_PEER_ID_LENGTH = PEER_ID_EDGE_LENGTH * 2
    }
}
