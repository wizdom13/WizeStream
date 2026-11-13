Community theme packs are data files for supported WizeStream appearance options. They are not executable plugins and cannot add arbitrary Android behavior, scripts or remotely loaded artwork.

## Import with a preview

1. Open **Settings → Appearance → Theme packs → Import theme pack**.
2. Choose a supported `.json` file.
3. Inspect the preview and compatibility information.
4. Choose **Apply theme** to make the change.
5. Recreate or reopen an existing player if its current view still uses the previous appearance.

Cancel or Back leaves the existing appearance unchanged. Export the current theme when you want to preserve it, and use **Restore previous appearance** when you want to undo the last applied pack.

## Versions and supported styling

| Pack version | Scope |
| --- | --- |
| Version 1 | Supported palette and appearance settings. |
| Version 2 | Additional bounded glass/navigation settings, native listening-player layouts and embedded PNG artwork/icons. |

Version 2 packs require WizeStream 1.35.0 or newer. Existing supported version 1 palettes remain compatible.

Vinyl, pocket and cassette listening designs are native layouts. Their presence is not a promise of an animated or mechanically interactive player. Theme files do not replace the underlying media player or its accessibility behavior.

## Glass has deliberate boundaries

Glass styling affects supported navigation surfaces rather than every card, dialog and settings page. Feed blur depends on the supported layout and runtime conditions.

Low-memory devices, an active player, or opaque-surface fallbacks can produce a more opaque result. A safe fallback is expected behavior, not evidence that every imported pack is invalid.

## Pack limits

The version 2 format is bounded to keep imports predictable:

- A maximum 512 KiB pack file.
- Up to six embedded PNG assets.
- Up to 96 KiB per asset.
- Image dimensions no larger than 1024 × 1024.
- A combined decoded pixel budget of 1,048,576 pixels.
- Player icons no larger than 256 × 256.

These are format-validation limits, not an invitation to download remote assets from a pack. Keep the entire pack self-contained and do not add URLs, scripts or custom fonts outside the documented format.

## Build an accessible theme

Test text contrast, native touch targets, focus indication, labels, captions and action visibility. Check light/dark backgrounds, a manual color preset, landscape and at least one smaller display. Decorative artwork should not obscure the action it accompanies.

Themes are included in supported appearance backups, but are not synchronized as ordinary profile settings. Transfer a pack intentionally when you want the same appearance on another device.

## Share and troubleshoot

Use the project’s theme creator guide and schema as the format reference. Include a compatible app version and screenshots when sharing a pack. If an import is rejected, first inspect the JSON version, file size, embedded PNG format and asset limits rather than weakening validation.

Keep licensing and attribution for artwork you distribute. Report a theme failure to WizeStream with the smallest reproducible pack and a description of the affected layout, avoiding private media or account details in screenshots.
