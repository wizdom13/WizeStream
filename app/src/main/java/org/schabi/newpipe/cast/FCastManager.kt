package org.schabi.newpipe.cast

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.CopyOnWriteArraySet
import org.fcast.sender_sdk.ApplicationInfo
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceDiscovererEventHandler
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaTrack
import org.fcast.sender_sdk.MediaTrackType
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.QueueState
import org.fcast.sender_sdk.ReceiverError
import org.fcast.sender_sdk.Source
import org.fcast.sender_sdk.TrackList
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.ListHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Application-scoped discovery and playback handoff for FCast-compatible TV receivers. */
object FCastManager {
    private const val TAG = "FCastManager"
    private const val RECEIVER_URL = "https://fcast.org"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val castContext by lazy { CastContext() }
    private val devices = linkedMapOf<String, DeviceInfo>()
    private val observers = CopyOnWriteArraySet<(List<DeviceInfo>) -> Unit>()
    private var discoverer: NsdDeviceDiscoverer? = null

    @Volatile
    private var activeDevice: CastingDevice? = null
    private var activeHandler: DeviceEventHandler? = null

    @Volatile
    private var activeDeviceName: String? = null

    @Volatile
    private var activePlaying = false

    @JvmStatic
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @JvmStatic
    fun canCast(context: Context, info: StreamInfo): Boolean = isAvailable() && selectSource(context, info) != null

    @JvmStatic
    fun showDevicePicker(context: Context, info: StreamInfo) {
        if (!isAvailable()) return
        val connectedDevice = activeDevice
        val connectedName = activeDeviceName
        if (connectedDevice?.isReady() == true && connectedName != null) {
            showActiveControls(context, info, connectedDevice, connectedName)
            return
        }
        showAvailableDevices(context, info)
    }

