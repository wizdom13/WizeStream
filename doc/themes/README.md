# Creating WizeStream theme packs

This guide describes **theme format version 1**, introduced by the theme-pack
foundation. It requires a build containing that feature; releases predating it
cannot import packs. The format version is independent of the app version.

## What version 1 can do

A pack is a small UTF-8 JSON file combining WizeStream's existing, app-maintained
appearance options. It can choose a fixed light, dark, AMOLED black, or Eye
Comfort appearance, or follow the system, and select different accent palettes
for day and night. Built-in Forest, Ocean, and Midnight packs use exactly this
format and the same parser as third-party packs.

This is a **palette-recipe format**, not an arbitrary skinning engine. Version 1
does **not** accept custom hex/RGB colours, fonts, corner radii, images, icon packs,
animation, transparency/blur, HTML, CSS, scripts, URLs, executable code, resource
paths, or custom layouts. Supported palette tokens select complete built-in
colour roles rather than replacing one isolated colour. Existing accessibility,
focus, layout, and player behaviour remain controlled by the app.

Glass effects and vinyl/iPod/Walkman player layouts need separately implemented
app features. Adding fields for them to a version 1 pack will cause rejection;
this guide does not promise their availability or a delivery date.

## Quick start

1. Copy [Ocean](../../app/src/main/assets/themes/ocean.json),
   [Forest](../../app/src/main/assets/themes/forest.json), or
   [Midnight](../../app/src/main/assets/themes/midnight.json).
2. Change `name` and `author`, then choose the supported options below.
3. Save as, for example, `my-ocean-theme.json`, in UTF-8 without a byte-order mark.
   Do not ZIP the file or rename an APK as JSON.
4. In WizeStream open **Settings → Appearance → Theme packs → Import theme pack**.
5. Choose the file in Android's document picker. Inspect the preview and choose
   **Apply theme**. Cancel or Back leaves the current theme unchanged.
6. Test both day and night modes and the screens in the checklist below.
7. Share the JSON file with the theme name, author, format version, and screenshots.

You can also choose **Built-in theme packs**, or use **Export current theme** as a
starting point. Exporting regular appearance settings uses the placeholder name
`My WizeStream theme` and author `Theme creator`; replace these before sharing.
Exporting an active pack retains its metadata. Exports contain only the theme
recipe, not history, accounts, subscriptions, device information, or other settings.

## Complete example

```json
{
  "format": "wizestream-theme",
  "version": 1,
  "name": "Ocean",
  "author": "Your display name",
  "mode": "system",
  "night": "dark",
  "lightAccent": "blue",
  "darkAccent": "cyan"
}
```

All field names and enum values are **case-sensitive**. Use exactly the spellings
shown here, regardless of the app's display language. Field ordering and whitespace
do not matter. Strings must use JSON double quotes.

## Field reference

| Field | Required | Type | Default when omitted | Meaning |
| --- | --- | --- | --- | --- |
| `format` | Yes | String | None | Must be `wizestream-theme`. |
| `version` | Yes | Integer | None | Must be the JSON number `1`, not `"1"` or `1.0`. |
| `name` | Yes | String | None | Display name: 1–80 UTF-16 code units, not whitespace-only. |
| `author` | Yes | String | None | Creator attribution: same limits as `name`. This is a label, not verified identity. |
| `mode` | No | String | `system` | `system`, `light`, `dark`, `black`, or `eye_comfort`. |
| `night` | No | String | `dark` | `dark` or `black`; used only when `mode` is `system` and the device is in dark mode. |
| `lightAccent` | No | String | `wizestream` | Accent palette used in light mode. |
| `darkAccent` | No | String | `wizestream` | Accent palette used in dark/black mode. Ignored by Eye Comfort. |

Optional means **omitted**, not `null`, an empty string, or an unknown value.
Name and author reject control characters (including escaped newlines/tabs) and
bidirectional embedding/override/isolate controls. Emoji can count as more than
one UTF-16 code unit. Ordinary Unicode names are supported; plain text is displayed
without interpreting markup. Keep attribution short and recognizable.

### Accent tokens

Both accent fields accept these same tokens:

| Token | Palette |
| --- | --- |
| `wizestream` | WizeStream's branded green palette |
| `neutral` | Neutral palette |
| `green` | Green |
| `blue` | Blue |
| `cyan` | Cyan |
| `purple` | Purple |
| `yellow` | Yellow |
| `orange` | Orange |
| `pink` | Pink |
| `red` | Red |
| `follow_system` | Material dynamic colours where supported; existing app fallback elsewhere |

Tokens select the existing theme's primary, secondary, container, and corresponding
foreground roles. They are not promises of one exact RGB value across modes or
future app updates. Surface colours come from the chosen base mode. For reproducible
screenshots, choose a named palette instead of `follow_system`: dynamic colours
depend on Android/device support and the user's system configuration. Do not assume
that a phone's dynamic palette will also appear on a TV or older Android device.

### Mode examples

**Always light:** `"mode": "light"` uses `lightAccent`; `night` and `darkAccent`
are retained in export but have no effect while that mode is active.

**Always dark:** `"mode": "dark"` uses `darkAccent`, independently of device mode.

**Always AMOLED black:** `"mode": "black"` uses black surfaces and `darkAccent`.

**Follow system with AMOLED at night:** `"mode": "system", "night": "black"`
uses the light appearance in device light mode and black surfaces in dark mode.

