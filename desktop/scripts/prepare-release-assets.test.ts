import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, expect, test } from 'vitest';
import { dump, load } from 'js-yaml';

const execute = promisify(execFile);
const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

test('merges macOS architectures and emits checksums for a complete release', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'wizestream-release-'));
  temporaryDirectories.push(root);
  const input = path.join(root, 'input');
  const output = path.join(root, 'output');
  for (const directory of ['win', 'mac-x64', 'mac-arm64', 'linux-x64', 'linux-arm64']) {
    await mkdir(path.join(input, directory), { recursive: true });
  }

  await asset(input, 'win', 'WizeStream Desktop-0.6.0-beta.1-windows-x64.exe');
  await metadata(input, 'win', 'beta.yml', 'WizeStream Desktop-0.6.0-beta.1-windows-x64.exe');
  for (const arch of ['x64', 'arm64']) {
    await asset(input, `mac-${arch}`, `WizeStream Desktop-0.6.0-beta.1-macos-${arch}.dmg`);
    await asset(input, `mac-${arch}`, `WizeStream Desktop-0.6.0-beta.1-macos-${arch}.zip`);
    await metadata(input, `mac-${arch}`, 'beta-mac.yml', `WizeStream Desktop-0.6.0-beta.1-macos-${arch}.zip`);
    await asset(input, `linux-${arch}`, `WizeStream Desktop-0.6.0-beta.1-linux-${arch}.AppImage`);
    await metadata(input, `linux-${arch}`, arch === 'x64' ? 'beta-linux.yml' : 'beta-linux-arm64.yml',
      `WizeStream Desktop-0.6.0-beta.1-linux-${arch}.AppImage`);
  }

  const script = path.resolve(import.meta.dirname, 'prepare-release-assets.mjs');
  await execute(process.execPath, [script, '--input', input, '--output', output]);

  const mac = load(await readFile(path.join(output, 'beta-mac.yml'), 'utf8')) as { files: Array<{ url: string }> };
  expect(mac.files.map((file) => file.url)).toEqual([
    'WizeStream Desktop-0.6.0-beta.1-macos-arm64.zip',
    'WizeStream Desktop-0.6.0-beta.1-macos-x64.zip',
  ]);
  expect(await readFile(path.join(output, 'SHA256SUMS'), 'utf8')).toContain('beta-mac.yml');
  expect(JSON.parse(await readFile(path.join(output, 'release-manifest.json'), 'utf8')).prerelease).toBe(true);
});

async function asset(root: string, directory: string, name: string): Promise<void> {
  await writeFile(path.join(root, directory, name), `fixture:${name}`);
}

async function metadata(root: string, directory: string, name: string, url: string): Promise<void> {
  await writeFile(path.join(root, directory, name), dump({
    version: '0.6.0-beta.1', files: [{ url, sha512: `sha512:${url}`, size: 42 }],
    path: url, sha512: `sha512:${url}`, releaseDate: '2026-08-14T00:00:00.000Z',
  }));
}
