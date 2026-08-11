import { createServer } from 'node:http';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test } from 'vitest';
import { DownloadManager } from './download-manager.js';

const temporaryDirectories: string[] = [];

afterEach(async () => {
  await Promise.all(temporaryDirectories.splice(0).map((directory) => rm(directory, { recursive: true, force: true })));
});

describe('DownloadManager', () => {
  test('downloads into the fixed directory and records completion', async () => {
    const content = Buffer.from('WizeStream Phase 4 download');
    const server = createServer((_request, response) => {
      response.writeHead(200, {
        'connection': 'close',
        'content-length': content.length,
        'content-type': 'video/mp4',
      });
      response.end(content);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server address');
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-download-'));
    temporaryDirectories.push(directory);
    const completed: Array<{ id: string; sourceUrl: string }> = [];
    const manager = new DownloadManager(
      path.join(directory, 'downloads'),
      path.join(directory, 'state', 'downloads.json'),
      async (job) => { completed.push({ id: job.id, sourceUrl: job.sourceUrl }); },
    );
    await manager.initialize();

    const started = await manager.start({
      url: `http://127.0.0.1:${address.port}/video`,
      sourceUrl: 'https://www.youtube.com/watch?v=phase4fixture',
      title: '../../unsafe: title',
      format: 'MPEG_4',
      kind: 'video',
    });
    const result = await waitFor(() => manager.list().find((job) => job.id === started.id), 'completed');

    expect(result.state).toBe('completed');
    expect(result.fileName).not.toMatch(/[\\/:*?"<>|]/);
    expect(result.fileName.endsWith('.mp4')).toBe(true);
    expect(await readFile(manager.completedPath(started.id))).toEqual(content);
    expect(completed).toEqual([{ id: started.id, sourceUrl: 'https://www.youtube.com/watch?v=phase4fixture' }]);
    await closeServer(server);
  }, 10_000);

  test('pauses and resumes with an HTTP range request', async () => {
    const content = Buffer.alloc(2 * 1024 * 1024, 0x57);
    let rangeRequests = 0;
    const server = createServer((request, response) => {
      const range = request.headers.range?.match(/^bytes=(\d+)-$/);
      const offset = range ? Number(range[1]) : 0;
      if (offset > 0) rangeRequests += 1;
      response.writeHead(offset > 0 ? 206 : 200, {
        'connection': 'close',
        'content-length': content.length - offset,
        ...(offset > 0 ? { 'content-range': `bytes ${offset}-${content.length - 1}/${content.length}` } : {}),
      });
      let position = offset;
      const interval = setInterval(() => {
        if (position >= content.length) { clearInterval(interval); response.end(); return; }
        const end = Math.min(content.length, position + 8 * 1024);
        response.write(content.subarray(position, end));
        position = end;
      }, 5);
      response.on('close', () => clearInterval(interval));
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server address');
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-resume-'));
    temporaryDirectories.push(directory);
    const manager = new DownloadManager(
      path.join(directory, 'downloads'),
      path.join(directory, 'downloads.json'),
      async () => undefined,
    );
    await manager.initialize();
    const started = await manager.start({
      url: `http://127.0.0.1:${address.port}/large-video`,
      sourceUrl: 'https://www.youtube.com/watch?v=resumefixture',
      title: 'Resume fixture',
      format: 'MPEG_4',
      kind: 'video',
    });
    await waitForCondition(() => {
      const job = manager.list().find((value) => value.id === started.id);
      return job?.state === 'downloading' && job.bytesDownloaded > 256 * 1024
        && job.bytesDownloaded < content.length;
    });
    await manager.pause(started.id);
    expect(manager.list().find((job) => job.id === started.id)?.state).toBe('paused');
    await manager.resume(started.id);
    await waitFor(() => manager.list().find((job) => job.id === started.id), 'completed');

    expect(rangeRequests).toBeGreaterThan(0);
    expect(Buffer.compare(await readFile(manager.completedPath(started.id)), content)).toBe(0);
    await closeServer(server);
  }, 10_000);
});

async function waitFor(
  value: () => ReturnType<DownloadManager['list']>[number] | undefined,
  state: string,
): Promise<ReturnType<DownloadManager['list']>[number]> {
  const deadline = Date.now() + 5_000;
  while (Date.now() < deadline) {
    const current = value();
    if (current?.state === state) return current;
    if (current?.state === 'failed') throw new Error(current.error);
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`Timed out waiting for ${state}`);
}

async function waitForCondition(condition: () => boolean): Promise<void> {
  const deadline = Date.now() + 5_000;
  while (Date.now() < deadline) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  throw new Error('Timed out waiting for download progress');
}

function closeServer(server: ReturnType<typeof createServer>): void {
  server.close();
  server.unref();
}
