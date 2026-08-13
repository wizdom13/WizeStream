import { randomUUID } from 'node:crypto';
import { spawn, type ChildProcess } from 'node:child_process';
import { mkdir, open, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import type {
  DownloadComponent, DownloadJob, DownloadKind, DownloadRequest, DownloadSource, DownloadState,
} from '../shared/contracts.js';

interface StoredComponent extends DownloadComponent {
  source: DownloadSource;
  partialPath: string;
  etag?: string;
  lastModified?: string;
}

interface StoredDownload extends DownloadJob {
  sourceUrl: string;
  mimeType: string;
  finalPath: string;
  muxPath: string;
  components: StoredComponent[];
  metadataRecorded: boolean;
}

interface DownloadManagerOptions {
  ffmpegPath?: string;
  ffprobePath?: string;
  refreshSources?(sourceUrl: string): Promise<DownloadSource[]>;
}

type DownloadListener = (jobs: DownloadJob[]) => void;
type CompletedListener = (job: StoredDownload) => Promise<void>;

const EXTENSIONS: Record<string, string> = {
  MPEG_4: 'mp4', MP4: 'mp4', WEBM: 'webm', M4A: 'm4a', WEBMA: 'webm', WEBMA_OPUS: 'webm',
  MP3: 'mp3', OPUS: 'opus', OGG: 'ogg', VTT: 'vtt', SRT: 'srt', TTML: 'ttml',
};
const DEFAULT_EXTENSIONS: Record<DownloadKind, string> = { video: 'mp4', audio: 'm4a', caption: 'vtt' };
const DEFAULT_MIME_TYPES: Record<DownloadKind, string> = {
  video: 'video/mp4', audio: 'audio/mp4', caption: 'text/vtt',
};

export class DownloadManager {
  private readonly jobs = new Map<string, StoredDownload>();
  private readonly controllers = new Map<string, AbortController>();
  private readonly processes = new Map<string, ChildProcess>();
  private listener?: DownloadListener;
  private lastProgressNotification = 0;
  private persistence = Promise.resolve();

  constructor(
    private readonly destinationDirectory: string,
    private readonly stateFile: string,
    private readonly completed: CompletedListener,
    private readonly options: DownloadManagerOptions = {},
  ) {}

  async initialize(): Promise<void> {
    await mkdir(this.destinationDirectory, { recursive: true });
    try {
      const parsed = JSON.parse(await readFile(this.stateFile, 'utf8')) as unknown;
      const values = Array.isArray(parsed) ? parsed : storedJobs(parsed);
      for (const raw of values) {
        const value = migrateStoredDownload(raw);
        if (!value) continue;
        if (['downloading', 'queued', 'muxing', 'validating'].includes(value.state)) value.state = 'paused';
        this.jobs.set(value.id, value);
      }
      await this.persist();
      for (const value of this.jobs.values()) {
        if (value.state === 'completed' && value.metadataRecorded === false) {
          await this.recordCompletion(value);
        }
      }
      await this.persist();
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
  }

  setListener(listener: DownloadListener): void { this.listener = listener; this.notify(); }

  list(): DownloadJob[] {
    return [...this.jobs.values()].sort((a, b) => b.createdAt - a.createdAt).map(publicJob);
  }

  async start(input: DownloadRequest): Promise<DownloadJob> {
    httpUrl(input.sourceUrl);
    const title = safeTitle(input.title);
    const sources = normalizeSources(input);
    const kind = input.kind ?? (input.video ? 'video' : input.audio ? 'audio' : 'caption');
    const container = sources.length === 2 ? muxContainer(sources[0]!.source, sources[1]!.source)
      : extensionFor(sources[0]!.source.format, kind);
    const id = randomUUID();
    const fileName = `${safeFileName(title)}-${id.slice(0, 8)}.${container}`;
    const finalPath = path.join(this.destinationDirectory, fileName);
    const components: StoredComponent[] = sources.map(({ role, source }) => ({
      role, source, state: 'queued', bytesDownloaded: 0,
      partialPath: `${finalPath}.${role}.component`,
    }));
    const job: StoredDownload = {
      id, sourceUrl: input.sourceUrl, title, fileName, kind,
      mimeType: outputMimeType(container, kind), state: 'queued', stage: 'queued',
      bytesDownloaded: 0, createdAt: Date.now(), finalPath,
      muxPath: `${finalPath}.muxing.${container}`, outputContainer: container, components,
      metadataRecorded: false,
    };
    this.jobs.set(id, job);
    await this.changed();
    void this.run(job);
    return publicJob(job);
  }

  async pause(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (!['downloading', 'queued', 'muxing', 'validating'].includes(job.state)) return;
    job.state = 'paused'; job.stage = 'paused';
    this.controllers.get(id)?.abort(); this.processes.get(id)?.kill();
    await this.changed();
    await this.waitForStopped(id);
  }

  async resume(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (job.state !== 'paused' && job.state !== 'failed') throw new Error('Download cannot be resumed');
    job.error = undefined; job.state = 'queued'; job.stage = 'queued';
    await this.changed();
    void this.run(job);
  }

  async cancel(id: string): Promise<void> {
    const job = this.requireJob(id);
    if (job.state === 'completed') throw new Error('A completed download cannot be cancelled');
    job.state = 'cancelled'; job.stage = 'cancelled';
    this.controllers.get(id)?.abort(); this.processes.get(id)?.kill();
    await this.waitForStopped(id);
    await Promise.all([...job.components.map((value) => rm(value.partialPath, { force: true })),
      rm(job.muxPath, { force: true })]);
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
      for (const component of job.components) {
        if (component.state === 'completed') continue;
        job.state = 'downloading';
        job.stage = component.role === 'video' ? 'downloading_video'
          : component.role === 'audio' ? 'downloading_audio' : 'downloading_caption';
        component.state = 'downloading';
        await this.changed();
        await this.downloadComponent(job, component, controller, true);
        component.state = 'completed';
        await this.changed();
      }
      if (job.components.length === 2) await this.mux(job);
      else await rename(job.components[0]!.partialPath, job.finalPath);
      job.completedAt = Date.now();
      await this.recordCompletion(job);
      await Promise.all(job.components.map((value) => rm(value.partialPath, { force: true })));
      job.state = 'completed'; job.stage = 'completed';
      await this.changed();
    } catch (error) {
      if (job.state !== 'paused' && job.state !== 'cancelled') {
        job.state = 'failed'; job.stage = 'failed'; job.error = safeError(error);
        await this.changed();
      }
    } finally {
      this.controllers.delete(job.id); this.processes.delete(job.id);
    }
  }

  private async downloadComponent(
    job: StoredDownload, component: StoredComponent, controller: AbortController,
    allowRefresh: boolean, forceRestart = false,
  ): Promise<void> {
    const existing = forceRestart ? 0 : await fileSize(component.partialPath);
    if (!forceRestart && component.totalBytes !== undefined && existing === component.totalBytes) {
      component.bytesDownloaded = existing;
      job.bytesDownloaded = job.components.reduce((sum, item) => sum + item.bytesDownloaded, 0);
      job.totalBytes = knownTotal(job.components);
      return;
    }
    if (!forceRestart && component.totalBytes !== undefined && existing > component.totalBytes) {
      await rm(component.partialPath, { force: true });
      component.bytesDownloaded = 0;
      component.totalBytes = undefined;
      return this.downloadComponent(job, component, controller, allowRefresh, true);
    }
    const response = await fetch(httpUrl(component.source.url), {
      signal: controller.signal, headers: existing > 0 ? { Range: `bytes=${existing}-` } : undefined,
    });
    if ([401, 403, 410].includes(response.status) && allowRefresh && this.options.refreshSources) {
      component.source = await this.refreshSource(job.sourceUrl, component.source);
      await this.changed();
      return this.downloadComponent(job, component, controller, false, false);
    }
    if (!response.ok && response.status !== 206) throw new Error(`MEDIA_HTTP_${response.status}`);
    if (!response.body) throw new Error('MEDIA_EMPTY_RESPONSE');
    const append = existing > 0 && response.status === 206 && validContentRange(response, existing);
    const validatorChanged = append && ((component.etag && response.headers.get('etag')
      && component.etag !== response.headers.get('etag')) || (component.lastModified
      && response.headers.get('last-modified') && component.lastModified !== response.headers.get('last-modified')));
    if (validatorChanged || (existing > 0 && response.status === 206 && !append)) {
      await response.body.cancel(); await rm(component.partialPath, { force: true });
      component.bytesDownloaded = 0; component.totalBytes = undefined;
      return this.downloadComponent(job, component, controller, allowRefresh, true);
    }
    component.etag = response.headers.get('etag') ?? component.etag;
    component.lastModified = response.headers.get('last-modified') ?? component.lastModified;
    component.bytesDownloaded = append ? existing : 0;
    component.totalBytes = totalSize(response, component.bytesDownloaded);
    const file = await open(component.partialPath, append ? 'a' : 'w');
    const reader = response.body.getReader();
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (controller.signal.aborted) throw controller.signal.reason;
        await file.write(value); component.bytesDownloaded += value.byteLength;
        job.bytesDownloaded = job.components.reduce((sum, item) => sum + item.bytesDownloaded, 0);
        job.totalBytes = knownTotal(job.components);
        const now = Date.now();
        if (now - this.lastProgressNotification >= 200) { this.lastProgressNotification = now; this.notify(); }
      }
    } finally { await file.close(); }
  }

  private async refreshSource(sourceUrl: string, previous: DownloadSource): Promise<DownloadSource> {
    const candidates = await this.options.refreshSources?.(sourceUrl) ?? [];
    const matches = candidates.filter((value) => sameFingerprint(value, previous));
    if (matches.length !== 1) throw new Error('SOURCE_CHANGED');
    return { ...matches[0]!, url: httpUrl(matches[0]!.url).toString() };
  }

  private async mux(job: StoredDownload): Promise<void> {
    const ffmpeg = this.options.ffmpegPath; const ffprobe = this.options.ffprobePath;
    if (!ffmpeg || !ffprobe) throw new Error('MEDIA_TOOLS_UNAVAILABLE');
    const video = job.components.find((value) => value.role === 'video');
    const audio = job.components.find((value) => value.role === 'audio');
    if (!video || !audio) throw new Error('ADAPTIVE_COMPONENTS_MISSING');
    await rm(job.muxPath, { force: true });
    job.state = 'muxing'; job.stage = 'muxing'; await this.changed();
    await this.runProcess(job.id, ffmpeg, [
      '-y', '-nostdin', '-hide_banner', '-loglevel', 'error', '-i', video.partialPath, '-i', audio.partialPath,
      '-map', '0:v:0', '-map', '1:a:0', '-c', 'copy',
      ...(job.outputContainer === 'mp4' ? ['-movflags', '+faststart'] : []), job.muxPath,
    ], 'MUX_FAILED');
    job.state = 'validating'; job.stage = 'validating'; await this.changed();
    const output = await this.runProcess(job.id, ffprobe, [
      '-v', 'error', '-show_entries', 'stream=codec_type', '-of', 'json', job.muxPath,
    ], 'MUX_VALIDATION_FAILED', true);
    const streams = JSON.parse(output) as { streams?: Array<{ codec_type?: string }> };
    const kinds = new Set(streams.streams?.map((value) => value.codec_type));
    if (!kinds.has('video') || !kinds.has('audio')) throw new Error('MUX_OUTPUT_INCOMPLETE');
    await rename(job.muxPath, job.finalPath);
  }

  private async recordCompletion(job: StoredDownload): Promise<void> {
    try {
      await this.completed(job);
      job.metadataRecorded = true;
    } catch (error) {
      console.error('[downloads] Could not record synchronized metadata', safeError(error));
    }
  }

  private runProcess(id: string, executable: string, args: string[], code: string, capture = false): Promise<string> {
    return new Promise((resolve, reject) => {
      const child = spawn(executable, args, {
        shell: false, windowsHide: true, stdio: ['ignore', capture ? 'pipe' : 'ignore', 'pipe'],
      });
      this.processes.set(id, child); let stdout = ''; let stderr = '';
      child.stdout?.on('data', (value: Buffer) => { stdout += value.toString(); });
      child.stderr?.on('data', (value: Buffer) => { stderr += value.toString(); });
      child.once('error', reject);
      child.once('exit', (exitCode, signal) => {
        this.processes.delete(id);
        if (exitCode === 0) resolve(stdout);
        else reject(new Error(`${code}: ${(stderr || signal || exitCode || 'unknown').toString().slice(0, 400)}`));
      });
    });
  }

  private requireJob(id: string): StoredDownload {
    if (!/^[0-9a-f-]{36}$/i.test(id)) throw new Error('Invalid download id');
    const job = this.jobs.get(id); if (!job) throw new Error('Unknown download'); return job;
  }

  private async waitForStopped(id: string): Promise<void> {
    const deadline = Date.now() + 5_000;
    while ((this.controllers.has(id) || this.processes.has(id)) && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    if (this.controllers.has(id) || this.processes.has(id)) throw new Error('Timed out while stopping the download');
  }

  private async changed(): Promise<void> { await this.persist(); this.notify(); }
  private notify(): void { this.listener?.(this.list()); }

  private async persist(): Promise<void> {
    const snapshot = JSON.stringify({ schemaVersion: 2, jobs: [...this.jobs.values()] }, null, 2);
    const task = this.persistence.then(async () => {
      await mkdir(path.dirname(this.stateFile), { recursive: true });
      const temporary = `${this.stateFile}.tmp`; await writeFile(temporary, snapshot, 'utf8');
      await rename(temporary, this.stateFile);
    });
    this.persistence = task.catch(() => undefined); await task;
  }
}

