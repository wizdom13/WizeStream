# electron-mpv-video

English | [简体中文](README.zh-CN.md)

Bring libmpv's broad codec and container support to Electron, enabling playback of more video formats while keeping the video in Chromium's rendering pipeline, so your app can place HTML controls and other UI on top.

![electron-mpv-video demo](demo/screenshot.jpg)

The package exposes three integration entry points:

- `electron-mpv-video/main`: main-process lifecycle and IPC service.
- `electron-mpv-video/preload`: composable `contextBridge` API.
- `electron-mpv-video/renderer`: the UI-free `<mpv-video>` custom element.

The package is currently distributed as a **source-built native addon**. Installing it requires an external libmpv SDK/runtime; supported platforms have conventional default locations, and environment variables can override them. libmpv binaries are not stored in this repository.

## Media format support

Playback support comes from the libmpv build and the FFmpeg libraries it was compiled with. Common inputs include:

- **Video containers:** MP4/MOV, Matroska (MKV/WebM), AVI, MPEG-TS/M2TS, MPEG-PS/VOB, FLV, and Ogg.
- **Video codecs:** H.264/AVC, H.265/HEVC, AV1, VP9, VP8, MPEG-2 Video, and MPEG-4 Part 2.
- **Audio formats and codecs:** MP3, AAC/M4A, FLAC, Opus, Vorbis, WAV/PCM, AC-3/E-AC-3, and DTS.
- **Streaming formats and protocols:** HLS (`.m3u8`), MPEG-DASH (`.mpd`), direct HTTP/HTTPS media URLs, RTSP/RTP, RTMP, and SRT.

