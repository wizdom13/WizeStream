/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.Context

/**
 * Device-local exclusions for media discovered through MediaStore or SAF.
 *
 * These identifiers are intentionally not part of Device Sync because file paths and document
 * URIs are device-specific.
 */
class LocalMediaExclusionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isExcluded(item: LocalMediaItem): Boolean {
        return item.contentUri in ignoredItems() || mediaFolderKey(item) in ignoredMediaFolders()
    }

    fun ignoreItem(item: LocalMediaItem) {
        updateSet(IGNORED_ITEMS_KEY) { it += item.contentUri }
    }

    fun ignoreMediaFolder(item: LocalMediaItem) {
        updateSet(IGNORED_MEDIA_FOLDERS_KEY) { it += mediaFolderKey(item) }
    }

    fun ignoreDocumentLocation(location: LocalMediaDocumentLocation) {
        updateSet(IGNORED_DOCUMENT_FOLDERS_KEY) { it += documentLocationKey(location) }
    }

    fun isDocumentLocationExcluded(location: LocalMediaDocumentLocation): Boolean {
        return documentLocationKey(location) in ignoredDocumentFolders()
    }

    fun isDocumentEntryExcluded(entry: LocalMediaDocumentEntry): Boolean {
        return if (entry.isDirectory) {
            isDocumentLocationExcluded(entry.location)
        } else {
            entry.contentUri in ignoredItems()
        }
    }

    fun ignoredCount(): Int {
        return ignoredItems().size +
            ignoredMediaFolders().size +
            ignoredDocumentFolders().size
    }

    fun clear() {
        preferences.edit()
            .remove(IGNORED_ITEMS_KEY)
            .remove(IGNORED_MEDIA_FOLDERS_KEY)
            .remove(IGNORED_DOCUMENT_FOLDERS_KEY)
            .apply()
    }

    private fun ignoredItems(): Set<String> =
        preferences.getStringSet(IGNORED_ITEMS_KEY, emptySet()).orEmpty()

    private fun ignoredMediaFolders(): Set<String> =
        preferences.getStringSet(IGNORED_MEDIA_FOLDERS_KEY, emptySet()).orEmpty()

    private fun ignoredDocumentFolders(): Set<String> =
        preferences.getStringSet(IGNORED_DOCUMENT_FOLDERS_KEY, emptySet()).orEmpty()

    private fun updateSet(key: String, mutate: (MutableSet<String>) -> Unit) {
        val updated = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        mutate(updated)
        preferences.edit().putStringSet(key, updated).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "local_media_exclusions"
        private const val IGNORED_ITEMS_KEY = "ignored_items"
        private const val IGNORED_MEDIA_FOLDERS_KEY = "ignored_media_folders"
        private const val IGNORED_DOCUMENT_FOLDERS_KEY = "ignored_document_folders"

        internal fun mediaFolderKey(item: LocalMediaItem): String {
            return listOf(
                item.volumeName.trim(),
                item.relativePath.trim().trim('/')
            ).joinToString("|")
        }

        internal fun documentLocationKey(location: LocalMediaDocumentLocation): String {
            return buildString {
                append(location.rootUri)
                append('|')
                append(location.path.joinToString("/"))
            }
        }
    }
}
