import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type { DownloadJob, DownloadKind, DownloadRequest } from '../shared/contracts.js';

interface StoredDownload extends DownloadJob {
  url: string;
  sourceUrl: string;
  mimeType: string;
  finalPath: string;
  partialPath: string;
}

type DownloadListener = (jobs: DownloadJob[]) => void;
type CompletedListener = (job: StoredDownload) => Promise<void>;

const EXTENSIONS: Record<string, string> = {
  MPEG_4: 'mp4', WEBM: 'webm', M4A: 'm4a', WEBMA: 'webm', WEBMA_OPUS: 'webm',
  MP3: 'mp3', OPUS: 'opus', OGG: 'ogg', VTT: 'vtt', SRT: 'srt', TTML: 'ttml',
};

const DEFAULT_EXTENSIONS: Record<DownloadKind, string> = {
  video: 'mp4', audio: 'm4a', caption: 'vtt',
};

const DEFAULT_MIME_TYPES: Record<DownloadKind, string> = {
  video: 'video/mp4', audio: 'audio/mp4', caption: 'text/vtt',
};

export class DownloadManager {
  private readonly jobs = new Map<string, StoredDownload>();
  private readonly controllers = new Map<string, AbortController>();
  private listener?: DownloadListener;
  private lastProgressNotification = 0;
  private persistence = Promise.resolve();

  constructor(
    private readonly destinationDirectory: string,
    private readonly stateFile: string,
    private readonly completed: CompletedListener,
  ) {}

  async initialize(): Promise<void> {
    await mkdir(this.destinationDirectory, { recursive: true });
    try {
      const values = JSON.parse(await readFile(this.stateFile, 'utf8')) as StoredDownload[];
      for (const value of values) {
        if (!isStoredDownload(value)) continue;
        if (value.state === 'downloading' || value.state === 'queued') value.state = 'paused';
        this.jobs.set(value.id, value);
      }
      await this.persist();
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
  }

  setListener(listener: DownloadListener): void {
    this.listener = listener;
    this.notify();
  }

  list(): DownloadJob[] {
    return [...this.jobs.values()]
      .sort((left, right) => right.createdAt - left.createdAt)
      .map(publicJob);
  }

  async start(input: DownloadRequest): Promise<DownloadJob> {
    const parsed = httpUrl(input.url);
    const sourceUrl = httpUrl(input.sourceUrl);
    const title = safeTitle(input.title);
    if (!['video', 'audio', 'caption'].includes(input.kind)) throw new Error('Invalid download kind');
    const extension = extensionFor(input.format, input.kind);
    const id = randomUUID();
    const fileName = `${safeFileName(title)}-${id.slice(0, 8)}.${extension}`;
    const finalPath = path.join(this.destinationDirectory, fileName);
    const job: StoredDownload = {
      id,
      url: parsed.toString(),
      sourceUrl: sourceUrl.toString(),
      title,
      fileName,
      kind: input.kind,
      mimeType: safeMimeType(input.mimeType, input.kind),
      state: 'queued',
      bytesDownloaded: 0,
      createdAt: Date.now(),
      finalPath,
      partialPath: `${finalPath}.part`,
    };
    this.jobs.set(id, job);
    await this.changed();
    void this.run(job);
    return publicJob(job);
  }

  async pause(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (job.state !== 'downloading' && job.state !== 'queued') return;
    job.state = 'paused';
    this.controllers.get(id)?.abort();
    await this.changed();
    await this.waitForStopped(id);
  }

  async resume(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (job.state !== 'paused' && job.state !== 'failed') throw new Error('Download cannot be resumed');
    job.error = undefined;
    job.state = 'queued';
    await this.changed();
    void this.run(job);
  }

  async cancel(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (job.state === 'completed') throw new Error('A completed download cannot be cancelled');
    job.state = 'cancelled';
    this.controllers.get(id)?.abort();
    await this.waitForStopped(id);
    await rm(job.partialPath, { force: true });
    await this.changed();
  }

  completedPath(id: string): string {
    const job = this.requireJob(id);
    if (job.state !== 'completed') throw new Error('Download is not complete');
    return job.finalPath;
  }

  private async run(job: StoredDownload): Promise<void> {
    if (this.controllers.has(job.id)) return;
    const controller = new AbortController();
    this.controllers.set(job.id, controller);
    try {
      const existing = await fileSize(job.partialPath);
      const response = await fetch(job.url, {
        signal: controller.signal,
        headers: existing > 0 ? { Range: `bytes=${existing}-` } : undefined,
      });
      if (!response.ok && response.status !== 206) throw new Error(`Download server returned HTTP ${response.status}`);
      if (!response.body) throw new Error('Download server returned an empty response');

      const append = existing > 0 && response.status === 206;
      job.bytesDownloaded = append ? existing : 0;
      job.totalBytes = totalSize(response, job.bytesDownloaded);
      job.state = 'downloading';
      await this.changed();

      const file = await open(job.partialPath, append ? 'a' : 'w');
      const reader = response.body.getReader();
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (controller.signal.aborted) throw controller.signal.reason;
          await file.write(value);
          job.bytesDownloaded += value.byteLength;
          const now = Date.now();
          if (now - this.lastProgressNotification >= 200) {
            this.lastProgressNotification = now;
            this.notify();
          }
        }
      } finally {
        await file.close();
      }
      await rename(job.partialPath, job.finalPath);
      job.completedAt = Date.now();
      job.totalBytes = job.bytesDownloaded;
      await this.completed(job).catch((error: unknown) => {
        console.error('[downloads] Could not record synchronized metadata', error);
      });
      job.state = 'completed';
      await this.changed();
    } catch (error) {
      if (job.state !== 'paused' && job.state !== 'cancelled') {
        job.state = 'failed';
        job.error = safeError(error);
        await this.changed();
      }
    } finally {
      this.controllers.delete(job.id);
    }
  }

  private requireJob(id: string): StoredDownload {
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error('Invalid download id');
    const job = this.jobs.get(id);
    if (!job) throw new Error('Unknown download');
    return job;
  }

  private async waitForStopped(id: string): Promise<void> {
    const deadline = Date.now() + 5_000;
    while (this.controllers.has(id) && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    if (this.controllers.has(id)) throw new Error('Timed out while stopping the download');
  }

  private async changed(): Promise<void> {
    await this.persist();
    this.notify();
  }

  private notify(): void {
    this.listener?.(this.list());
  }

  private async persist(): Promise<void> {
    const snapshot = JSON.stringify([...this.jobs.values()], null, 2);
    const task = this.persistence.then(async () => {
      await mkdir(path.dirname(this.stateFile), { recursive: true });
      const temporary = `${this.stateFile}.tmp`;
      await writeFile(temporary, snapshot, 'utf8');
      await rename(temporary, this.stateFile);
    });
    this.persistence = task.catch(() => undefined);
    await task;
  }
}

