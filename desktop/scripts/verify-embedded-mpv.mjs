import { access } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { app } from 'electron';

const require = createRequire(import.meta.url);
const addonPath = path.resolve(import.meta.dirname, '../native/mpv/mpv_addon.node');

try {
  await access(addonPath);
  const addon = require(addonPath);
  if (typeof addon.MpvPlayer !== 'function') throw new Error('The native addon does not export MpvPlayer');
  console.log(`Embedded libmpv addon loaded under Electron ${process.versions.electron}.`);
  app.quit();
} catch (error) {
  console.error(error);
  app.exit(1);
}
