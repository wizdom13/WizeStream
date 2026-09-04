/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.content.SharedPreferences
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsJsonHelper

internal class HomeTabSyncAdapter(
    private val context: Context,
    private val preferences: SharedPreferences,
    database: AppDatabase,
    private val recordRepository: StructuredPreferenceRecordRepository
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.HOME_TABS

    private val feedGroupDao = database.feedGroupDAO()
    private val playlistDao = database.playlistDAO()
    private val playlistSyncDao = database.playlistSyncDAO()

    override fun snapshotHash(): String {
        return structuredPreferenceDigest(TabsJsonHelper.getJsonToSave(currentTabs()))
    }

    override fun reconcile(bootstrap: Boolean) {
        val tabs = currentTabs()
        if (tabs.size > MAX_HOME_TABS) {
            throw StructuredPreferenceSyncException(
                "There are too many home tabs to synchronize"
            )
        }
        val desiredTabs = tabs.mapNotNull(::toSyncedHomeTab)
            .associateBy(StructuredPreferenceRecordId::homeTab)
        desiredTabs.forEach { (recordId, tab) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.HOME_TAB,
                record = SyncedStructuredPreferenceRecord(homeTab = tab)
            )
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.HOME_TAB
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filterNot { it.recordId in desiredTabs }
            .forEach(recordRepository::saveLocalDelete)
        recordRepository.saveLocalUpsert(
            category = category,
            recordId = StructuredPreferenceRecordId.homeTabOrder(),
            recordType = StructuredPreferenceRecordType.HOME_TAB_ORDER,
            record = SyncedStructuredPreferenceRecord(
                homeTabOrder = SyncedHomeTabOrder(
                    tabs.mapNotNull(::toSyncedHomeTab)
                        .map(StructuredPreferenceRecordId::homeTab)
                )
            )
        )
    }

    override fun materialize() {
        val records = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.HOME_TAB
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .associateBy(StructuredPreferenceSyncRecordEntity::recordId)
        val order = recordRepository.getRecord(
            category,
            StructuredPreferenceRecordId.homeTabOrder()
        )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
            ?.let(recordRepository::decodeRecord)
            ?.homeTabOrder
            ?.tabRecordIds
            .orEmpty()
        val tabs = order.mapNotNull { recordId ->
            records[recordId]?.let(recordRepository::decodeRecord)?.homeTab
                ?.let(::toLocalHomeTab)
        }
        if (tabs.isNotEmpty()) {
            preferences.edit()
                .putString(
                    context.getString(R.string.saved_tabs_key),
                    TabsJsonHelper.getJsonToSave(tabs)
                )
                .commit()
        }
    }

    private fun toSyncedHomeTab(tab: Tab): SyncedHomeTab? {
        return when (tab) {
            is Tab.BlankTab -> SyncedHomeTab(SyncedHomeTabType.BLANK)

            is Tab.DefaultKioskTab -> SyncedHomeTab(SyncedHomeTabType.DEFAULT_KIOSK)

            is Tab.SubscriptionsTab -> SyncedHomeTab(SyncedHomeTabType.SUBSCRIPTIONS)

            is Tab.FeedTab -> SyncedHomeTab(SyncedHomeTabType.FEED)

            is Tab.BookmarksTab -> SyncedHomeTab(SyncedHomeTabType.BOOKMARKS)

            is Tab.HistoryTab -> SyncedHomeTab(SyncedHomeTabType.HISTORY)

            is Tab.DownloadsTab -> SyncedHomeTab(SyncedHomeTabType.DOWNLOADS)

            is Tab.KioskTab -> SyncedHomeTab(
                type = SyncedHomeTabType.KIOSK,
                serviceId = tab.kioskServiceId,
                kioskId = tab.kioskId
            )

            is Tab.ChannelTab -> SyncedHomeTab(
                type = SyncedHomeTabType.CHANNEL,
                serviceId = tab.channelServiceId,
                url = tab.channelUrl.trim(),
                name = tab.channelName.take(MAX_STRUCTURED_NAME_LENGTH)
            )

            is Tab.PlaylistTab -> {
                if (tab.playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                    val recordId = playlistSyncDao.getLocalMapping(tab.playlistId)
                        ?.playlistRecordId
                        ?: return null
                    SyncedHomeTab(
                        type = SyncedHomeTabType.LOCAL_PLAYLIST,
                        name = tab.playlistName.take(MAX_STRUCTURED_NAME_LENGTH),
                        linkedRecordId = recordId
                    )
                } else {
                    SyncedHomeTab(
                        type = SyncedHomeTabType.REMOTE_PLAYLIST,
                        serviceId = tab.playlistServiceId,
                        url = tab.playlistUrl.trim(),
                        name = tab.playlistName.take(MAX_STRUCTURED_NAME_LENGTH)
                    )
                }
            }

            is Tab.FeedGroupTab -> {
                val recordId = recordRepository.getFeedGroupMapping(tab.feedGroupId)
                    ?.groupRecordId
                    ?: return null
                SyncedHomeTab(
                    type = SyncedHomeTabType.FEED_GROUP,
                    name = tab.feedGroupName.take(MAX_STRUCTURED_NAME_LENGTH),
                    linkedRecordId = recordId
                )
            }

            else -> null
        }
    }

    private fun toLocalHomeTab(tab: SyncedHomeTab): Tab? {
        return when (tab.type) {
            SyncedHomeTabType.BLANK -> Tab.Type.BLANK.tab

            SyncedHomeTabType.DEFAULT_KIOSK -> Tab.Type.DEFAULT_KIOSK.tab

            SyncedHomeTabType.SUBSCRIPTIONS -> Tab.Type.SUBSCRIPTIONS.tab

            SyncedHomeTabType.FEED -> Tab.Type.FEED.tab

            SyncedHomeTabType.BOOKMARKS -> Tab.Type.BOOKMARKS.tab

            SyncedHomeTabType.HISTORY -> Tab.Type.HISTORY.tab

            SyncedHomeTabType.DOWNLOADS -> Tab.Type.DOWNLOADS.tab

            SyncedHomeTabType.KIOSK -> Tab.KioskTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.kioskId)
            )

            SyncedHomeTabType.CHANNEL -> Tab.ChannelTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.url),
                requireNotNull(tab.name)
            )

            SyncedHomeTabType.LOCAL_PLAYLIST -> {
                val mapping = playlistSyncDao.getLocalMapping(
                    requireNotNull(tab.linkedRecordId)
                ) ?: return null
                val playlist = playlistDao.getPlaylistDirect(mapping.playlistUid)
                    ?: return null
                Tab.PlaylistTab(
                    mapping.playlistUid,
                    playlist.name ?: requireNotNull(tab.name)
                )
            }

            SyncedHomeTabType.REMOTE_PLAYLIST -> Tab.PlaylistTab(
                requireNotNull(tab.serviceId),
                requireNotNull(tab.url),
                requireNotNull(tab.name)
            )

            SyncedHomeTabType.FEED_GROUP -> {
                val mapping = recordRepository.getFeedGroupMapping(
                    requireNotNull(tab.linkedRecordId)
                ) ?: return null
                val group = feedGroupDao.getGroupDirect(mapping.groupUid)
                    ?: return null
                Tab.FeedGroupTab(
                    group.uid,
                    group.name,
                    group.icon.drawableResource
                )
            }
        }
    }

    private fun currentTabs(): List<Tab> {
        val savedTabs = preferences.getString(
            context.getString(R.string.saved_tabs_key),
            null
        )
        return try {
            TabsJsonHelper.getTabsFromJson(savedTabs)
        } catch (_: TabsJsonHelper.InvalidJsonException) {
            TabsJsonHelper.getDefaultTabs()
        }
    }
}
