/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.network

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LegacyTlsCompatInstrumentedTest {
    @Test
    fun bundledIsrgRootCanAnchorLegacyTrustManager() {
        assumeTrue(Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.N)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LegacyTlsCompat.bundledIsrgRoot(context)
        assertTrue(root.subjectX500Principal.name.contains("CN=ISRG Root X1"))

        val trustManager = LegacyTlsCompat.createLegacyTrustManager(context)
        trustManager.checkServerTrusted(arrayOf(root), "RSA")
    }
}
