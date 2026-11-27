# HLS video downloads

The manual Download action supports complete HLS videos when a service has no
progressive video file. Direct files retain priority. The final output is a normal
MP4 containing the selected original tracks; the download does not re-encode media.

## Supported input

- URL-backed, muxed HLS video sources from the extractor, including a master
  playlist with a separate AAC audio rendition.
- Finalized playlists with `EXT-X-ENDLIST`, AVC video and optional AAC audio.
- MPEG-TS segments or fragmented MP4 segments with a stable initialization segment.
- One selected video variant and its default supported audio rendition. An
  advertised resolution limits variant selection; unknown quality selects the
  best supported variant.
- Signed resource URLs, byte ranges, provider request headers and the app proxy.

Live, unfinished, encrypted/DRM, discontinuous, gapped and content-steered playlists
are rejected. DASH, separate extractor-level HLS video/audio pairing, bulk HLS
downloads, and automatic HLS downloads are outside this implementation.

The download dialog labels the source as HLS and the output as MP4. Manifest size
is not presented as video size. Range selection, companion captions, MP3 conversion
and the thread selector are unavailable for an HLS selection. Independently
available progressive audio and subtitle downloads keep their normal controls.

## Download and export phases

1. `HlsDownloadPreparation` bounds and validates the master and selected media
   playlists, then keeps those exact bytes in a persistent private cache. Master
   downloads use explicit stream keys so other variants and audio languages are
   not downloaded accidentally.
2. `HlsDownloadMission` runs Media3's downloader in the ordinary download queue.
   Pausing cancels the active transport; resuming creates a new downloader over the
   same cache. A predecessor-worker chain prevents rapid pause/resume from opening
   a cache still in use. Progress and the selected plan survive process recreation.
3. After acquisition releases the cache, `HlsMp4Exporter` remuxes through a
   cache-only media source. There is no upstream network factory in this phase.
   Missing input fails instead of silently transferring data during postprocessing.
4. The exporter denies encoders and validates the resulting MP4's AVC/AAC tracks,
   nonempty samples and duration before the postprocessor copies it to the chosen
   destination. Silent input remains silent.
5. Successful completion removes the private input cache. Failed export or final
   writes retain it for retry. Cancellation releases readers before cache cleanup
   and removes output when the user requested deletion.

Redirects and cross-origin child resources use the existing download-header
credential boundaries. HTTP range responses must cover the requested span before
the cache may treat them as complete. Expired links fail with a request to reopen
the video; the downloader does not substitute new signed URLs into an existing
selected manifest.

## Validation

JVM regressions cover capability policy, real dialog/catalog behavior, request
construction, output metadata, manifest selection/validation, byte ranges, header
boundaries, serialization and worker lifecycle races.

`HlsMp4ExporterTest` uses generated color/tone fixtures to perform real Android
exports of muxed MPEG-TS, fragmented MP4 with separate audio, and silent AVC.
Platform `MediaExtractor` checks output tracks, samples and timestamps. Additional
cases remove a cached segment or cancel after export has written output, checking
that no incomplete file is published and the cache is released. A loopback guard
detects unintended network fallback. The fixtures contain no provider media.

The existing CI Android 23 shards and Android 35 job run these device tests.
Compiling their sources alone is not evidence that a media export succeeded.
