package org.schabi.newpipe.player.video

/** Session state survives UI/surface changes but manual projection never leaks to another video. */
class SphericalPlaybackState {
    enum class Mode { AUTO, FLAT, EQUIRECTANGULAR }

    var mode = Mode.AUTO
    var useSensorRotation = false
    private var item: String? = null
    private var extractorHint = false
    private var containerHint = false

    fun selectItem(key: String) {
        if (item == key) return
        item = key
        mode = Mode.AUTO
        extractorHint = false
        containerHint = false
    }

    internal fun currentItemKey(): String = item.orEmpty()

    fun setExtractorHint(spherical: Boolean) {
        extractorHint = spherical
    }
    fun setContainerHint(projectionData: ByteArray?) {
        containerHint = projectionData != null && projectionData.isNotEmpty()
    }

    fun isSpherical(): Boolean = when (mode) {
        Mode.AUTO -> extractorHint || containerHint
        Mode.FLAT -> false
        Mode.EQUIRECTANGULAR -> true
    }
}
