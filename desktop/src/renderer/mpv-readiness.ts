interface MpvLifecycleEvent {
  type?: string;
  data?: unknown;
  level?: string;
  reason?: string;
  error?: string;
}

export interface MpvReadinessWait {
  promise: Promise<void>;
  cancel(): void;
}

export function waitForMpvMediaReady(target: EventTarget, timeoutMillis = 30_000): MpvReadinessWait {
  let settled = false;
  let newMediaStarted = false;
  let lastLoadDiagnostic: string | undefined;
  let lastLoadDiagnosticPriority = 0;
  let resolvePromise!: () => void;
  let rejectPromise!: (reason: Error) => void;
  const promise = new Promise<void>((resolve, reject) => {
    resolvePromise = resolve;
    rejectPromise = reject;
  });

  const cleanup = () => {
    clearTimeout(timeout);
    target.removeEventListener('mpv-event', onLifecycleEvent);
    target.removeEventListener('mpv-error', onPlayerError);
  };
  const finish = (error?: Error) => {
    if (settled) return;
    settled = true;
    cleanup();
    if (error) rejectPromise(error);
    else resolvePromise();
  };
  const onLifecycleEvent = (event: Event) => {
    const detail = (event as CustomEvent<MpvLifecycleEvent>).detail ?? {};
    if (detail.type === 'log-message' && typeof detail.data === 'string'
      && (detail.level === 'error' || detail.level === 'fatal' || detail.level === 'warn')) {
      const diagnostic = detail.data.trim().replace(/\s+/g, ' ').slice(0, 300);
      const priority = /HTTP error\s+\d{3}/i.test(diagnostic) ? 3
        : /failed to open/i.test(diagnostic) ? 2
          : /subprocess|youtube-dl/i.test(diagnostic) ? 0 : 1;
      if (diagnostic && priority >= lastLoadDiagnosticPriority) {
        lastLoadDiagnostic = diagnostic;
        lastLoadDiagnosticPriority = priority;
      }
    } else if (detail.type === 'start-file') {
      newMediaStarted = true;
    } else if (detail.type === 'file-loaded' && detail.error) {
      finish(new Error(`Media failed to load: ${detail.error}`));
    } else if (detail.type === 'file-loaded') {
      finish();
    } else if (detail.type === 'end-file' && newMediaStarted
      && (detail.reason === 'error' || detail.error)) {
      finish(new Error(detail.error
        ? `Media failed to load: ${detail.error}${lastLoadDiagnostic ? ` (${lastLoadDiagnostic})` : ''}`
        : `Media failed to load${lastLoadDiagnostic ? `: ${lastLoadDiagnostic}` : ''}`));
    } else if (detail.error && newMediaStarted) {
      finish(new Error(`Media failed to load: ${detail.error}`));
    }
  };
  const onPlayerError = (event: Event) => {
    finish(new Error(String((event as CustomEvent<unknown>).detail ?? 'Unknown player error')));
  };
  const timeout = setTimeout(() => finish(new Error('Timed out while loading media')), timeoutMillis);

  target.addEventListener('mpv-event', onLifecycleEvent);
  target.addEventListener('mpv-error', onPlayerError);
  return { promise, cancel: () => finish() };
}
