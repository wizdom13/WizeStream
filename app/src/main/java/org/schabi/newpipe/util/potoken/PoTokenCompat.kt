package org.schabi.newpipe.util.potoken

/**
 * WizeStreamExtractor compatibility note.
 *
 * The latest WizeStreamExtractor source used on the `pipe` branch does not expose the
 * app-injected token callback API expected by other NewPipe-derived branches. Keep this
 * object as a documented no-op placeholder so the branch does not crash, fake tokens, or log
 * sensitive PoToken/signed URL values while the extractor lacks that integration point.
 */
object PoTokenCompat {
    val TAG: String? = PoTokenCompat::class.simpleName
}
