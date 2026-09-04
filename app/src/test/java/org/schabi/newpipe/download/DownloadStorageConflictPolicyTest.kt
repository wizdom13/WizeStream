/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.schabi.newpipe.R
import us.shandian.giga.service.MissionState

class DownloadStorageConflictPolicyTest {
    @ParameterizedTest
    @MethodSource("conflicts")
    fun `maps mission state to its conflict prompt`(
        state: MissionState,
        positiveButton: Int,
        message: Int,
        action: DownloadConflictAction
    ) {
        val prompt = DownloadStorageConflictPolicy.forMissionState(state)

        assertEquals(positiveButton, prompt.positiveButton)
        assertEquals(message, prompt.message)
        assertEquals(action, prompt.action)
    }

    private companion object {
        @JvmStatic
        fun conflicts(): Stream<Arguments> = Stream.of(
            Arguments.of(
                MissionState.Finished,
                R.string.overwrite,
                R.string.overwrite_finished_warning,
                DownloadConflictAction.REPLACE
            ),
            Arguments.of(
                MissionState.Pending,
                R.string.overwrite,
                R.string.download_already_pending,
                DownloadConflictAction.REPLACE
            ),
            Arguments.of(
                MissionState.PendingRunning,
                R.string.generate_unique_name,
                R.string.download_already_running,
                DownloadConflictAction.UNIQUE_NAME
            ),
            Arguments.of(
                MissionState.None,
                R.string.overwrite,
                R.string.overwrite_unrelated_warning,
                DownloadConflictAction.REUSE_EXISTING
            )
        )
    }
}
