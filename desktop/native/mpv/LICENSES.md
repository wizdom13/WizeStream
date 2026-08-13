# Embedded playback licenses and sources

- `electron-mpv-video` 0.1.1 is MIT licensed. Its complete license and third-party notices are
  packaged separately and retained in `desktop/vendor/electron-mpv-video`.
- libmpv/mpv is distributed under GPL-2.0-or-later and LGPL-2.1-or-later components. WizeStream's
  Windows build comes from the checksum-pinned Shinchiro `mpv-winbuild-cmake` archive. Linux uses
  Ubuntu 24.04 `libmpv-dev` 0.37.0-1ubuntu4. macOS uses Homebrew mpv 0.41.0.
- Upstream source and license information: https://mpv.io and https://github.com/mpv-player/mpv.

The generated `manifest.json` in each artifact records the exact target and SHA-256 digest of every
staged native file.
