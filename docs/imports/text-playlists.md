# Importing a text list of video URLs

Open **Bookmarked Playlists → overflow menu → Import URL list**, choose a text file,
review the preview, and select an existing local playlist or enter a new playlist name.
Press **Import videos**. Remote bookmarked playlists cannot be modified by this importer.

The first version accepts YouTube video URLs, one per line, including `youtube.com/watch`,
`youtu.be`, `/shorts/`, `/live/`, and `/embed/` links. Mobile and music YouTube hosts are
accepted. Links are normalized to the video ID, removing timestamps, tracking parameters,
and playlist context. Channel links, playlist-only links, other services, local files,
credentials in URLs, and nonstandard ports are rejected.

Files must be UTF-8 (an optional BOM is accepted), no larger than 1 MiB, with at most
2,000 lines. Blank lines and lines beginning with `#` are ignored. The preview shows
counts, the first 50 valid links, and invalid line numbers. No database changes or
video requests occur until Import is pressed.

**Skip duplicate videos** is enabled by default. It skips repeated video IDs in the
file and videos already in the destination playlist. Turn it off to preserve repeated
occurrences. Successful videos retain their file order and append after existing items.
Existing playlist names, order, and stream metadata are preserved. Importing into a new
playlist always creates a separate playlist, even if another has the same name. To
avoid creating another playlist on repeat import, select the existing destination.

Existing saved video metadata is reused; otherwise the importer fetches video details
using WizeStream's normal extractor. Internet access is needed for those requests.
Failures are skipped and their original line numbers appear in the result alongside
added, duplicate, and invalid counts. No empty playlist is created when nothing imports.

The destination profile is captured when choosing the file. Changing profiles does not
redirect the import. Deleted destinations are rejected. Successful entries are saved in
one transaction after loading completes; errors during saving roll back all changes.
Duplicate detection is repeated inside that transaction to account for concurrent edits.
Playlists are picked up by the normal Device Sync reconciliation.

Keep the dialog open until completion. Cancel stops further processing; a request already
in progress may need to finish before cancellation is observed. No playlist changes are
made if cancelled before the final transaction commits. Cancellation cannot undo an import
that has already committed. Rotation retains the preview, destination, and progress;
after process recreation, select the file again.