function normalizeSources(input: DownloadRequest): Array<{ role: DownloadKind; source: DownloadSource }> {
  if (input.video) {
    if (!input.audio) return [{ role: 'video', source: validSource(input.video, 'video') }];
    return [{ role: 'video', source: validSource(input.video, 'video') },
      { role: 'audio', source: validSource(input.audio, 'audio') }];
  }
  if (input.audio) return [{ role: 'audio', source: validSource(input.audio, 'audio') }];
  if (input.caption) return [{ role: 'caption', source: validSource(input.caption, 'caption') }];
  if (!input.url || !input.kind) throw new Error('Invalid download source');
  return [{ role: input.kind, source: validSource({ url: input.url, format: input.format, mimeType: input.mimeType }, input.kind) }];
}

function validSource(value: DownloadSource, kind: DownloadKind): DownloadSource {
  return { ...value, url: httpUrl(value.url).toString(), kind, mimeType: safeMimeType(value.mimeType, kind) };
}

function muxContainer(video: DownloadSource, audio: DownloadSource): string {
  const vf = video.format?.toUpperCase(); const af = audio.format?.toUpperCase();
  if (['MPEG_4', 'MP4'].includes(vf ?? '') && ['M4A', 'MPEG_4', 'MP4'].includes(af ?? '')) return 'mp4';
  if (vf === 'WEBM' && ['WEBM', 'WEBMA', 'WEBMA_OPUS', 'OPUS', 'OGG'].includes(af ?? '')) return 'webm';
  return 'mkv';
}

