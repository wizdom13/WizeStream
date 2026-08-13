# Third-Party Notices

## mpv / libmpv

`electron-mpv-video` interfaces with and dynamically links to libmpv. libmpv
is not included in this repository or npm package, and it is not covered by
this project's MIT License.

mpv is licensed under GPL-2.0-or-later by default. It can be built under
LGPL-2.1-or-later when GPL-only files are excluded, commonly by configuring
mpv with `-Dgpl=false`. That option alone does not guarantee that the resulting
build is LGPL-compatible: linked libraries, including the particular FFmpeg
build, can affect the final license.

Before distributing an application that bundles libmpv or a native addon
linked against it, verify the license of the exact libmpv build and all linked
dependencies, then comply with their license and notice requirements. In
particular, distributing a GPL build of libmpv may require the combined work
to be distributed under GPL-compatible terms.

Official mpv licensing information:

- https://github.com/mpv-player/mpv/blob/master/Copyright
- https://github.com/mpv-player/mpv/blob/master/LICENSE.GPL
- https://github.com/mpv-player/mpv/blob/master/LICENSE.LGPL
