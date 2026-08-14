import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process';
import { access } from 'node:fs/promises';
import path from 'node:path';
import readline from 'node:readline';
import { app } from 'electron';
import type { BackendMethod } from '../shared/contracts.js';

interface PendingRequest {
  resolve(value: unknown): void;
  reject(error: Error): void;
  timeout: NodeJS.Timeout;
}

interface RpcResponse {
  id?: number;
  result?: unknown;
  error?: { message?: string };
}

export class BackendClient {
  private process?: ChildProcessWithoutNullStreams;
  private nextId = 1;
  private readonly pending = new Map<number, PendingRequest>();

  async start(): Promise<void> {
    if (this.process) return;
    const resources = app.isPackaged ? process.resourcesPath : path.resolve(app.getAppPath(), 'backend/build');
    const runtime = path.join(resources, app.isPackaged ? 'runtime' : 'runtime');
    const libraries = path.join(resources, app.isPackaged ? 'backend/lib' : 'install/wizestream-desktop-backend/lib');
    const java = path.join(runtime, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
    await access(java);
    await access(libraries);

    this.process = spawn(
      java,
      [
        '-Dfile.encoding=UTF-8',
        '-cp',
        path.join(libraries, '*'),
        'org.wisso.wizestream.desktop.backend.DesktopBackend',
        '--data-dir',
        app.getPath('userData'),
      ],
      { shell: false, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true },
    );

    readline.createInterface({ input: this.process.stdout }).on('line', (line) => this.onLine(line));
    this.process.stderr.on('data', (value: Buffer) => console.error(`[backend] ${value.toString().trimEnd()}`));
    this.process.once('exit', (code, signal) => {
      this.process = undefined;
      const error = new Error(`WizeStream backend exited (${code ?? signal ?? 'unknown'})`);
      for (const pending of this.pending.values()) {
        clearTimeout(pending.timeout);
        pending.reject(error);
      }
      this.pending.clear();
    });
    await this.invoke('health');
  }

  async stop(): Promise<void> {
    const child = this.process;
    this.process = undefined;
    child?.kill();
  }

  async invoke<T>(method: BackendMethod, params: Record<string, unknown> = {}): Promise<T> {
    if (!this.process) throw new Error('WizeStream backend is not running');
    const id = this.nextId++;
    const request = JSON.stringify({ jsonrpc: '2.0', id, method, params });
    return new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Backend request timed out: ${method}`));
      }, method === 'sync.run' ? 10 * 60_000
        : method.startsWith('backup.') || method.startsWith('subscriptions.') ? 2 * 60_000 : 45_000);
      this.pending.set(id, { resolve: resolve as (value: unknown) => void, reject, timeout });
      this.process?.stdin.write(`${request}\n`, (error) => {
        if (error) {
          clearTimeout(timeout);
          this.pending.delete(id);
          reject(error);
        }
      });
    });
  }

  private onLine(line: string): void {
    let response: RpcResponse;
    try {
      response = JSON.parse(line) as RpcResponse;
    } catch {
      console.error('[backend] Ignored malformed response');
      return;
    }
    if (typeof response.id !== 'number') return;
    const pending = this.pending.get(response.id);
    if (!pending) return;
    clearTimeout(pending.timeout);
    this.pending.delete(response.id);
    if (response.error) pending.reject(new Error(response.error.message ?? 'Backend request failed'));
    else pending.resolve(response.result);
  }
}
