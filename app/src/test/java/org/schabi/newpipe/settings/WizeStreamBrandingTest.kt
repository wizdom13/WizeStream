package org.schabi.newpipe.settings

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

class WizeStreamBrandingTest {
    private val resourcesDirectory = listOf(
        Path.of("src/main/res"),
        Path.of("app/src/main/res")
    ).first { it.exists() }

    @Test
    fun `localized product strings do not restore the legacy brand`() {
        val upstreamOnlyResources = setOf(
            "donation_encouragement",
            "import_settings_vulnerable_format"
        )

        Files.walk(resourcesDirectory).use { paths ->
            paths
                .filter { it.fileName.toString() == "strings.xml" }
                .forEach { path ->
                    val document = newDocumentBuilder().parse(path.toFile())
                    val strings = document.getElementsByTagName("string")
                    for (index in 0 until strings.length) {
                        val string = strings.item(index) as Element
                        val name = string.getAttribute("name")
                        if (name in upstreamOnlyResources) {
                            continue
                        }

                        val text = string.textContent.lowercase(Locale.ROOT)
                        LEGACY_BRAND_VARIANTS.firstOrNull(text::contains)?.let { legacyBrand ->
                            fail(
                                "Legacy product brand '$legacyBrand' found in " +
                                    "$path string resource '$name'"
                            )
                        }
                    }
                }
        }
    }

    @Test
    fun `about links use WizeStream destinations`() {
        val document = newDocumentBuilder()
            .parse(resourcesDirectory.resolve("values/donottranslate.xml").toFile())
        val values = document.getElementsByTagName("string")
        val strings = buildMap {
            for (index in 0 until values.length) {
                val string = values.item(index) as Element
                put(string.getAttribute("name"), string.textContent)
            }
        }

        assertEquals(
            "https://github.com/wizdom13/WizeStream",
            strings["website_url"]
        )
        assertEquals(
            "https://github.com/wizdom13/WizeStream/blob/pipe/PRIVACY.md",
            strings["privacy_policy_url"]
        )
        assertEquals(
            "https://github.com/wizdom13/WizeStream/blob/pipe/docs/faq.md",
            strings["faq_url"]
        )
    }

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }.newDocumentBuilder()

    companion object {
        private val LEGACY_BRAND_VARIANTS = listOf(
            "newpipe",
            "new pipe",
            "nepipe",
            "نيوپايپ",
            "نيو پايپ",
            "نيوبايب",
            "نیوپایپ",
            "نیو پائپ",
            "نیوپائیپ",
            "নিউপাইপ",
            "নিউ পাইপ",
            "न्यूपाइप",
            "न्यू पाइप",
            "ന്യൂപൈപ്പ്",
            "ന്യൂപൈപ്പ",
            "நியூபைப்",
            "நியூபைப்ப",
            "న్యూపిప్",
            "న్యూప్యాప్",
            "న్యూపెయిప్",
            "న్యూపైప్",
            "ਨਿਊਪਾਈਪ",
            "ਨਿਊ ਪਾਈਪ",
            "ߣߌߎߔߌߔ",
            "ᱱᱤᱭᱩ ᱯᱟᱭᱯᱮ",
            "ᱱᱤᱣ ᱯᱟᱭᱯ",
            "ନୂତନ ପାଇପ୍",
            "novoj cijevi"
        )
    }
}
