package org.schabi.newpipe.player

internal object ChannelSkipPolicy {
    enum class Action {
        NONE,
        INTRO,
        OUTRO
    }

    fun decide(
        duration: Long,
        position: Long,
        start: Long,
        end: Long,
        freshStart: Boolean,
        manuallyOverridden: Boolean
    ): Action {
        if (
            manuallyOverridden || duration <= 0 || position < 0 ||
            start < 0 || end < 0 || start + end >= duration
        ) {
            return Action.NONE
        }
        if (freshStart && start > 0 && position < start) return Action.INTRO
        if (end > 0 && position >= duration - end && position < duration) return Action.OUTRO
        return Action.NONE
    }
}