function publicJob(value: StoredDownload): DownloadJob {
  return {
    id: value.id,
    title: value.title,
    fileName: value.fileName,
    kind: value.kind,
    state: value.state,
    bytesDownloaded: value.bytesDownloaded,
    totalBytes: value.totalBytes,
    createdAt: value.createdAt,
    completedAt: value.completedAt,
    error: value.error,
  };
}

function httpUrl(value: string): URL {
  if (typeof value !== 'string' || value.length > 16_384) throw new Error('Invalid download URL');
  const parsed = new URL(value);
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('Only HTTP downloads are allowed');
  return parsed;
}

function safeTitle(value: string): string {
  const title = value?.trim();
  if (!title || title.length > 200) throw new Error('Invalid download title');
  return title;
}

function safeFileName(value: string): string {
  const normalized = value.normalize('NFKC').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    .replace(/[. ]+$/g, '').replace(/\s+/g, ' ').slice(0, 120).trim();
  return normalized || 'WizeStream download';
}

function extensionFor(format: string | undefined, kind: DownloadKind): string {
  const extension = format ? EXTENSIONS[format.toUpperCase()] : undefined;
  return extension ?? DEFAULT_EXTENSIONS[kind];
}

function safeMimeType(value: string | undefined, kind: DownloadKind): string {
  const mimeType = value?.trim() || DEFAULT_MIME_TYPES[kind];
  if (!/^[\w.+-]+\/[\w.+-]+$/.test(mimeType) || mimeType.length > 100) throw new Error('Invalid MIME type');
  return mimeType;
}

function totalSize(response: Response, offset: number): number | undefined {
  const range = response.headers.get('content-range')?.match(/\/(\d+)$/);
  if (range?.[1]) return Number(range[1]);
  const length = Number(response.headers.get('content-length'));
  return Number.isFinite(length) && length >= 0 ? offset + length : undefined;
}

async function fileSize(filePath: string): Promise<number> {
  try { return (await stat(filePath)).size; }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return 0; throw error; }
}

function safeError(error: unknown): string {
  if (error instanceof Error && error.name === 'AbortError') return 'Download paused';
  const value = error instanceof Error ? error.message : String(error);
  return value.slice(0, 500);
}

function isStoredDownload(value: unknown): value is StoredDownload {
  if (!value || typeof value !== 'object') return false;
  const job = value as Partial<StoredDownload>;
  return typeof job.id === 'string' && typeof job.url === 'string' && typeof job.sourceUrl === 'string'
    && typeof job.title === 'string'
    && typeof job.fileName === 'string' && typeof job.finalPath === 'string'
    && typeof job.partialPath === 'string' && typeof job.createdAt === 'number'
    && typeof job.bytesDownloaded === 'number'
    && ['video', 'audio', 'caption'].includes(job.kind ?? '')
    && ['queued', 'downloading', 'paused', 'completed', 'failed', 'cancelled'].includes(job.state ?? '');
}
