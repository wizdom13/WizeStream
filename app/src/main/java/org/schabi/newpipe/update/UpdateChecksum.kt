package org.schabi.newpipe.update

import java.io.File
import java.security.MessageDigest
import java.util.Locale

object UpdateChecksum {
    private const val SHA_256_PREFIX = "sha256:"
    private val SHA_256_REGEX = Regex("^[0-9a-fA-F]{64}$")

    fun fromGitHubDigest(digest: String?): String? {
        val value = digest?.trim().orEmpty()
        if (!value.startsWith(SHA_256_PREFIX, ignoreCase = true)) {
            return null
        }
        return value.substring(SHA_256_PREFIX.length)
            .takeIf(SHA_256_REGEX::matches)
            ?.lowercase(Locale.ROOT)
    }

    fun normalizeSha256(checksum: String?): String? {
        return checksum
            ?.trim()
            ?.takeIf(SHA_256_REGEX::matches)
            ?.lowercase(Locale.ROOT)
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }
    }

    fun matches(file: File, expectedSha256: String): Boolean {
        val normalizedExpected = normalizeSha256(expectedSha256) ?: return false
        val expectedBytes = normalizedExpected.hexToByteArray()
        val actualBytes = calculateSha256(file).hexToByteArray()
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
