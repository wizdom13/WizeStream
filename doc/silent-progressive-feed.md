# Quiet progressive feed refresh

Based on upstream `pipe` at `1c5b7b6e7058cc49f3bb3bebd85f23b7f8bd592b`
(WizeStream 1.39.0, 10 November 2026). The progressive refresh from #1216 is
already integrated, including its stable per-scope/group highlight cutoff.

## Confirmed causes

Each asynchronous feed snapshot called `highlightNewItemsAfter`, which forced a
range of existing rows to rebind and restarted the bottom new-items animation.
The range could include many unchanged rows. Showing content also repeatedly hid
the refresh controls before the next progress state showed them again. RecyclerView
insert/move/change animations ran throughout the refresh.

## Changes

- Keep per-channel persistence, the existing 500 ms content coalescing, ordering,
  filtering, stable IDs, background service and cancellation behavior.
- Include each displayed row's highlight flag in Groupie's content comparison.
  Apply its highlight when it binds; remove the forced highlight-range rebinds.
  Keep the upstream stable highlight cutoff.
- Disable row animations during refresh and restore the existing animator afterward.
  Update empty/content visibility only when it changes and keep progress controls
  visible throughout a running refresh.
- Create one completion notice per successful refresh and scope/group. Preserve it
  across idle events, late database snapshots and observer reactivation. Consume it
  only once, after the current view actually presents visible new IDs. Progress,
  cancellation and errors do not create completion notices. Ignore obsolete view
  callbacks. Track IDs rather than publication dates for the announcement, so an
  already cached item does not become a new arrival just because it is highlighted.
- Use a generic localized completion message instead of an exact global count:
  filters and the existing 500-row limit can hide part of the fetched content.

This changes no thumbnail dimensions, layout settings, dependencies, database
schema, extraction concurrency or background execution infrastructure. It retains
the upstream version and standard debug package/signing configuration.

## Verification

Behavioral coverage exercises quiet intermediate updates, unchanged-row adapter
notifications, completion after a late snapshot, dismissal without re-announcement,
error/cancellation, refresh identity, cached IDs, diff equality and lifecycle replay.
The UI tests mount the actual fragment in Robolectric with a fake view model and
mocked unrelated preview/notes controllers; they do not exercise media decoding.
Build/test results are recorded with the delivered APK.

No device frame-time, battery or real-playback benchmark is claimed. Removing
repeated whole-range binds and refresh-time row animations is expected to reduce
jank, but the actual perceived smoothness still needs validation on a device.

### Cloud verification results (10 November 2026)

- `assembleDebug lintDebug testDebugUnitTest`: successful. All 2,088 JVM tests
  passed, including 84 feed tests and three actual-fragment Robolectric UI tests.
- Ktlint and Checkstyle passed. Android lint completed with 950 warnings and
  46 existing errors filtered by the repository baseline; the baseline was not
  changed. Source-inspection growth, database-version and changelog checks passed.
- The UI test uses disabled system animations to inspect completion banners;
  it checks adapter notifications and animator restoration, not real frame timing.
- Debug APK: WizeStream 1.39.0 (1039000),
  `org.wisso.newpipematerial.debug`, Android 6.0+ (API 23), target API 35.
  Built with JDK 21, Gradle 9.5.1, AGP 9.2.1 and Android SDK 37.0.
  APK signing, ZIP alignment and packaged 64-bit ELF 16 KB compatibility passed.
- The debug signing certificate matches the previously delivered 1.36.0 debug
  APK, allowing an update with its settings retained. The debug package installs
  separately from the official application. Installation requires permitting APKs
  from the browser/file manager. This is a development build, not a release.
- No emulator/device integration, live extraction, video decoding, playback,
  resource-usage or refresh-duration benchmark was executed. Existing background
  service behavior is preserved; continuity is covered by the host-side lifecycle
  tests rather than a real playback session.
