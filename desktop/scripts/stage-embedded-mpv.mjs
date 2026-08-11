import { copyFile, mkdir, readdir, rm } from 'node:fs/promises';
import path from 'node:path';

const desktopDirectory = path.resolve(import.meta.dirname, '..');
const source = path.join(desktopDirectory, 'node_modules/electron-mpv-video/native/mpv-addon/build/Release');
const destination = path.join(desktopDirectory, 'native/mpv');
await mkdir(destination, { recursive: true });

for (const entry of await readdir(destination)) {
  if (/\.(?:node|dll|dylib)$/i.test(entry)) await rm(path.join(destination, entry), { force: true });
}

let copied = 0;
for (const entry of await readdir(source, { withFileTypes: true })) {
  if (!entry.isFile() || !/\.(?:node|dll|dylib)$/i.test(entry.name)) continue;
  await copyFile(path.join(source, entry.name), path.join(destination, entry.name));
  copied += 1;
}
if (copied === 0) throw new Error('The embedded libmpv build produced no native runtime files');
console.log(`Staged ${copied} embedded libmpv runtime file(s).`);
