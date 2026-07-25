/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.graphics.Color
import android.os.Bundle
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

class DeviceSyncSettingsFragment : BasePreferenceFragment() {
    private val scanPairingCode = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::pairWithCode)
    }

    private val syncManager: DeviceSyncManager
        get() = DeviceSyncManager.get(requireContext())

    private lateinit var identityPreference: Preference
    private lateinit var trustedDevicesPreference: Preference
    private lateinit var showPairingCodePreference: Preference
    private lateinit var scanPairingCodePreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        identityPreference = requirePreference(R.string.device_sync_identity_key)
        trustedDevicesPreference = requirePreference(R.string.device_sync_trusted_devices_key)
        showPairingCodePreference = requirePreference(R.string.device_sync_show_code_key)
        scanPairingCodePreference = requirePreference(R.string.device_sync_scan_code_key)

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
    }

    private fun updateState() {
        identityPreference.summary = syncManager.peerId
        val peers = syncManager.trustedPeers
        trustedDevicesPreference.summary = if (peers.isEmpty()) {
            getString(R.string.device_sync_no_trusted_devices)
        } else {
            peers.joinToString(separator = "\n") { peer ->
                getString(
                    R.string.device_sync_trusted_device_summary,
                    peer.deviceName,
                    abbreviatePeerId(peer.peerId)
                )
            }
        }
    }

    private fun createPairingCode() {
        setPairingActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val code = withContext(Dispatchers.IO) {
                    syncManager.createPairingCode()
                }
                showPairingCodeDialog(code)
            } catch (error: Exception) {
                showError(error)
            } finally {
                setPairingActionsEnabled(true)
            }
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.device_sync_scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanPairingCode.launch(options)
    }

    private fun pairWithCode(code: String) {
        setPairingActionsEnabled(false)
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
                showError(error)
            } finally {
                scanPairingCodePreference.setSummary(R.string.device_sync_scan_code_summary)
                setPairingActionsEnabled(true)
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
                syncManager.clearTrustedPeers()
                updateState()
            }
            .show()
    }

    private fun setPairingActionsEnabled(enabled: Boolean) {
        showPairingCodePreference.isEnabled = enabled
        scanPairingCodePreference.isEnabled = enabled
    }

    private fun showError(error: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_pairing_failed)
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
