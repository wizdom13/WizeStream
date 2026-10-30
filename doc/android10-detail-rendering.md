# Android 10 detail-panel rendering workaround

Issue #1032 reports disappearing video details on a Motorola One Macro running Android 10.
The reporter confirmed persistence on v1.35.0 after the original text-only software layers.
The supplied recording also shows action icons disappearing while the channel image and
content below the panel remain visible, so the symptom is not restricted to text glyphs.

The follow-up rasterizes `detailContentRootLayout` as one software layer on Android 10.
This groups the title, channel labels, and action controls in the same bounded panel.
The player container and scrolling pager remain outside that layer. A defensive check
rejects any root containing a SurfaceView or TextureView to avoid rasterizing video output.
Visibility, opacity, selection, accessibility, and event handling are not rewritten.
Other Android versions retain their existing rendering path.

Tests cover layer scope, repeated application, unchanged child state, video-surface guards,
and the API 28/30 exclusions. They cannot reproduce the Motorola GPU/compositor behavior.
The exact device-specific cause remains unconfirmed: this is a targeted compatibility
workaround and #1032 must remain open until the reporter verifies a build containing it.
