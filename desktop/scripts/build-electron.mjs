import { rm } from 'node:fs/promises';
import path from 'node:path';
import { build } from 'esbuild';

const desktopDirectory = path.resolve(import.meta.dirname, '..');
const outputDirectory = path.join(desktopDirectory, 'dist-electron');
await rm(outputDirectory, { recursive: true, force: true });

const common = {
  bundle: true,
  platform: 'node',
  target: 'node24',
  external: ['electron', 'electron-updater'],
  sourcemap: false,
};

await build({
  ...common,
  entryPoints: [path.join(desktopDirectory, 'src/main/main.ts')],
  format: 'esm',
  outfile: path.join(outputDirectory, 'main/main.js'),
});

await build({
  ...common,
  entryPoints: [path.join(desktopDirectory, 'src/preload/index.cts')],
  format: 'cjs',
  outfile: path.join(outputDirectory, 'preload/index.cjs'),
});
