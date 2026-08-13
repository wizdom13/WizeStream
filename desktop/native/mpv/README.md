# Embedded libmpv runtime

Each native packaging job stages the Electron-ABI-matched addon and its relocated libmpv dependency
closure into this directory. Generated `.node`, `.dll`, `.dylib` and `.so*` files are intentionally
not committed. `manifest.json` records the target, Electron version and fork baseline in artifacts.