function publicJob(value: StoredDownload): DownloadJob {
  return {
    id: value.id, title: value.title, fileName: value.fileName, kind: value.kind,
    state: value.state, stage: value.stage, bytesDownloaded: value.bytesDownloaded,
    totalBytes: value.totalBytes, createdAt: value.createdAt, completedAt: value.completedAt,
    error: value.error, outputContainer: value.outputContainer,
    components: value.components.map(({ role, state, bytesDownloaded, totalBytes }) => ({ role, state, bytesDownloaded, totalBytes })),
  };
}

function httpUrl(value: string): URL {
  if (typeof value !== 'string' || value.length > 16_384) throw new Error('Invalid media URL');
  const parsed = new URL(value);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Only HTTP media URLs are allowed');
  return parsed;
}

function safeTitle(value: string): string {
  const title = value?.trim(); if (!title || title.length > 200) throw new Error('Invalid download title'); return title;
}

function safeFileName(value: string): string {
  const normalized = value.normalize('NFKC').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
    .replace(/[. ]+$/g, '').replace(/\s+/g, ' ').slice(0, 120).trim();
  return normalized || 'WizeStream download';
}

function extensionFor(format: string | undefined, kind: DownloadKind): string {
  return (format ? EXTENSIONS[format.toUpperCase()] : undefined) ?? DEFAULT_EXTENSIONS[kind];
}

