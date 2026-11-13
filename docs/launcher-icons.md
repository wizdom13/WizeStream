# App icon selection

Open **Settings → Appearance → App icon** and choose Default, Blue, Green, or Purple.
The built-in choices retain the WizeStream mark and include matching Android TV banners.
The default restores the original icon and TV artwork. This is a manual palette selection;
it does not automatically track imported themes or system wallpaper colors.

Phone launchers use adaptive icons on supported Android versions and a vector fallback
on older devices. Monochrome themed icons remain available: a launcher using wallpaper
colors can override the chosen icon color. TV launchers can show the matching banner.
Launcher caches may delay the update or require removing and re-adding a pinned shortcut.

Only bundled icons are supported; theme packs cannot install arbitrary launcher artwork.
The selection is device-wide, stored as Android component state rather than in a profile,
and is not included in Device Sync or preference backups. Android normally preserves
component state over app updates. The actual MainActivity stays enabled for links,
notifications, and explicit launches; only launcher aliases are switched.

The new alias is enabled before old aliases are disabled. A failed switch attempts to
restore the previous states and reports the error. The app does not deliberately kill
its process, although launcher refresh behavior varies by device.
