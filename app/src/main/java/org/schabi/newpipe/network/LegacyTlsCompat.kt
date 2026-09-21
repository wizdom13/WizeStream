/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.network

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.schabi.newpipe.R

/**
 * Extends the legacy Android trust store with ISRG Root X1.
 *
 * Android 7.0 and earlier do not ship ISRG Root X1, while WizeStream still supports Android 6.
 * Modern Android versions continue to use the platform trust configuration unchanged.
 */
object LegacyTlsCompat {
    private const val TAG = "LegacyTlsCompat"

    @JvmStatic
    fun configure(context: Context, builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (!needsBundledIsrgRoot(Build.VERSION.SDK_INT)) {
            return builder
        }

        return try {
            val trustManager = createLegacyTrustManager(context.applicationContext)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (error: Exception) {
            // A compatibility aid must never prevent the app from starting. If the bundled root
            // cannot be loaded, retain Android's normal trust configuration and surface the
            // original network error to the caller.
            Log.w(TAG, "Could not install legacy ISRG Root X1 trust", error)
            builder
        }
    }

    internal fun needsBundledIsrgRoot(sdkInt: Int): Boolean {
        return sdkInt in Build.VERSION_CODES.M..Build.VERSION_CODES.N
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    internal fun createLegacyTrustManager(context: Context): X509TrustManager {
        val systemTrustManager = defaultTrustManager(null)
        val combinedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        systemTrustManager.acceptedIssuers.forEachIndexed { index, certificate ->
            combinedKeyStore.setCertificateEntry("system-$index", certificate)
        }
        combinedKeyStore.setCertificateEntry("isrg-root-x1", bundledIsrgRoot(context))

        return defaultTrustManager(combinedKeyStore)
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    internal fun bundledIsrgRoot(context: Context): X509Certificate {
        return context.resources.openRawResource(R.raw.isrgrootx1).use { input ->
            CertificateFactory.getInstance("X.509")
                .generateCertificate(input) as X509Certificate
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun defaultTrustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .singleOrNull()
            ?: throw GeneralSecurityException("Expected exactly one X509TrustManager")
    }
}