function safeMimeType(value: string | undefined, kind: DownloadKind): string {
  const mimeType = value?.trim() || DEFAULT_MIME_TYPES[kind];
  if (!/^[\w.+-]+\/[\w.+-]+$/.test(mimeType) || mimeType.length > 100) throw new Error('Invalid MIME type');
  return mimeType;
}

function outputMimeType(container: string, kind: DownloadKind): string {
  if (kind !== 'video') return DEFAULT_MIME_TYPES[kind];
  return container === 'webm' ? 'video/webm' : container === 'mkv' ? 'video/x-matroska' : 'video/mp4';
}

function totalSize(response: Response, offset: number): number | undefined {
  const range = response.headers.get('content-range')?.match(/\/(\d+)$/);
  if (range?.[1]) return Number(range[1]);
  const length = Number(response.headers.get('content-length'));
  return Number.isFinite(length) && length >= 0 ? offset + length : undefined;
}

function validContentRange(response: Response, offset: number): boolean {
  return response.headers.get('content-range')?.startsWith(`bytes ${offset}-`) === true;
}

function knownTotal(components: StoredComponent[]): number | undefined {
  return components.every((value) => value.totalBytes !== undefined)
    ? components.reduce((sum, value) => sum + (value.totalBytes ?? 0), 0) : undefined;
}