    private fun showAvailableDevices(context: Context, info: StreamInfo) {
        val source = selectSource(context, info)
        if (source == null) {
            Toast.makeText(context, R.string.cast_no_compatible_stream, Toast.LENGTH_LONG).show()
            return
        }

        startDiscovery(context.applicationContext)
        val availableDevices = mutableListOf<DeviceInfo>()
        val labels = mutableListOf<String>()
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, labels)
        lateinit var observer: (List<DeviceInfo>) -> Unit
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cast_to_tv)
            .setAdapter(adapter) { _, position ->
                availableDevices.getOrNull(position)?.let { connect(context, it, source) }
            }
            .setNeutralButton(R.string.cast_receiver_help) { _, _ ->
                ShareUtils.openUrlInApp(context, RECEIVER_URL)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        observer = { currentDevices ->
            availableDevices.clear()
            availableDevices.addAll(currentDevices.filter(::isResolved))
            labels.clear()
            labels.addAll(availableDevices.map(::deviceLabel))
            adapter.notifyDataSetChanged()
            dialog.setTitle(
                if (availableDevices.isEmpty()) {
                    R.string.cast_searching
                } else {
                    R.string.cast_to_tv
                }
            )
        }
        observers.add(observer)
        dialog.setOnDismissListener { observers.remove(observer) }
        dialog.show()
        observer(snapshot())
    }

    @Synchronized
    private fun startDiscovery(context: Context) {
        if (discoverer != null) return
        discoverer =
            NsdDeviceDiscoverer(
                context,
                object : DeviceDiscovererEventHandler {
                    override fun deviceAvailable(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                    override fun deviceChanged(deviceInfo: DeviceInfo) = updateDevice(deviceInfo)

                    override fun deviceRemoved(deviceName: String) {
                        synchronized(this@FCastManager) {
                            devices.remove(deviceName)
                        }
                        publishDevices()
                    }
                }
            )
    }

    private fun updateDevice(deviceInfo: DeviceInfo) {
        synchronized(this) {
            devices[deviceInfo.name] = deviceInfo
        }
        publishDevices()
    }

    private fun publishDevices() {
        val current = snapshot()
        mainHandler.post { observers.forEach { it(current) } }
    }

    @Synchronized
    private fun snapshot(): List<DeviceInfo> = devices.values.sortedBy { it.name.lowercase() }

    private fun connect(context: Context, deviceInfo: DeviceInfo, source: CastSource) {
        Toast.makeText(
            context,
            context.getString(R.string.cast_connecting, deviceInfo.name),
            Toast.LENGTH_SHORT
        ).show()
        runCatching {
            activeDevice?.disconnect()
            val device = castContext.createDeviceFromInfo(deviceInfo)
            val handler = playbackHandler(context.applicationContext, device, deviceInfo, source)
            activeDevice = device
            activeDeviceName = deviceInfo.name
            activePlaying = false
            activeHandler = handler
            device.connect(
                ApplicationInfo(
                    "WizeStream",
                    BuildConfig.VERSION_NAME,
                    "${Build.MANUFACTURER} ${Build.MODEL}"
                ),
                handler,
                1000uL
            )
        }.onFailure {
            Log.e(TAG, "Unable to connect to casting receiver", it)
            clearActiveDevice()
            showToast(context, context.getString(R.string.cast_failed, deviceInfo.name))
        }
    }

    private fun showActiveControls(
        context: Context,
        info: StreamInfo,
        device: CastingDevice,
        deviceName: String
    ) {
        val playPauseLabel = if (activePlaying) R.string.pause else R.string.play
        val actions = arrayOf(
            context.getString(playPauseLabel),
            context.getString(R.string.stop_casting),
            context.getString(R.string.change_tv)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.casting_to, deviceName))
            .setItems(actions) { _, position ->
                when (position) {
                    0 -> if (activePlaying) device.pausePlayback() else device.resumePlayback()

                    1 -> runCatching {
                        device.stopPlayback()
                        device.disconnect()
                    }.also { clearActiveDevice() }

                    2 -> {
                        runCatching { device.disconnect() }
                        clearActiveDevice()
                        showAvailableDevices(context, info)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun playbackHandler(
        context: Context,
        device: CastingDevice,
        deviceInfo: DeviceInfo,
        source: CastSource
    ): DeviceEventHandler = object : DeviceEventHandler {
        override fun connectionStateChanged(state: DeviceConnectionState) {
            when (state) {
                is DeviceConnectionState.Connected -> {
                    showToast(context, context.getString(R.string.cast_connected, deviceInfo.name))
                    runCatching {
                        device.load(
                            LoadRequest.Video(
                                contentType = source.contentType,
                                url = source.url,
                                resumePosition = 0.0,
                                speed = null,
                                volume = null,
                                metadata = null,
                                requestHeaders = null
                            ),
                            500uL
                        )
                    }.onFailure {
                        Log.e(TAG, "Unable to load cast media", it)
                        showToast(context, context.getString(R.string.cast_failed, deviceInfo.name))
                    }
                }

                DeviceConnectionState.Disconnected -> {
                    if (activeDevice === device) clearActiveDevice()
                }

                DeviceConnectionState.Connecting,
                DeviceConnectionState.Reconnecting -> Unit
            }
        }

        override fun volumeChanged(volume: Double) = Unit
        override fun timeChanged(time: Double) = Unit
        override fun playbackStateChanged(state: PlaybackState) {
            if (activeDevice === device) {
                activePlaying = state == PlaybackState.PLAYING
            }
        }
        override fun durationChanged(duration: Double) = Unit
        override fun speedChanged(speed: Double) = Unit
        override fun sourceChanged(source: Source) = Unit
        override fun playbackError(message: String) {
            Log.e(TAG, "Receiver playback error: $message")
            showToast(context, context.getString(R.string.cast_playback_failed))
        }
        override fun tracksAvailable(tracks: List<MediaTrack>) = Unit
        override fun trackSelected(id: UInt?, typ: MediaTrackType) = Unit
        override fun playbackStopped() = Unit
        override fun tracksChanged(tracks: TrackList) = Unit
        override fun queueChanged(queue: QueueState) = Unit
        override fun commandError(error: ReceiverError) {
            Log.e(TAG, "Receiver command error: $error")
        }
    }

    private fun selectSource(context: Context, info: StreamInfo): CastSource? {
        if (info.hlsUrl.isNotBlank()) {
            return CastSource(info.hlsUrl, "application/vnd.apple.mpegurl")
        }

        val videoStreams = info.videoStreams
            .filter { it.isUrl && !it.isVideoOnly }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .toMutableList()
        if (videoStreams.isNotEmpty()) {
            val index = ListHelper.getDefaultResolutionIndex(context, videoStreams)
                .coerceIn(0, videoStreams.lastIndex)
            return sourceFrom(videoStreams[index])
        }

        if (info.dashMpdUrl.isNotBlank()) {
            return CastSource(info.dashMpdUrl, "application/dash+xml")
        }

        return info.audioStreams.firstOrNull { it.isUrl }?.let(::sourceFrom)
    }

    private fun sourceFrom(stream: VideoStream): CastSource = CastSource(
        stream.content,
        stream.format?.mimeType ?: "video/mp4"
    )

    private fun sourceFrom(stream: AudioStream): CastSource = CastSource(
        stream.content,
        stream.format?.mimeType ?: "audio/mp4"
    )

    private fun isResolved(deviceInfo: DeviceInfo): Boolean = deviceInfo.port != 0.toUShort() && deviceInfo.addresses.isNotEmpty()

    private fun deviceLabel(deviceInfo: DeviceInfo): String {
        val protocol = if (deviceInfo.protocol == ProtocolType.F_CAST) "FCast" else "Cast"
        return "${deviceInfo.name} · $protocol"
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    @Synchronized
    private fun clearActiveDevice() {
        activeDevice = null
        activeDeviceName = null
        activeHandler = null
        activePlaying = false
    }

    private data class CastSource(val url: String, val contentType: String)
}
