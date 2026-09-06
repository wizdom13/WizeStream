package org.schabi.newpipe.extractor.utils

class HtmlParser {
    companion object {
        @JvmStatic
        fun htmlToString(html: String?): String? {
            if (html == null) {
                return null
            }

            val withNewLines = html.replace(Regex("(?i)<br\\s*/?>"), "\n")
            return withNewLines.replace(Regex("<[^>]*>"), "")
        }
    }
}
