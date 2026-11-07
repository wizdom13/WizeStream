package org.schabi.newpipe.download

import android.content.Context
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilenameUtils
import us.shandian.giga.service.DownloadManager

/** A separate resumable subtitle mission; never overwrites an existing sidecar. */
internal object CompanionCaption {
    fun prepare(context: Context, video: StoredFileHelper, subtitle: SubtitlesStream): Pair<StoredFileHelper, DownloadMissionRequest> {
        val parent = requireNotNull(video.parentUri) { "A folder is required for companion captions" }
        val base = FilenameUtils.createFilename(context, filenameBase(video.name, subtitle.languageTag))
        val output = DownloadOutputPlanFactory.forSubtitle(base, subtitle.format, 0)
        val directory = StoredDirectoryHelper(context, parent, DownloadManager.TAG_VIDEO)
        val file = requireNotNull(directory.createUniqueFile(output.filename, output.mimeType))
        require(file.canWrite()) { "Caption file is not writable" }
        return file to DownloadMissionRequestFactory.forSubtitle(subtitle)
    }

    internal fun filenameBase(videoName: String, language: String): String = videoName.substringBeforeLast('.', videoName) + "." + language.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "sub" }
}
