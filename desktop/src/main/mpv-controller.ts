import { spawn, type ChildProcess } from 'node:child_process';
import { access } from 'node:fs/promises';

export class MpvController {
  private process?: ChildProcess;
  private executable?: string;

  async status(): Promise<{ available: boolean; executable?: string; running: boolean }> {
    const executable = await this.resolveExecutable();
    return { available: executable !== undefined, executable, running: this.process !== undefined };
  }

  async play(url: string, title?: string): Promise<void> {
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Only HTTP media URLs are allowed');
    const executable = await this.resolveExecutable();
    if (!executable) throw new Error('mpv was not found. Install mpv or set WIZESTREAM_MPV_PATH.');
    await this.stop();
    const args = ['--force-window=yes', '--keep-open=yes', '--no-terminal'];
    if (title) args.push(`--force-media-title=${title.slice(0, 200)}`);
    args.push('--', parsed.toString());
    this.process = spawn(executable, args, { shell: false, stdio: 'ignore', windowsHide: false });
    this.process.once('exit', () => { this.process = undefined; });
  }

  async stop(): Promise<void> {
    this.process?.kill();
    this.process = undefined;
  }

  private async resolveExecutable(): Promise<string | undefined> {
    if (this.executable) return this.executable;
    const configured = process.env.WIZESTREAM_MPV_PATH;
    if (configured) {
      await access(configured);
      this.executable = configured;
      return configured;
    }
    const candidates = process.platform === 'win32' ? ['mpv.exe'] : ['mpv'];
    for (const candidate of candidates) {
      const available = await new Promise<boolean>((resolve) => {
        const child = spawn(candidate, ['--version'], { shell: false, stdio: 'ignore', windowsHide: true });
        child.once('error', () => resolve(false));
        child.once('exit', (code) => resolve(code === 0));
      });
      if (available) {
        this.executable = candidate;
        return candidate;
      }
    }
    return undefined;
  }
}
