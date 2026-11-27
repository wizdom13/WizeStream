/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download.hls

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Download input is persistent and never shares the player's evictable cache. */
object HlsDownloadCache {
    private var providerContext: Context? = null
    private var sharedProvider: StandaloneDatabaseProvider? = null

    fun open(context: Context, cacheId: String): SimpleCache {
        val cache = SimpleCache(directory(context, cacheId), NoOpCacheEvictor(), databaseProvider(context))
        try {
            cache.checkInitialization()
            return cache
        } catch (error: Exception) {
            cache.release()
            throw error
        }
    }

    fun networkFactory(cache: Cache, upstreamFactory: DataSource.Factory): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)

    /** No upstream exists: a missing span fails instead of downloading during postprocessing. */
    fun offlineFactory(cache: Cache): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(null)
        .setCacheWriteDataSinkFactory(null)

    fun remove(context: Context, cacheId: String) {
        val folder = directory(context, cacheId)
        if (!SimpleCache.isCacheFolderLocked(folder)) {
            SimpleCache.delete(folder, databaseProvider(context))
        }
    }

    internal fun directory(context: Context, cacheId: String): File {
        require(cacheId.matches(Regex("[a-f0-9-]{36}"))) { "Invalid HLS cache identifier" }
        return File(context.filesDir, "hls-downloads/$cacheId")
    }

    @Synchronized
    private fun databaseProvider(context: Context): StandaloneDatabaseProvider {
        val application = context.applicationContext
        if (providerContext !== application) {
            // The application is stable for the process; replacing it also isolates test apps.
            sharedProvider?.close()
            sharedProvider = StandaloneDatabaseProvider(application)
            providerContext = application
        }
        return requireNotNull(sharedProvider)
    }
}
