# Generated HLS export fixtures

`avc-aac.zip.base64` contains three seconds of a plain blue 64 × 64 image at 10 fps and a
440 Hz tone. All media was generated locally with FFmpeg 7.1.5; no external video, audio,
website, or credentials are included.

- `ts/index.m3u8` contains three one-second MPEG-TS segments with AVC Constrained Baseline
  video and mono AAC-LC audio at 48 kHz.
- `fmp4/index.m3u8` selects `video.m3u8` and a separate default `audio.m3u8` rendition.
  Their initialization fragments and media fragments contain the same generated AVC/AAC
  content. AAC encoder padding makes the audio duration slightly longer than three seconds.
- All media playlists are finite VOD playlists with `#EXT-X-ENDLIST`, without encryption,
  discontinuities, or externally referenced resources.

The decoded ZIP SHA-256 is
`3eeb13f1ca109f457539e09bf0079337183cef5df1274128060dfa7ca9aba236`.

The instrumented test appends synthetic signed query parameters to the playlist references,
feeds every resource through a byte-array data source, and downloads into the actual Media3
cache. Export runs after releasing that cache instance, using the production exporter. A
loopback connection counter detects unexpected network fallback; no external service is used.
Android's `MediaExtractor` then checks both output tracks, their codecs, sample counts,
timestamps, and durations. These assertions require a device or emulator; JVM compilation alone
does not establish successful export.

## Regeneration

Run from an empty working directory with `ts` and `fmp4` subdirectories:

```sh
ffmpeg -f lavfi -i 'color=c=blue:s=64x64:r=10:d=3' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=3' \
  -map 0:v -map 1:a -c:v libx264 -profile:v baseline -pix_fmt yuv420p \
  -preset ultrafast -g 10 -keyint_min 10 -sc_threshold 0 -bf 0 \
  -c:a aac -b:a 32k -ac 1 -shortest -f hls -hls_time 1 -hls_playlist_type vod \
  -hls_segment_filename 'ts/segment-%02d.ts' ts/index.m3u8

ffmpeg -f lavfi -i 'color=c=blue:s=64x64:r=10:d=3' -an \
  -c:v libx264 -profile:v baseline -pix_fmt yuv420p -preset ultrafast \
  -g 10 -keyint_min 10 -sc_threshold 0 -bf 0 \
  -f hls -hls_time 1 -hls_playlist_type vod -hls_segment_type fmp4 \
  -hls_fmp4_init_filename video-init.mp4 \
  -hls_segment_filename 'fmp4/video-%02d.m4s' fmp4/video.m3u8

ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=3' -vn \
  -c:a aac -b:a 32k -ac 1 \
  -f hls -hls_time 1 -hls_playlist_type vod -hls_segment_type fmp4 \
  -hls_fmp4_init_filename audio-init.mp4 \
  -hls_segment_filename 'fmp4/audio-%02d.m4s' fmp4/audio.m3u8
```

Create `fmp4/index.m3u8` as:

```text
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-INDEPENDENT-SEGMENTS
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Tone",DEFAULT=YES,AUTOSELECT=YES,URI="audio.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=64x64,CODECS="avc1.42c00a,mp4a.40.2",AUDIO="audio"
video.m3u8
```

Archive the sorted files with ZIP deflate, fixed entry timestamp `2026-01-01 00:00:00`, and
regular-file permissions `0644`, then Base64-encode the ZIP. Different FFmpeg builds can change
the encoded bytes while preserving the fixture's behavior; update the checksum after regeneration.
