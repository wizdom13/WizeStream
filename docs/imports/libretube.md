# Importing a LibreTube backup

In LibreTube, create an advanced JSON backup and include the categories you want to
transfer. In WizeStream, open **Settings → Backup and restore → Import from LibreTube**,
choose that JSON file, review the destination profile and counts, select categories,
and press **Import selected categories**. Nothing is written during preview.

Supported categories:

- Subscriptions and channel groups, including group membership. Existing channel
  names, notification settings and memberships are preserved; new memberships are added.
  A group containing a channel absent from the subscriptions list imports that channel
  using its channel ID as the temporary name.
- Watch history. LibreTube does not export watch-event timestamps or repeat counts.
  Newly imported history uses the import time and one view; existing history wins.
- Playback positions, in milliseconds. Existing positions are never overwritten.
- Local playlists, preserving video order, repeated videos and empty lists. Names gain
  a `(LibreTube)` suffix. Identical named imported lists with the same ordered video
  URLs are reused on repeat import. Changed lists receive a numbered suffix instead
  of replacing an existing list. Playlist descriptions and custom artwork are not imported.
- Remote YouTube playlist bookmarks, deduplicated by playlist URL within the profile.

LibreTube writes both `localPlaylists` and a Piped-compatible `playlists` representation.
WizeStream prefers nonempty `localPlaylists`, using `playlists` only as a fallback,
so a backup does not import each playlist twice. Subscription/group aliases are supported.

Imports merge into the profile captured when choosing the backup. The preview identifies
that profile; switching profiles does not redirect the operation. A removed destination
causes the transaction to roll back. Selecting only certain categories leaves the others
untouched. Invalid individual entries are skipped; malformed or unsupported backup
structures are rejected. The import uses a database transaction, so failure or cancellation
before commit does not leave partial database writes. Do not leave the import screen until
completion. Rotation retains the preview and running operation; after process recreation,
select the file again. Reimporting the same backup does not duplicate existing entries.

This importer reads version 1 JSON exports with LibreTube's documented backup fields
(`format` may be `Piped` or `LibreTube`). Limits: 16 MiB, 50,000 category/nested-list entries,
and 32 levels of JSON nesting. ZIP files and raw SQLite databases are not supported.
No network requests are needed for importing; only validated YouTube identifiers are stored.
Settings, credentials, accounts, custom instances, downloads and search history are ignored.
Export back to LibreTube is not included.

Format references:
- https://github.com/libre-tube/LibreTube/blob/master/app/src/main/java/com/github/libretube/obj/BackupFile.kt
- https://github.com/libre-tube/LibreTube/blob/master/app/src/main/java/com/github/libretube/db/obj/WatchHistoryItem.kt
- https://github.com/libre-tube/LibreTube/blob/master/app/src/main/java/com/github/libretube/db/obj/WatchPosition.kt