**Eye Comfort:** `"mode": "eye_comfort"` uses the app's complete Eye Comfort
palette. Both accent fields and `night` are ignored visually, but still must contain
valid values if supplied. Omitting them is simplest.

### Minimal pack

```json
{
  "format": "wizestream-theme",
  "version": 1,
  "name": "My theme",
  "author": "My name"
}
```

This follows system mode, uses dark surfaces at night, and selects the branded
WizeStream accent in both modes. It does not automatically inherit the user's
previous accent choice.

## Validation and failure behaviour

The importer reads at most **16 KiB (16,384 bytes)** of UTF-8 theme data plus one
byte to detect overflow. The limit includes whitespace and metadata. It rejects:

- Invalid UTF-8, malformed JSON, comments, trailing commas, trailing content,
  arrays instead of the root object, or nested objects/arrays as values.
- Duplicate keys, unknown fields, wrong types, missing required fields, unsupported
  format/version, unsupported mode/palette values, and invalid metadata.
- Files above the byte limit and inaccessible or unreadable documents.

There is no downloading of referenced assets and no evaluation of imported code.
Future fields are rejected rather than silently ignored. Invalid packs do not
replace the current theme. If invalid theme data is restored from an app backup,
the app falls back to regular appearance settings so Appearance remains accessible.

A machine-readable [JSON Schema](theme-pack.schema.json) is provided for editor
assistance. The app's runtime validation is authoritative: generic JSON Schema
validators may accept numeric `1.0`, cannot catch duplicate keys after parsing, and
do not enforce the encoded-file byte limit. Keep the literal integer `1`, unique
keys, and the size limit even if your editor reports no errors.

## Preview, application, and restoration

The preview shows sample surface/text and accent-container colours for the pack's
applicable modes. System-mode packs show both day and night; fixed-mode packs show
one sample. It is a colour sample, **not a screenshot of every app screen**. The
preview dialog's surrounding controls use the current theme so Cancel stays familiar.

Applying persists a single active pack and recreates the settings screen. Main
navigation picks up the theme on returning; previously open player screens may
need reopening to update every control. System-mode packs follow normal system
night-mode changes. Theme-aware player colours use the selected pack too.

While a pack is active, the regular Theme, Night theme, and Theme colour selectors
are disabled with an explanation. Other appearance options remain independent.
Applying another pack replaces the active pack; there is no installed-pack library
in version 1. Keep the JSON files you want to switch between.

**Restore previous appearance** removes the pack and reveals the underlying regular
appearance settings, which applying a pack does not overwrite. This is not a factory
reset and does not erase other preferences or user data. If those underlying settings
were changed by backup restore or device sync while a pack was active, their current
values will be used. The same action works when stored pack data is invalid.

Packs are app-wide, not per-profile. The active recipe is stored with app preferences
and can be included in app settings backups. It is not added to the device-sync
allowlist in this version; share the exported JSON and import it on another device.

## Creator testing checklist

Before publishing a pack:

- Import the actual shared file, check its name/author, and confirm that Cancel/Back
  does not change the current appearance.
- Apply it and check the home feed, subscriptions, search, video details, settings,
  dialogs, selected navigation items, mini-player, and player seek bar.
- For system mode, switch device light/dark settings and verify both variants.
- Check text and selected/focused controls against their backgrounds. Test larger
  text settings and Android TV D-pad focus if you can access a TV device.
- If using `follow_system`, test a supported dynamic-colour device and a device
  without dynamic colours. Accept the documented fallback or choose a fixed palette.
- Reopen existing player screens, restart the app, and verify persistence.
- Export the applied pack, import that export, and verify equivalent appearance.
- Restore previous appearance and confirm the earlier settings return.
- Test on the oldest Android version you claim to support. Do not claim device
  coverage based only on the preview.

Do not try to work around focus or text readability with unsupported fields. Report
an app palette problem with the JSON file, app version, Android version, screenshot,
and exact affected screen. The app maintains the palettes; a pack cannot repair an
underlying component that ignores its theme.

## Sharing and compatibility policy

Share a plain `.json` attachment or a GitHub file/release download, accompanied by
screenshots of each applicable mode. Users import files manually; version 1 has no
gallery, subscription, automatic update, remote asset loader, or theme marketplace.
Only claim authorship of your own work and use a display name you are comfortable
sharing. Attribution is unverified and must not imply official endorsement.

State that the recipient needs **WizeStream with theme-pack format v1 support**.
Older apps do not gain support just because a file uses `version: 1`. Do not guess a
minimum app release number before the feature is released. Format extensions will
be documented and versioned; do not increment `version` yourself to enable features.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| File cannot be selected | Save with `.json`; use a document provider that exposes JSON, plain text, or binary documents. |
| Import fails | Check strict JSON syntax, field names/types, supported values, UTF-8 encoding, 16 KiB limit, and file access. |
| Hex colour rejected | Version 1 supports palette tokens only. |
| Night accent seems unused | Use `system` with device dark mode, `dark`, or `black`; light mode uses `lightAccent`. |
| Eye Comfort ignores accent | Intentional: Eye Comfort uses its complete built-in palette. |
| Dynamic colour differs on another device | Expected; use a named palette for more consistent results. |
| Existing player has old colours | Close and reopen that player screen; restart the app if necessary. |
| Theme selectors are disabled | Restore previous appearance in Theme packs before editing regular selectors. |
| Export fails | Select a writable document provider/location and retry; a failed provider write can leave a partial file. |
| Want blur, custom images, shapes, or a vinyl layout | Not supported by v1; these require future app features. |