Exact availability depends on how libmpv and FFmpeg were built. [View the full format and codec list](https://ffmpeg.org/general.html#Supported-File-Formats_002c-Codecs-or-Features), or see [mpv's protocol documentation](https://mpv.io/manual/master/#protocols) for network playback details.

## Supported targets

The currently implemented native targets are:

- macOS arm64
- Windows x64

Linux is not supported yet.

## Electron compatibility

This package intentionally does not declare an Electron peer dependency yet.

- `shared-texture` requires Electron 40 or newer and uses `electron.sharedTexture` plus a WebGPU `VideoFrame` renderer. It is currently tested with Electron 40.10.5.
- `webgl` uses the software libmpv render pipeline and uploads RGBA frames to WebGL2. It does not call the Electron shared texture API.
- `canvas2d` uses the same software pipeline and draws RGBA frames with Canvas 2D. It does not call the Electron shared texture API.

Only Electron 40.10.5 is part of the current validation baseline. Compatibility with older Electron releases is not guaranteed even for the software modes.

## Native build requirements

- Node.js 22+
- Python and a working `node-gyp` toolchain
- A libmpv SDK/runtime matching the target platform and architecture

The build uses these defaults:

| Platform | Headers | Link library | Runtime directory |
| --- | --- | --- | --- |
| macOS arm64 | `/opt/homebrew/include` | `/opt/homebrew/lib/libmpv.dylib` | `/opt/homebrew/opt/mpv/lib` |
| Windows x64 | `%USERPROFILE%\libmpv\include` | `%USERPROFILE%\libmpv\lib\mpv.lib` | `%USERPROFILE%\libmpv\bin` |

Override any default when necessary:

- `MPV_INCLUDE_DIR`: directory containing `mpv/client.h`
- `MPV_LIB`: full path to the linker input (`libmpv.dylib` or `mpv.lib`)
- `MPV_RUNTIME_DIR`: directory containing the runtime dylib/DLL files

`npm run build:native` validates the resolved paths, builds the addon, and stages the runtime files next to `mpv_addon.node`.

### macOS arm64

Install libmpv, you don't need to set additional environment variables when installing with Homebrew:

```sh
brew install mpv
npm install electron-mpv-video
```

When developing in this repository, run 'npm install' or 'npm run build:native' after installation.
If you build using a custom libmpv, you can explicitly set the environment variables:

```sh
export MPV_INCLUDE_DIR=/custom/include
export MPV_LIB=/custom/lib/libmpv.dylib
export MPV_RUNTIME_DIR=/custom/lib
```

The post-build step copies dylibs from the runtime directory beside the addon,
rewrites the addon's direct libmpv reference to
`@loader_path/libmpv.dylib`, and ad-hoc signs the staged libmpv copy. Homebrew's
libmpv may still refer to other Homebrew libraries. A portable packaged app
must also bundle and relocate every required non-system dependency.

The shared-texture implementation uses OpenGL and IOSurface. The current
minimum deployment target in `binding.gyp` is macOS 12.0, while the actual
minimum also depends on the selected libmpv build.

Download/install references:

- https://brew.sh/
- https://formulae.brew.sh/formula/mpv
- https://mpv.io/installation/

### Windows x64

1. Install Visual Studio Build Tools with the **Desktop development with C++**
   workload.
2. Open the latest [Shinchiro Windows builds](https://github.com/shinchiro/mpv-winbuild-cmake/releases/latest) release and download the non-`v3`
   `mpv-dev-x86_64-...7z` archive.
3. Arrange the extracted files under the current user's profile:

```text
%USERPROFILE%\libmpv\
├── include\mpv\...
├── lib\mpv.lib
└── bin\libmpv-2.dll
```

Download references:

- https://github.com/shinchiro/mpv-winbuild-cmake/releases/latest
- https://mpv.io/installation/

The development archive contains headers, `libmpv-2.dll`, and the MinGW import
library `libmpv.dll.a`. Copy the headers to `include\mpv` and the DLL to
`bin`. This project builds with MSVC, so generate `lib\mpv.lib` from the DLL
before building:

```powershell
# Run from an x64 Native Tools Command Prompt for Visual Studio.
powershell -ExecutionPolicy Bypass `
  -File .\scripts\create-libmpv-import-lib.ps1

npm install electron-mpv-video
```

The helper uses the `%USERPROFILE%\libmpv` layout by default. Environment
variables still support a custom location:

```powershell
$env:MPV_INCLUDE_DIR = 'D:\sdk\libmpv\include'
$env:MPV_LIB = 'D:\sdk\libmpv\lib\mpv.lib'
$env:MPV_RUNTIME_DIR = 'D:\sdk\libmpv\bin'
```

You can also pass `-DllPath` and `-OutputPath` directly to the import-library
helper. After a successful build, every DLL in the resolved runtime directory
is copied next to `mpv_addon.node`.

## Install

```sh
npm install electron-mpv-video
```

The package install script runs `npm run build:native`. Environment variables
are only required when overriding the platform defaults.

For development in this repository:

```sh
npm install
npm run build
npm run dev
```

## Electron integration

### Main process

Create one service for the Electron application and attach every window that may create players:

```ts
import { app, BrowserWindow } from 'electron'
import { createMpvMain } from 'electron-mpv-video/main'

const mpv = createMpvMain()

async function createWindow() {
  const window = new BrowserWindow({
    webPreferences: {
      preload: '/absolute/path/to/your-preload.cjs',
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mpv.attachWindow(window)
  await window.loadFile('index.html')
}

app.on('before-quit', () => {
  void mpv.dispose()
})
```

`attachWindow()` authorizes the window's renderer to create sessions. Sessions are isolated by their owning `webContents` and are destroyed when the window closes.

An optional addon path can be supplied for custom build layouts:

```ts
const mpv = createMpvMain({
  addonPath: '/absolute/path/to/mpv_addon.node',
})
```

### Preload

Call `exposeMpvApi()` from the application's existing preload:

```ts
import { exposeMpvApi } from 'electron-mpv-video/preload'

exposeMpvApi()
```

It exposes the API as:

```ts
window._electronMpvVideo
```

The preload registers the shared-texture receiver before renderer code creates a player. The current source-built integration uses `sandbox: false` so the application preload can load the npm package.

### Renderer

Register the custom element explicitly:

```ts
import { defineMpvVideoElement } from 'electron-mpv-video/renderer'

// Safe to call more than once.
defineMpvVideoElement()
```

Then create the element in HTML:

```html
<mpv-video render-mode="shared-texture" volume="80"></mpv-video>
```

The element contains only its rendering canvases and the minimum layout styles required for them. It does not ship controls, themes, or an external CSS file. Size and surrounding UI are controlled by the application:

```css
.player-frame {
  position: relative;
  width: 960px;
  aspect-ratio: 16 / 9;
}

.player-frame mpv-video {
  position: absolute;
  inset: 0;
}
```

```ts
const video = document.querySelector('mpv-video')!

await video.open('/absolute/path/to/video.mkv')
await video.play()
await video.pause()
await video.seek(30)
await video.setVolume(70)
```

Switching between the `shared-texture` and `software(webgl/canvas2d)` pipelines recreates the native player and restores:

- media source
- current playback time
- volume
- paused or playing state
- stopped state

Switching between `webgl` and `canvas2d` keeps the same software native player and only changes the renderer canvas.

### Element properties and methods

Properties:

- `src`
- `loop`
- `volume`
- `mode`
- `currentTime`
- `duration`
- `videoWidth`
- `videoHeight`
- `rendererName`
- `playerId`

Methods:

- `open(source)`
- `play()`
- `pause()`
- `stop()`
- `seek(seconds)`
- `setVolume(value)`
- `setRenderMode(mode)`
- `destroy()`

Events:

- `mpv-state`: normalized player and renderer state
- `mpv-event`: raw normalized libmpv event
- `mpv-error`: renderer, event-pump, or native rendering error

## Low-level renderer API

Applications that do not want the custom element can use the preload session directly:

```ts
const player = await window._electronMpvVideo.create({
  renderMode: 'webgl',
  width: 960,
  height: 540,
})

const disposeFrame = player.onFrame((frame) => {
  // Draw frame.rgba into an application-owned surface.
})

await player.open('/absolute/path/to/video.mp4')
await player.play()

// Later:
disposeFrame()
await player.destroy()
```

## Packaged applications

Native modules and their adjacent dynamic libraries cannot be loaded directly from an asar archive. Configure the application packager to unpack:

```text
node_modules/electron-mpv-video/native/mpv-addon/build/Release/*.{node,dylib,dll}
```

If the packager does not support brace expansion, add separate patterns for
`*.node`, `*.dylib`, and `*.dll`. The unpacked package must preserve the same
relative path because the main-process loader resolves the addon there and the
runtime loader resolves the staged libmpv files beside it.

On macOS, bundling only `libmpv.dylib` is sufficient only when all of its other
non-system dependencies are also discoverable. On Windows, keep every DLL from
the selected libmpv build together in the same unpacked directory.

## Demo

The root-level `demo` application consumes the same public subpath exports as an external Electron application:

```sh
npm run dev
```

For a production build:

```sh
npm run build
npm start
```

Paste an absolute media path or use the Demo's file picker. The file picker belongs to the Demo and is not part of the library API.

## Validation

```sh
npm run check
npm run build:native
npm run test:native
npm pack --dry-run
```

The repository also includes an Electron runtime test that opens a generated or local media file and switches between software and shared-texture pipelines while paused and playing:

```sh
MPV_SMOKE_SOURCE=/absolute/path/to/video.mp4 npm run test:electron
```

This test requires a graphical session, a supported Electron/macOS or Electron/Windows runtime, and a working libmpv installation.

## License

The original `electron-mpv-video` source code is licensed under the
[MIT License](LICENSE).

libmpv is an external runtime/build dependency, is not included in this
repository or npm package, and is not covered by this project's MIT License.
mpv is GPL-2.0-or-later by
default and can be built as
LGPL-2.1-or-later when its GPL-only components are excluded. The effective
license also depends on the libraries linked into the particular libmpv build.
See [Third-Party Notices](THIRD_PARTY_NOTICES.md) before distributing libmpv
with an application.
