import { access } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { constants as osConstants } from 'node:os';
import path from 'node:path';
import { app } from 'electron';

const require = createRequire(import.meta.url);
const addonPath = path.resolve(import.meta.dirname, '../native/mpv/mpv_addon.node');

try {
  await access(addonPath);
  const addon = loadNativeAddon(addonPath);
  if (typeof addon.MpvPlayer !== 'function') throw new Error('The native addon does not export MpvPlayer');
  console.log(`Embedded libmpv addon loaded under Electron ${process.versions.electron}.`);
  app.exit(0);
} catch (error) {
  console.error(error);
  app.exit(1);
}

function loadNativeAddon(value) {
  if (process.platform !== 'linux') return require(value);
  const nativeModule = { exports: {} };
  process.dlopen(nativeModule, value, osConstants.dlopen.RTLD_NOW | osConstants.dlopen.RTLD_DEEPBIND);
  return nativeModule.exports;
}
