package org.schabi.newpipe.extractor.utils

import java.util.regex.Pattern

class RegexUtils {
    companion object {
        @JvmStatic
        fun extract(input: String, regex: String): String? {
            val matcher = Pattern.compile(regex).matcher(input)
            return if (matcher.find()) matcher.group(0) else null
        }
    }
}
