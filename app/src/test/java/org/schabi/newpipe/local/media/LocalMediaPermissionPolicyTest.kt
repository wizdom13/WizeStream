/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LocalMediaPermissionPolicyTest {
    @Test
    fun `pre runtime permission devices need no media permission`() {
        assertArrayEquals(
            emptyArray<String>(),
            LocalMediaPermissionPolicy.requiredPermissions(22)
        )
    }

    @Test
    fun `legacy devices use the shared storage permission`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            LocalMediaPermissionPolicy.requiredPermissions(32)
        )
    }

    @Test
    fun `android 13 requests audio and video independently`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            ),
            LocalMediaPermissionPolicy.requiredPermissions(33)
        )
    }
}
