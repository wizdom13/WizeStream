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

internal class DownloadPickerResultPolicyTest {
    @ParameterizedTest
    @MethodSource("pickerResults")
    fun `maps picker result to handling action`(
        resultAccepted: Boolean,
        hasUri: Boolean,
        isOwnFileUri: Boolean,
        expected: DownloadPickerResultAction
    ) {
        assertEquals(
            expected,
            DownloadPickerResultPolicy.resolve(resultAccepted, hasUri, isOwnFileUri)
        )
    }

    private companion object {
        @JvmStatic
        fun pickerResults(): Stream<Arguments> = Stream.of(
            Arguments.of(
                false,
                true,
                true,
                DownloadPickerResultAction.CANCELLED
            ),
            Arguments.of(
                true,
                false,
                true,
                DownloadPickerResultAction.INVALID
            ),
            Arguments.of(
                true,
                true,
                true,
                DownloadPickerResultAction.OWN_FILE
            ),
            Arguments.of(
                true,
                true,
                false,
                DownloadPickerResultAction.DOCUMENT
            )
        )
    }
}