async function fileSize(filePath: string): Promise<number> {
  try { return (await stat(filePath)).size; }
  catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return 0; throw error; }
}

function sameFingerprint(left: DownloadSource, right: DownloadSource): boolean {
  const fields: Array<keyof DownloadSource> = ['id', 'kind', 'format', 'deliveryMethod', 'resolution', 'codec', 'audioTrackId', 'videoOnly'];
  return fields.every((field) => (left[field] ?? '') === (right[field] ?? ''));
}

function safeError(error: unknown): string {
  if (error instanceof Error && error.name === 'AbortError') return 'Download paused';
  const value = error instanceof Error ? error.message : String(error);
  return value.replace(/https?:\/\/\S+/gi, '[media URL hidden]').slice(0, 500);
}

function storedJobs(value: unknown): unknown[] {
  if (!value || typeof value !== 'object') return [];
  const state = value as { schemaVersion?: unknown; jobs?: unknown };
  return state.schemaVersion === 2 && Array.isArray(state.jobs) ? state.jobs : [];
}

function migrateStoredDownload(value: unknown): StoredDownload | undefined {
  if (!value || typeof value !== 'object') return undefined;
  const job = value as Partial<StoredDownload> & { url?: string; partialPath?: string };
  if (typeof job.id !== 'string' || typeof job.title !== 'string' || typeof job.fileName !== 'string'
    || typeof job.finalPath !== 'string' || typeof job.createdAt !== 'number'
    || !['video', 'audio', 'caption'].includes(job.kind ?? '')) return undefined;
  if (Array.isArray(job.components) && typeof job.muxPath === 'string') {
    return { ...(job as StoredDownload), metadataRecorded: job.metadataRecorded ?? true };
  }
  if (!job.url || !job.partialPath || !job.sourceUrl) return undefined;
  const kind = job.kind as DownloadKind;
  const source: DownloadSource = { url: job.url, mimeType: job.mimeType, kind };
  return {
    ...(job as StoredDownload), stage: legacyStage(job.state), outputContainer: path.extname(job.finalPath).slice(1),
    metadataRecorded: true, muxPath: `${job.finalPath}.muxing.${path.extname(job.finalPath).slice(1)}`, components: [{ role: kind, source, state: 'paused',
      bytesDownloaded: job.bytesDownloaded ?? 0, totalBytes: job.totalBytes, partialPath: job.partialPath }],
  };
}

function legacyStage(state: DownloadState | undefined): DownloadJob['stage'] {
  if (state === 'completed' || state === 'failed' || state === 'cancelled' || state === 'paused'
    || state === 'queued' || state === 'muxing' || state === 'validating') return state;
  return state === 'downloading' ? 'paused' : 'paused';
}
