import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFile, lstat, mkdir, readdir, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import path from 'node:path';

const execute = promisify(execFile);
const desktopDirectory = path.resolve(import.meta.dirname, '..');
const source = path.join(desktopDirectory, 'node_modules/electron-mpv-video/native/mpv-addon/build/Release');
const destination = path.join(desktopDirectory, 'native/mpv');
await mkdir(destination, { recursive: true });
for (const entry of await readdir(destination)) {
  if (/\.(?:node|dll|dylib)$|\.so(?:\.\d+)*$/i.test(entry)) await rm(path.join(destination, entry), { force: true });
}

const staged = new Map();
for (const entry of await readdir(source, { withFileTypes: true })) {
  if ((!entry.isFile() && !entry.isSymbolicLink())
    || !(/\.(?:node|dll|dylib)$|\.so(?:\.\d+)*$/i.test(entry.name))) continue;
  await stage(path.join(source, entry.name), entry.name);
}
if (![...staged.keys()].some((value) => value.endsWith('.node'))) {
  throw new Error('The embedded libmpv build produced no native addon');
}

if (process.platform === 'linux') await stageLinuxClosure();
if (process.platform === 'darwin') await stageMacClosure();

const manifest = {
  target: `${process.platform}-${process.arch}`,
  electron: JSON.parse(await readFile(path.join(desktopDirectory, 'package.json'), 'utf8')).devDependencies.electron,
  upstream: 'electron-mpv-video@0.1.1',
  fork: '0.1.1-wizestream.1',
  libmpv: process.platform === 'win32'
    ? { source: 'Shinchiro mpv-winbuild-cmake 20260811 f4d13e1c2c', license: 'GPL-2.0-or-later/LGPL-2.1-or-later' }
    : process.platform === 'darwin'
      ? { source: 'Homebrew mpv 0.41.0', license: 'GPL-2.0-or-later/LGPL-2.1-or-later' }
      : { source: 'Ubuntu 24.04 libmpv-dev 0.37.0-1ubuntu4', license: 'GPL-2.0-or-later/LGPL-2.1-or-later' },
  files: await Promise.all([...staged.entries()].sort(([left], [right]) => left.localeCompare(right))
    .map(async ([name, value]) => ({ name, sha256: createHash('sha256').update(await readFile(value.output)).digest('hex') }))),
};
await writeFile(path.join(destination, 'manifest.json'), JSON.stringify(manifest, null, 2));
console.log(`Staged ${staged.size} embedded libmpv runtime file(s) for ${manifest.target}.`);

async function stage(file, name = path.basename(file)) {
  const resolved = await realpath(file).catch(() => file);
  const output = path.join(destination, name);
  await copyFile(resolved, output);
  staged.set(name, { source: resolved, output });
  return output;
}

async function stageLinuxClosure() {
  const queue = [...staged.values()];
  const ignored = /^(?:linux-vdso|ld-linux|libc\.so|libm\.so|libdl\.so|libpthread\.so|librt\.so|libgcc_s\.so)/;
  for (let index = 0; index < queue.length; index += 1) {
    const current = queue[index];
    const { stdout } = await execute('ldd', [current.output]);
    for (const line of stdout.split('\n')) {
      const match = line.match(/=>\s+(\/[^\s]+)\s+\(/) ?? line.match(/^\s*(\/[^\s]+)\s+\(/);
      if (!match) continue;
      const dependency = match[1];
      const name = path.basename(dependency);
      if (ignored.test(name) || staged.has(name)) continue;
      const output = await stage(dependency, name);
      queue.push({ source: dependency, output });
    }
  }
  for (const value of staged.values()) {
    await execute('patchelf', ['--set-rpath', '$ORIGIN', value.output]);
  }
}

async function stageMacClosure() {
  const roots = [process.env.MPV_RUNTIME_DIR, process.env.MPV_DEPENDENCY_ROOTS]
    .filter(Boolean).flatMap((value) => value.split(path.delimiter));
  const queue = [...staged.values()];
  for (let index = 0; index < queue.length; index += 1) {
    const current = queue[index];
    const { stdout } = await execute('otool', ['-L', current.source]);
    for (const line of stdout.split('\n').slice(1)) {
      const reference = line.trim().split(' ')[0];
      if (!reference || reference.startsWith('/System/') || reference.startsWith('/usr/lib/')) continue;
      const dependency = await resolveMacDependency(reference, current.source, roots);
      const name = path.basename(dependency);
      let output = staged.get(name)?.output;
      if (!output) {
        output = await stage(dependency, name);
        queue.push({ source: dependency, output });
      }
      await execute('install_name_tool', ['-change', reference, `@loader_path/${name}`, current.output]);
    }
    if (current.output.endsWith('.dylib')) {
      await execute('install_name_tool', ['-id', `@loader_path/${path.basename(current.output)}`, current.output]);
    }
  }
  for (const value of staged.values()) await execute('codesign', ['--force', '--sign', '-', value.output]);
}

async function resolveMacDependency(reference, owner, roots) {
  if (reference.startsWith('/')) return reference;
  const suffix = reference.replace(/^@(?:rpath|loader_path)\//, '');
  const candidates = [path.join(path.dirname(owner), suffix), ...roots.map((root) => path.join(root, suffix))];
  for (const candidate of candidates) {
    try { if ((await lstat(candidate)).isFile() || (await lstat(candidate)).isSymbolicLink()) return candidate; }
    catch { /* try the next configured root */ }
  }
  throw new Error(`Could not resolve macOS dependency ${reference} for ${owner}`);
}
