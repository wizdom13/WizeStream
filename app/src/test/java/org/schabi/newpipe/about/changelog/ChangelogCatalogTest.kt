package org.schabi.newpipe.about.changelog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogCatalogTest {
    private val catalog = ChangelogCatalog.parse(
        """
        <section id="unreleased"><h2>Unreleased</h2><p>Future work</p></section>
        <section data-version="1.4.0" data-version-code="1004000"><h2>1.4.0</h2><div class="release-notes"><p>Fourth release</p></div></section>
        <section data-version="1.3.0" data-version-code="1003000"><h2>1.3.0</h2><div class="release-notes"><p>Third release</p></div></section>
        <section data-version="1.2.0" data-version-code="1002000"><h2>1.2.0</h2><div class="release-notes"><p>Second release</p></div></section>
        <section data-version="1.1.0" data-version-code="1001000"><h2>1.1.0</h2><div class="release-notes"><p>First release</p></div></section>
        """.trimIndent()
    )

    @Test
    fun firstRunShowsOnlyTheInstalledRelease() {
        val notice = catalog.notice("1.3.0", 0)!!
        assertEquals(1003000, notice.code)
        assertEquals("1.3.0", notice.version)
        assertTrue(notice.html.contains("Third release"))
        assertFalse(notice.html.contains("Second release"))
        assertFalse(notice.html.contains("Fourth release"))
        assertFalse(notice.html.contains("Future work"))
    }

    @Test
    fun skippedReleasesAppearNewestFirstWithoutRepeatingSeenNotes() {
        val html = catalog.notice("1.3.0", 1001000)!!.html
        assertTrue(html.indexOf("Third release") < html.indexOf("Second release"))
        assertFalse(html.contains("First release"))
        assertFalse(html.contains("Fourth release"))
    }

    @Test
    fun sameVersionAndDowngradesDoNotPrompt() {
        assertNull(catalog.notice("1.3.0", 1003000))
        assertNull(catalog.notice("1.2.0", 1003000))
    }

    @Test
    fun nightlySuffixUsesTheReleaseInsteadOfItsOverriddenAndroidCode() {
        assertEquals(1003000, catalog.notice("1.3.0-nightly.20260913.abcdef", 1002000)!!.code)
        assertNull(catalog.notice("1.3.0-nightly.20260914.123456", 1003000))
    }

    @Test
    fun unknownOrIncompleteNotesAreNotMislabelledAsCurrent() {
        assertNull(catalog.notice("1.30.0", 0))
        assertNull(ChangelogCatalog.parse("<section data-version='1.3.0' data-version-code='bad'>Broken</section>").notice("1.3.0", 0))
        assertNull(ChangelogCatalog.parse("<section data-version='1.3.0' data-version-code='1003000'><div class='release-notes'></div></section>").notice("1.3.0", 0))
    }
}
