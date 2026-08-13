import { createServer } from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, test } from 'vitest';
import { DownloadManager } from './download-manager.js';

const temporaryDirectories: string[] = [];
const execute = promisify(execFile);

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

  test('downloads adaptive components and atomically publishes validated video and audio', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-adaptive-'));
    temporaryDirectories.push(directory);
    const videoPath = path.join(directory, 'video.mp4');
    const audioPath = path.join(directory, 'audio.m4a');
    await execute('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'color=c=blue:s=160x90:d=1',
      '-an', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', videoPath]);
    await execute('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
      '-vn', '-c:a', 'aac', audioPath]);
    const video = await readFile(videoPath);
    const audio = await readFile(audioPath);
    const server = createServer((request, response) => {
      const content = request.url === '/audio' ? audio : video;
      response.writeHead(200, { 'connection': 'close', 'content-length': content.length });
      response.end(content);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server address');
    const completed: string[] = [];
    const manager = new DownloadManager(path.join(directory, 'downloads'), path.join(directory, 'downloads.json'),
      async (job) => { completed.push(job.fileName); }, { ffmpegPath: 'ffmpeg', ffprobePath: 'ffprobe' });
    await manager.initialize();
    const started = await manager.start({
      sourceUrl: 'https://www.youtube.com/watch?v=adaptivefixture', title: 'Adaptive fixture',
      video: { url: `http://127.0.0.1:${address.port}/video`, id: 'v1', format: 'MPEG_4', kind: 'video' },
      audio: { url: `http://127.0.0.1:${address.port}/audio`, id: 'a1', format: 'M4A', kind: 'audio' },
    });
    const result = await waitFor(() => manager.list().find((job) => job.id === started.id), 'completed');
    expect(result.outputContainer).toBe('mp4');
    expect(result.components.map((value) => value.state)).toEqual(['completed', 'completed']);
    const { stdout } = await execute('ffprobe', ['-v', 'error', '-show_entries', 'stream=codec_type',
      '-of', 'csv=p=0', manager.completedPath(started.id)]);
    expect(stdout).toContain('video');
    expect(stdout).toContain('audio');
    expect(completed).toHaveLength(1);
    await closeServer(server);
  }, 20_000);

  test('migrates a legacy Phase 4 job without deleting its partial file', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-legacy-'));
    temporaryDirectories.push(directory);
    const partialPath = path.join(directory, 'legacy.mp4.part');
    await writeFile(partialPath, Buffer.from('partial'));
    await writeFile(path.join(directory, 'downloads.json'), JSON.stringify([{
      id: '11111111-1111-4111-8111-111111111111', url: 'https://example.com/video',
      sourceUrl: 'https://example.com/watch', title: 'Legacy', fileName: 'legacy.mp4', kind: 'video',
      mimeType: 'video/mp4', state: 'downloading', bytesDownloaded: 7, createdAt: 1,
      finalPath: path.join(directory, 'legacy.mp4'), partialPath,
    }]));
    const manager = new DownloadManager(directory, path.join(directory, 'downloads.json'), async () => undefined);
    await manager.initialize();
    const job = manager.list()[0];
    expect(job?.state).toBe('paused');
    expect(job?.components).toHaveLength(1);
    expect(await readFile(partialPath)).toEqual(Buffer.from('partial'));
    const persisted = JSON.parse(await readFile(path.join(directory, 'downloads.json'), 'utf8')) as { schemaVersion: number };
    expect(persisted.schemaVersion).toBe(2);
  });

  test('refuses an ambiguous expired-source refresh without exposing signed URLs', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-refresh-'));
    temporaryDirectories.push(directory);
    const server = createServer((_request, response) => response.writeHead(403).end());
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server address');
    const signed = `http://127.0.0.1:${address.port}/video?secret=never-log-this`;
    const manager = new DownloadManager(directory, path.join(directory, 'downloads.json'), async () => undefined, {
      refreshSources: async () => [
        { url: 'https://media.example/one?token=one', id: 'video-1', kind: 'video', format: 'MPEG_4' },
        { url: 'https://media.example/two?token=two', id: 'video-1', kind: 'video', format: 'MPEG_4' },
      ],
    });
    await manager.initialize();
    const started = await manager.start({ sourceUrl: 'https://www.youtube.com/watch?v=expiredfixture',
      title: 'Expired fixture', url: signed, kind: 'video', format: 'MPEG_4' });
    const result = await waitFor(() => manager.list().find((job) => job.id === started.id), 'failed');
    expect(result.error).toBe('SOURCE_CHANGED');
    expect(JSON.stringify(result)).not.toContain('never-log-this');
    expect(JSON.stringify(result)).not.toContain('token=');
    await closeServer(server);
  });

  test('refreshes one unambiguous expired component without changing its fingerprint', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'wizestream-refresh-success-'));
    temporaryDirectories.push(directory);
    const content = Buffer.from('refreshed media');
    const server = createServer((request, response) => {
      if (request.url === '/expired') { response.writeHead(403).end(); return; }
      response.writeHead(200, { 'connection': 'close', 'content-length': content.length });
      response.end(content);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server address');
    const manager = new DownloadManager(directory, path.join(directory, 'downloads.json'), async () => undefined, {
      refreshSources: async () => [{ url: `http://127.0.0.1:${address.port}/fresh`, id: 'audio-1',
        kind: 'audio', format: 'M4A', deliveryMethod: 'PROGRESSIVE_HTTP' }],
    });
    await manager.initialize();
    const started = await manager.start({ sourceUrl: 'https://www.youtube.com/watch?v=refreshfixture',
      title: 'Refresh fixture', audio: { url: `http://127.0.0.1:${address.port}/expired`, id: 'audio-1',
        kind: 'audio', format: 'M4A', deliveryMethod: 'PROGRESSIVE_HTTP' } });
    await waitFor(() => manager.list().find((job) => job.id === started.id), 'completed');
    expect(await readFile(manager.completedPath(started.id))).toEqual(content);
    await closeServer(server);
  });
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
