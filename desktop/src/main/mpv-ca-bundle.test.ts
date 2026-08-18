import { afterEach, describe, expect, it } from 'vitest';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { ensureMpvCaBundle } from './mpv-ca-bundle.js';

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) =>
    rm(directory, { recursive: true, force: true })));
});

describe('ensureMpvCaBundle', () => {
  it('writes the trusted certificates used by embedded mpv', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'wizestream-mpv-ca-'));
    temporaryDirectories.push(directory);
    const certificate = '-----BEGIN CERTIFICATE-----\ntest-root\n-----END CERTIFICATE-----';

    const bundlePath = await ensureMpvCaBundle(directory, [certificate]);

    expect(bundlePath).toBe(path.join(directory, 'mpv-ca-bundle.pem'));
    expect(await readFile(bundlePath, 'utf8')).toBe(`${certificate}\n`);
  });

  it('rejects an empty certificate store', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'wizestream-mpv-ca-'));
    temporaryDirectories.push(directory);
    await expect(ensureMpvCaBundle(directory, [])).rejects.toThrow(
      'Electron did not provide any trusted root certificates',
    );
  });
});
