import type { AppUpdater, ProgressInfo, UpdateInfo } from 'electron-updater';
import type { UpdateState } from '../shared/contracts.js';

type StateListener = (state: UpdateState) => void;

export class UpdateManager {
  private stateValue: UpdateState;
  private initialized = false;

  constructor(
    private readonly updater: AppUpdater | undefined,
    currentVersion: string,
    private readonly listener: StateListener,
  ) {
    this.stateValue = {
      status: updater ? 'idle' : 'unavailable',
      currentVersion,
      channel: 'beta',
      ...(!updater ? { message: 'Updates are available in installed release builds.' } : {}),
    };
  }

  initialize(): void {
    if (!this.updater || this.initialized) return;
    this.initialized = true;
    this.updater.autoDownload = false;
    this.updater.autoInstallOnAppQuit = false;
    this.updater.allowPrerelease = true;
    this.updater.channel = 'beta';
    this.updater.allowDowngrade = false;
    this.updater.disableWebInstaller = true;

    this.updater.on('checking-for-update', () => this.update({ status: 'checking', message: undefined }));
    this.updater.on('update-available', (info) => this.available(info));
    this.updater.on('update-not-available', () => this.update({
      status: 'up-to-date', checkedAt: Date.now(), version: undefined, percent: undefined,
      message: 'WizeStream Desktop is up to date.',
    }));
    this.updater.on('download-progress', (progress) => this.progress(progress));
    this.updater.on('update-downloaded', (info) => this.update({
      status: 'downloaded', version: info.version, percent: 100,
      message: 'The signed update is ready to install.',
    }));
    this.updater.on('error', (error) => this.update({
      status: 'error', percent: undefined, message: safeError(error),
    }));
  }

  state(): UpdateState {
    return { ...this.stateValue };
  }

  async check(): Promise<UpdateState> {
    if (!this.updater || ['checking', 'downloading'].includes(this.stateValue.status)) return this.state();
    this.update({ status: 'checking', message: undefined, percent: undefined });
    try {
      await this.updater.checkForUpdates();
    } catch (error) {
      this.update({ status: 'error', message: safeError(error), percent: undefined });
    }
    return this.state();
  }

  async download(): Promise<UpdateState> {
    if (!this.updater || this.stateValue.status !== 'available') return this.state();
    this.update({ status: 'downloading', percent: 0, message: 'Downloading the signed update…' });
    try {
      await this.updater.downloadUpdate();
    } catch (error) {
      this.update({ status: 'error', message: safeError(error), percent: undefined });
    }
    return this.state();
  }

  install(): void {
    if (!this.updater || this.stateValue.status !== 'downloaded') return;
    this.updater.quitAndInstall(false, true);
  }

  private available(info: UpdateInfo): void {
    this.update({
      status: 'available', checkedAt: Date.now(), version: info.version,
      releaseName: typeof info.releaseName === 'string' ? info.releaseName : undefined,
      releaseNotes: releaseNotes(info.releaseNotes), percent: undefined,
      message: 'A signed update is available. Download it when you are ready.',
    });
  }

  private progress(progress: ProgressInfo): void {
    this.update({
      status: 'downloading', percent: Math.max(0, Math.min(100, progress.percent)),
      message: `Downloading the signed update… ${Math.round(progress.percent)}%`,
    });
  }

  private update(patch: Partial<UpdateState>): void {
    this.stateValue = { ...this.stateValue, ...patch };
    this.listener(this.state());
  }
}

function releaseNotes(value: UpdateInfo['releaseNotes']): string | undefined {
  if (typeof value === 'string') return value.slice(0, 4_000);
  if (!Array.isArray(value)) return undefined;
  return value.map((note) => `${note.version}: ${note.note ?? ''}`).join('\n').slice(0, 4_000);
}

function safeError(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/https?:\/\/[^\s]+/g, '[update server]').slice(0, 500);
}
