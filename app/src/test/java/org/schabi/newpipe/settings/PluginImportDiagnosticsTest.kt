/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.settings

import android.app.Application
import android.content.Context
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.plugin.PluginLocalSources
import org.schabi.newpipe.plugin.PreparedPluginPackage

/** Generated archives exercise the import comparison without loading or installing plugin code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PluginImportDiagnosticsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun aMatchingPackageStillMatchesIncludingUppercaseCatalogChecksums() {
        val fixture = fixture()
        fixture.prepare().use { prepared ->
            for (entry in listOf(fixture.entry(), fixture.entry { put("sha256", fixture.sha256.uppercase()) })) {
                val comparison = prepared.compare(entry)
                assertTrue(comparison.checksumMatches)
                assertNull(comparison.manifestError)
                assertTrue(comparison.matches)
                assertTrue(prepared.matches(entry))
            }
        }
    }

    @Test
    fun aChecksumOnlyMismatchReportsBothDigestsAndSuccessfulMetadataComparison() {
        val fixture = fixture()
        val entry = fixture.entry { put("sha256", OTHER_SHA256) }
        fixture.prepare().use { prepared ->
            val comparison = prepared.compare(entry)
            assertFalse(comparison.checksumMatches)
            assertNull(comparison.manifestError)
            assertFalse(comparison.matches)
            assertFalse(prepared.matches(entry))

            val details = details(prepared, listOf(entry))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum, fixture.sha256)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum, OTHER_SHA256)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum_differs)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_manifest_matches)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_listing_changed)))
        }
    }

    @Test
    fun aVersionMismatchIsRejectedEvenWhenTheChecksumMatches() {
        val fixture = fixture()
        val entry = fixture.entry {
            put("versionCode", 2)
            put("versionName", "2.0")
        }
        fixture.prepare().use { prepared ->
            val comparison = prepared.compare(entry)
            assertTrue(comparison.checksumMatches)
            assertFalse(comparison.matches)
            assertFalse(prepared.matches(entry))
            val reason = requireNotNull(comparison.manifestError)
            assertTrue(reason.contains("versionCode"))
            assertTrue(reason.contains("versionName"))

            val details = details(prepared, listOf(entry))
            assertTrue(details.contains(context.getString(R.string.plugins_error_identity, PLUGIN_ID, "1.0", 1)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_identity, PLUGIN_ID, "2.0", 2)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum_matches)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_manifest, reason)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_manifest_matches)))
        }
    }

    @Test
    fun aChecksumMismatchDoesNotHideTheManifestRejectionReason() {
        val fixture = fixture()
        val entry = fixture.entry {
            put("sha256", OTHER_SHA256)
            put("versionCode", 2)
            put("versionName", "2.0")
        }
        fixture.prepare().use { prepared ->
            val comparison = prepared.compare(entry)
            assertFalse(comparison.checksumMatches)
            assertFalse(comparison.matches)
            assertFalse(prepared.matches(entry))
            val reason = requireNotNull(comparison.manifestError)
            assertTrue(reason.contains("versionCode"))
            assertTrue(reason.contains("versionName"))

            val details = details(prepared, listOf(entry))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum_differs)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_manifest, reason)))
        }
    }

    @Test
    fun anUnsupportedApiKeepsItsOriginalFailureDespiteMatchingFieldsAndChecksum() {
        val fixture = fixture(apiVersion = "9.0")
        val entry = fixture.entry()
        fixture.prepare().use { prepared ->
            val comparison = prepared.compare(entry)
            assertTrue(comparison.checksumMatches)
            assertEquals("Plugin requires API 9.0", comparison.manifestError)
            assertFalse(comparison.matches)
            assertFalse(prepared.matches(entry))

            val details = details(prepared, listOf(entry))
            assertTrue(details.contains(context.getString(R.string.plugins_error_manifest, "Plugin requires API 9.0")))
            assertFalse(details.contains(context.getString(R.string.plugins_error_manifest_matches)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_listing_changed)))
        }
    }

    @Test
    fun anEmptyCatalogIsDistinguishedFromARejectedPackage() {
        fixture().prepare().use { prepared ->
            val details = details(prepared, emptyList())
            assertTrue(details.contains(context.getString(R.string.plugins_error_empty_catalog)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_package, PACKAGE_NAME)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_catalog, CATALOG_NAME)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_checksum_differs)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_missing_plugin, PLUGIN_ID)))
        }
    }

    @Test
    fun anUnrelatedCatalogDoesNotPresentAnotherPluginAsAnExpectedVersion() {
        val fixture = fixture()
        val entry = fixture.entry {
            put("pluginId", "org.example.different.plugin")
            put("versionName", "unrelated-version")
        }
        fixture.prepare().use { prepared ->
            val details = details(prepared, listOf(entry))
            assertTrue(details.contains(context.getString(R.string.plugins_error_missing_plugin, PLUGIN_ID)))
            assertFalse(details.contains("unrelated-version"))
            assertFalse(details.contains(context.getString(R.string.plugins_error_checksum_differs)))
            assertFalse(details.contains(context.getString(R.string.plugins_error_empty_catalog)))
        }
    }

    @Test
    fun aLargeCatalogShowsAtMostFiveRelevantCandidatesAndCountsTheRemainder() {
        val fixture = fixture()
        val entries = (2..8).map { version ->
            fixture.entry {
                put("versionCode", version)
                put("versionName", "catalog-$version")
            }
        }
        val unrelated = fixture.entry {
            put("pluginId", "org.example.different.plugin")
            put("versionName", "unrelated-version")
        }
        fixture.prepare().use { prepared ->
            val details = details(prepared, listOf(unrelated) + entries)
            for (version in 2..6) {
                assertTrue(details.contains(context.getString(R.string.plugins_error_identity, PLUGIN_ID, "catalog-$version", version)))
            }
            assertFalse(details.contains("catalog-7"))
            assertFalse(details.contains("catalog-8"))
            assertFalse(details.contains("unrelated-version"))
            assertTrue(details.contains(context.getString(R.string.plugins_error_more_entries, 2)))
        }
    }

    @Test
    fun changedPackageContentsIncludeTheInitialAndCurrentChecksums() {
        val fixture = fixture()
        val entry = fixture.entry { put("sha256", OTHER_SHA256) }
        fixture.prepare().use { prepared ->
            val details = details(prepared, listOf(entry), initialSha256 = OTHER_SHA256)
            assertTrue(details.contains(context.getString(R.string.plugins_error_package_changed, OTHER_SHA256)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum, fixture.sha256)))
            assertFalse(prepared.matches(entry))
        }
    }

    @Test
    fun anUnchangedOrAbsentInitialChecksumDoesNotClaimThePackageChanged() {
        val fixture = fixture()
        val entry = fixture.entry { put("sha256", OTHER_SHA256) }
        fixture.prepare().use { prepared ->
            for (initial in listOf(null, fixture.sha256, fixture.sha256.uppercase())) {
                val unchangedDetails = details(prepared, listOf(entry), initialSha256 = initial)
                if (initial != null) {
                    assertFalse(unchangedDetails.contains(context.getString(R.string.plugins_error_package_changed, initial)))
                }
                assertEquals(
                    details(prepared, listOf(entry)),
                    unchangedDetails
                )
            }
        }
    }

    @Test
    fun aMatchingCandidateExplainsThatTheSelectedListingChanged() {
        val fixture = fixture()
        fixture.prepare().use { prepared ->
            val details = details(prepared, listOf(fixture.entry()))
            assertTrue(details.contains(context.getString(R.string.plugins_error_checksum_matches)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_manifest_matches)))
            assertTrue(details.contains(context.getString(R.string.plugins_error_listing_changed)))
        }
    }

    private fun details(
        prepared: PreparedPluginPackage,
        entries: List<PluginRepository.Entry>,
        initialSha256: String? = null
    ) = PluginImportDiagnostics.catalogMismatch(context, prepared, PACKAGE_NAME, CATALOG_NAME, entries, initialSha256)

    private fun Fixture.prepare(): PreparedPluginPackage = bytes.inputStream().use {
        PluginLocalSources.preparePackage(context, it)
    }

    private fun fixture(apiVersion: String = "2.0"): Fixture {
        val metadata = JsonObject().apply {
            put("pluginId", PLUGIN_ID)
            put("name", "Import diagnostics fixture")
            put("versionCode", 1)
            put("versionName", "1.0")
            put("host", "wizestream")
            put("apiVersion", apiVersion)
            put("author", "Fixture publisher")
            put("entryClass", "org.example.diagnostics.MustNotBeLoaded")
        }
        val bytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                val files = linkedMapOf(
                    "plugin.json" to JsonWriter.string(metadata).toByteArray(),
                    "classes.dex" to byteArrayOf(1, 2, 3)
                )
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
        return Fixture(bytes, metadata, Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(bytes)))
    }

    private data class Fixture(val bytes: ByteArray, val metadata: JsonObject, val sha256: String) {
        fun entry(change: JsonObject.() -> Unit = {}): PluginRepository.Entry {
            val entry = JsonObject().apply {
                putAll(metadata)
                put("package", "fixture.ppp")
                put("sha256", sha256)
                // Comparison does not authenticate signatures; installation has a separate gate.
                put("signature", "fixture-signature")
                put("publicKey", "BHXTB1DqhMt9/MVbz63M5/YPOkEtuDPh72ueB4DqUsc=")
                change()
            }
            val catalog = JsonObject().apply {
                put("formatVersion", 1)
                put("name", CATALOG_NAME)
                put("plugins", JsonArray(listOf(entry)))
            }
            return PluginRepository.parse("https://example.org/fixtures/index.json", JsonWriter.string(catalog)).plugins.single()
        }
    }

    private companion object {
        const val PLUGIN_ID = "org.example.diagnostics.fixture"
        const val PACKAGE_NAME = "fixture.ppp"
        const val CATALOG_NAME = "fixture-index.json"
        val OTHER_SHA256 = "f".repeat(64)
    }
}
