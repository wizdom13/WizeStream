PipePipe third-party binary compatibility fixture

Plugin: Twitch 1.2.1, version code 5, by Jonas Mohr
Package source:
https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/twitch-5.ppp
Package SHA-256:
3b48b2fd3c773c36f1f3ab7ae4e18af0eb92cd3975f004ba074120b1e4f4228a
Package size: 239727 bytes

Source revision for this release:
https://github.com/MohrJonas/TwitchPipePipePlugin/tree/9204d76a1d45271b9887fef2ef0561ce07819a84
Catalog signature and key were recorded from:
https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/index.json

The third-party package has an upstream "MIT NON-AI License", with additional
restrictions; do not describe it as standard MIT or as a WizeStream dependency.
Neither the package nor its implementation is committed to WizeStream or bundled
in the application. scripts/prepare-plugin-test-fixtures.py downloads the exact
published bytes and complete upstream notice into app/build/generated/plugin-test-assets.
The notice's SHA-256 is:
08c1c6ac745870031ef39014c9b3cc20b841444e24341c289b9a5932191e936c

Only instrumentation test APKs include these generated files. Do not publish those
test APKs or fixtures as release artifacts. Run the preparation script before
building compatibility tests. Instrumentation performs no internet access: all
extractor requests are answered by deterministic synthetic test responses.

This is an unmodified published package: compatibility tests must not repair or
replace its DEX. The publisher repository provides version 1.2.1, including its
Android 7 URL-encoding fix. The community catalog still listed older version 1.2.0
when this fixture was recorded; the publisher signing key is unchanged.

Native WizeStream API 2.0 compatibility fixture

Plugin: PornHub 1.0.5, version code 6, by WizeStream
Package source:
https://github.com/wizdom13/PornHub-WizeStream-Plugin/releases/download/repo/pornhub-wizestream-6.ppp
Package SHA-256:
b7be7de4cf12ce09e01ecdc66a593463129fec78249e1e524841b3bc017d4470
Package size: 141797 bytes
Source revision:
https://github.com/wizdom13/PornHub-WizeStream-Plugin/tree/cc182d0a451fb60024625b0aa068816a9303e89d
License: GPL-3.0-or-later; the preparation script includes the source LICENSE.
The catalog signature/key were recorded from the publisher's repo/index.json.

This package is included only in generated instrumentation test assets, never in
the production application. Its original signed DEX exercises real native
interface defaults, shared Kotlin/jsoup/nanojson dependencies, date/time APIs and
async downloader callbacks against the minified host. The checked-in HTML is
synthetic non-explicit metadata and uses reserved example.com media URLs. The
provider deliberately rejects .invalid hostnames, so those are not valid fixtures.
All extraction requests are answered offline; the test never fetches or renders
provider content. Package signatures are verified without rewriting the bytes.

Native WizeStream API 2.0 undated subscription-feed fixture

Plugin: XVideos 1.0.1, version code 2, by WizeStream
Package source:
https://github.com/wizdom13/XVideos-WizeStream-Plugin/releases/download/repo/xvideos-wizestream-2.ppp
Package SHA-256:
c3678c64116a165b50f930775287e69427da4dd880edf05754f9cc932d22e9eb
Package size: 164016 bytes
Source revision:
https://github.com/wizdom13/XVideos-WizeStream-Plugin/tree/6e43c2855fc0ab80122fcaa8ab3669c8c421288d
License: GPL-3.0-or-later; the preparation script includes the source LICENSE.
The catalog signature/key were recorded from the publisher's repo/index.json.

The PornHub and XVideos feed fixtures contain neutral channel listings without
publication dates. Tests load the original signed packages, extract their real
channel tabs, and persist the results through the host's feed database. Both
remain test-only generated assets; no provider page or media is requested.
