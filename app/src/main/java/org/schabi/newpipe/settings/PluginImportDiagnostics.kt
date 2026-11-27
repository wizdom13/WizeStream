/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.settings

import android.content.Context
import android.os.Build
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.plugin.PreparedPluginPackage

/** Describes the bytes actually inspected, without changing package acceptance or exposing URIs. */
internal object PluginImportDiagnostics {
    private const val MAX_CANDIDATES = 5
    private const val MAX_LABEL_LENGTH = 256

    fun environment(context: Context): String = context.getString(
        R.string.plugins_error_environment,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE,
        Build.VERSION.SDK_INT
    )

    fun catalogMismatch(
        context: Context,
        prepared: PreparedPluginPackage,
        packageName: String,
        catalogName: String,
        entries: List<PluginRepository.Entry>,
        initialPackageSha256: String? = null
    ): String = buildString {
        appendLine(context.getString(R.string.plugins_local_catalog_mismatch))
        appendLine()
        val manifest = prepared.manifest
        appendLine(context.getString(R.string.plugins_error_package, packageName.take(MAX_LABEL_LENGTH)))
        appendLine(context.getString(R.string.plugins_error_identity, manifest.pluginId, manifest.versionName.take(MAX_LABEL_LENGTH), manifest.versionCode))
        appendLine(context.getString(R.string.plugins_error_checksum, prepared.sha256))
        if (initialPackageSha256 != null && !initialPackageSha256.equals(prepared.sha256, ignoreCase = true)) {
            appendLine(context.getString(R.string.plugins_error_package_changed, initialPackageSha256))
        }
        appendLine()
        appendLine(context.getString(R.string.plugins_error_catalog, catalogName.take(MAX_LABEL_LENGTH)))
        val candidates = entries.filter { it.pluginId == manifest.pluginId }
        when {
            entries.isEmpty() -> append(context.getString(R.string.plugins_error_empty_catalog))

            candidates.isEmpty() -> append(context.getString(R.string.plugins_error_missing_plugin, manifest.pluginId))

            else -> {
                candidates.take(MAX_CANDIDATES).forEachIndexed { index, entry ->
                    if (index > 0) appendLine()
                    appendLine(context.getString(R.string.plugins_error_identity, entry.pluginId, entry.versionName.take(MAX_LABEL_LENGTH), entry.versionCode))
                    appendLine(context.getString(R.string.plugins_error_checksum, entry.sha256))
                    val comparison = prepared.compare(entry)
                    appendLine(context.getString(if (comparison.checksumMatches) R.string.plugins_error_checksum_matches else R.string.plugins_error_checksum_differs))
                    appendLine(
                        comparison.manifestError?.let { context.getString(R.string.plugins_error_manifest, it) }
                            ?: context.getString(R.string.plugins_error_manifest_matches)
                    )
                    if (comparison.matches) appendLine(context.getString(R.string.plugins_error_listing_changed))
                }
                if (candidates.size > MAX_CANDIDATES) {
                    append(context.getString(R.string.plugins_error_more_entries, candidates.size - MAX_CANDIDATES))
                }
            }
        }
    }.trimEnd()
}
