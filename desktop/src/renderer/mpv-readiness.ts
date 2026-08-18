interface MpvLifecycleEvent {
  type?: string;
  error?: string;
}

export interface MpvReadinessWait {
  promise: Promise<void>;
  cancel(): void;
}

export function waitForMpvMediaReady(target: EventTarget, timeoutMillis = 30_000): MpvReadinessWait {
  let settled = false;
  let newMediaStarted = false;
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
    if (detail.type === 'start-file') {
      newMediaStarted = true;
    } else if (detail.type === 'file-loaded' && detail.error) {
      finish(new Error(`Media failed to load: ${detail.error}`));
    } else if (detail.type === 'file-loaded') {
      finish();
    } else if (detail.type === 'end-file' && newMediaStarted) {
      finish(new Error(detail.error
        ? `Media failed to load: ${detail.error}`
        : 'Media ended before it finished loading'));
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
