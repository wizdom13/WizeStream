# Example service icon

`service-icon.svg` is the editable source for `../src/main/resources/icon.png`.
The PNG is a **256 × 256**, single-color image on a transparent background. WizeStream
tints service icons to match its light and dark themes; keep the background transparent.

The SVG is original example artwork from WizeStream contributors, licensed under GPL-3.0-or-later.

Render the checked-in PNG after changing the vector:

```sh
inkscape artwork/service-icon.svg --export-type=png --export-width=256 --export-height=256 --export-filename=src/main/resources/icon.png
```

The build packages the existing PNG; it does not require Inkscape. The manifest's
`"icon": "icon.png"` refers to the path inside the signed `.ppp` archive.
`pluginPackage` checks that the declared raster is present, decodes successfully, is at
most **256 KiB**, and has dimensions no larger than **512 × 512**.
