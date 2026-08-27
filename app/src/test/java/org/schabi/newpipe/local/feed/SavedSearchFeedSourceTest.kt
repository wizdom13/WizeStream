package org.schabi.newpipe.local.feed

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedSearchFeedSourceTest {
    private val sourceDirectory = if (Files.exists(Path.of("src/main/java"))) {
        Path.of("src/main/java")
    } else {
        Path.of("app/src/main/java")
    }

    @Test
    fun databaseMigrationCreatesFeedAndCacheTables() {
        val migrations = read("org/schabi/newpipe/database/Migrations.kt")
        val database = read("org/schabi/newpipe/database/AppDatabase.kt")
        val databaseFactory = read("org/schabi/newpipe/NewPipeDatabase.kt")

        assertTrue(migrations.contains("DB_VER_23 = 23"))
        assertTrue(migrations.contains("MIGRATION_22_23"))
        assertTrue(migrations.contains("saved_search_feed"))
        assertTrue(migrations.contains("saved_search_feed_stream"))
        assertTrue(database.contains("version = Migrations.DB_VER_23"))
        assertTrue(database.contains("SavedSearchFeedEntity::class"))
        assertTrue(database.contains("SavedSearchFeedStreamEntity::class"))
        assertTrue(databaseFactory.contains("MIGRATION_22_23"))
    }

    @Test
    fun savedFeedsUseBoundedPersistentCacheAndManualRefresh() {
        val manager = read("org/schabi/newpipe/local/feed/SavedSearchFeedManager.kt")
        val search = read(
            "org/schabi/newpipe/fragments/list/search/SearchFragment.java"
        )
        val streamDao = read(
            "org/schabi/newpipe/database/stream/dao/StreamDAO.kt"
        )

        assertTrue(manager.contains("MAXIMUM_CACHED_ITEMS = 300"))
        assertTrue(manager.contains("distinctBy"))
        assertTrue(manager.contains("replaceCache"))
        assertTrue(manager.contains("appendCache"))
        assertTrue(search.contains("MENU_REFRESH_SEARCH_FEED"))
        assertTrue(search.contains("loadSavedSearchCache()"))
        assertTrue(search.contains("cacheSavedSearchResults(result.getItems(), false)"))
        assertTrue(streamDao.contains("saved_search_feed_stream ssf"))
    }

    @Test
    fun savedFeedsAreReachableAndManageable() {
        val subscriptions = read(
            "org/schabi/newpipe/local/subscription/SubscriptionFragment.kt"
        )
        val navigation = read("org/schabi/newpipe/util/NavigationHelper.java")
        val search = read(
            "org/schabi/newpipe/fragments/list/search/SearchFragment.java"
        )

        assertTrue(subscriptions.contains("showSavedSearchFeedsDialog()"))
        assertTrue(navigation.contains("openSavedSearchFeed"))
        assertTrue(search.contains("MENU_SAVE_SEARCH_FEED"))
        assertTrue(search.contains("MENU_DELETE_SEARCH_FEED"))
    }

    private fun read(relativePath: String): String = Files.readString(
        sourceDirectory.resolve(relativePath)
    )
}
