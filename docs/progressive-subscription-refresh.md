# Progressive subscription refresh (#1211)

## Assessment before implementation

Inspected WizeStream `pipe` at `92c7e6e4474c94899b52383e28c0273a71838060`.
The following behavior is confirmed from the source:

- `FeedFragment.refreshFeed` starts `FeedLoadService`; leaving the fragment does
  not stop or bind that service. Opening a video uses the normal navigation path.
  The existing foreground service declares `dataSync`, exposes notification and
  feed cancel actions, rejects additional starts while loading, and returns
  `START_NOT_STICKY`. It lacks destruction cleanup and data-sync timeout handling.
- `FeedLoadManager` selects outdated, profile/group/service/mode-scoped
  subscriptions, shuffles them, and fetches on RxJava IO threads. Dedicated feeds
  have concurrency 5; full channel extractions have concurrency 3. Full YouTube
  extraction pauses 500–1500 ms between batches of 50. Tab pagination is bounded
  at eight continuation pages. Channel helpers bypass their info cache for a
  refresh. Downloader timeouts and extractor errors remain the existing policy.
- Each channel produces a materialized success/error notification. Results wait
  in a **20-channel buffer** before a transaction updates streams, feed membership,
  subscription metadata, and per-mode freshness. A final partial buffer flushes
  only at completion. The returned `Single` retains channel results for existing
  notification/download workers. Successful and partial channel content is stored;
  partial/failed channels remain outdated for retry. Final cleanup prunes old and
  orphan streams.
- `FeedViewModel` combines scope, preferences, refresh events and Room freshness
  metadata. It queries streams only for idle/success, produces an empty list for
  progress, and emits a separate progress-only screen state. Thus committed
  batches cannot appear during refresh; a recreated screen has no cached list.
- Feed SQL limits results to 500, groups by stream ID, and implements publication
  or first-discovery sorting with deterministic tie breakers. Stream identity is
  unique by service/URL; feed memberships are keyed by stream/subscription/mode.
  `FeedDatabaseManager.upsertAll` preserves first discovery and retains cached
  livestreams on partial extraction. The UI already uses Groupie's async diff
  adapter with stable stream IDs.
- `BackgroundFeedRefreshWorker`, `NotificationWorker`, and
  `AutomaticDownloadWorker` already reuse the manager. Periodic caching uses
  unique WorkManager work, network/battery constraints and bounded retries. Feed
  events are scope-specific, process-local replaying Rx processors. Profiles are
  captured by the manager, and writes use Room transactions.
- Existing JVM tests cover planning, cancellation delays, tab pagination, progress,
  event scoping, and background scheduling. Android tests cover partial refresh,
  transaction rollback, feed sorting/deduplication, scopes and profile isolation.

There is no confirmed navigation-triggered cancellation in this checkout. The
reported interruption is consistent with progress suppressing visible results;
actual playback/network contention and OS behavior require device validation.

## Options and decision

A timed/count buffer could reduce write frequency but adds timer, cancellation and
flush coordination. A new worker/service would duplicate existing lifecycle
ownership. Instead, commit each completed channel atomically on IO, retain the
existing service, and observe the feed query independently of progress. Throttle
and deduplicate visible snapshots before constructing items; reuse the async diff
adapter. Progress changes reuse the last item list rather than querying/rebuilding
it. This trades more short transactions for prompt availability and simpler
cancellation: already committed channels survive cancellation or later failure.
The existing concurrency limits, extraction pacing, refresh thresholds, scheduling,
notification result contract, database schema and application identity stay intact.

Navigation continues to dispose only screen observation, while the service owns
execution. Explicit cancellation, service destruction and Android 15 data-sync
timeout stop work and reset progress. Process death is intentionally not made
persistent/restartable. Existing independent scheduled workers may overlap a manual
refresh as before; cross-worker arbitration and alert ownership are outside this
change, while repeat service requests remain ignored.

The reactive query evaluates the optional future-upload filter against SQLite's
current time on each query, rather than freezing the cutoff at screen creation.
Existing one-shot DAO callers retain their explicit cutoff. Visible snapshots are
coalesced at 500 ms and deduplicated before item construction; progress keeps the
existing 120 ms throttle. This bounds rendering frequency, not extraction latency.

Feed observation follows LiveData's active observers. Leaving the screen disposes
only the query/render subscription; the service continues extraction and storage.
On return, observation reconnects to current Room data and replayed progress. This
avoids rebuilding hidden lists during playback. A result for a subscription deleted
while fetching is skipped inside its transaction, preventing a foreign-key failure
from aborting unrelated channels.

## Verification on the final source

- `assembleDebug lintDebug testDebugUnitTest`: successful; 1,999 JVM tests,
  zero failures, errors or skips, including all 66 feed tests and 12 added tests.
- New tests execute incremental Room persistence/observation, partial network
  failures, duplicates, transaction rollback, unsubscribe during fetch, cached
  livestream/discovery preservation, cancellation, future filtering, inactive and
  recreated observers, repeated service starts, activity navigation, destruction
  and timeout cleanup. Service tests use a fake loader and plain activities;
  they do not run video decoding or wait for Android's actual timeout quota.
- Kotlin lint, Checkstyle and dependency ordering passed. Checkstyle reports six
  existing warnings in untouched Java files. Android lint passed with the existing
  baseline: 931 warnings and 46 baseline-filtered errors. Touched-file warnings
  refer to unchanged note-badge refresh and progress-text code.
- Source-inspection test-growth, database-version and changelog checks passed.
  APK signature, ZIP alignment and packaged 64-bit native-library 16 KB checks
  passed.
- Cloud toolchain: Temurin 21.0.13+9, Gradle 9.5.1, AGP 9.2.1, Kotlin 2.3.21,
  SDK 37.0 revision 2, Build Tools 36.1.0; two Gradle workers and a 4 GiB heap.
  Final verification used in-process Kotlin compilation. Runtime-only overrides
  supplied Java's proxy/CA settings, writable Android/Gradle directories and a
  writable `user.home` for Robolectric. No repository build settings were changed.
- Debug APK: 1.36.0 / 1036000, `org.wisso.newpipematerial.debug`, minimum API 23,
  target API 35. The existing debug suffix and development signing allow side by
  side installation with the official release, with separate data. It cannot
  upgrade the release or a debug installation signed with another key.

No device/instrumentation tests or live performance benchmarks were run. This
cloud workspace has no configured emulator or `/dev/kvm`; an adb probe also failed
because its default configuration directory was read-only. First-channel early
availability and inactive-observer behavior are verified by offline tests. Earlier
visibility is expected; total duration, UI frame timing, memory/battery/network
use and simultaneous real playback require device measurement. Compilation is
not proof of functional Android device validation.

## Highlight correction in the replacement PR

The feed ViewModel retains a separate highlight baseline per service/mode scope.
A refresh captures the last observed pre-refresh timestamp; subsequent metadata,
progress and item snapshots cannot advance it. Completion, cancellation, filtering
and observer reactivation preserve it until the next refresh. The relative-time
header continues to use the current database timestamp. A screen first opened
mid-refresh uses its first available timestamp; an earlier timestamp cannot be
reconstructed if it was never observed.

Additional regression coverage exercises advancing metadata, missing timestamps,
completion, cancellation, successive refreshes and ViewModel reactivation.
The validation results above belong to the contributor's original feature head,
not this replacement. Local Gradle validation of the replacement was blocked by
network access to the Gradle distribution; its combined source requires PR CI.
