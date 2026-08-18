import { describe, expect, it, vi } from 'vitest';
import { waitForMpvMediaReady } from './mpv-readiness';

function mpvEvent(type: string, error?: string, reason?: string) {
  const event = new Event('mpv-event') as CustomEvent<{ type: string; error?: string; reason?: string }>;
  Object.defineProperty(event, 'detail', { value: { type, error, reason } });
  return event;
}

function mpvLog(data: string, level = 'error') {
  const event = new Event('mpv-event') as CustomEvent<{ type: string; data: string; level: string }>;
  Object.defineProperty(event, 'detail', { value: { type: 'log-message', data, level } });
  return event;
}

describe('mpv media readiness', () => {
  it('waits for the native file-loaded event', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    let finished = false;
    void wait.promise.then(() => { finished = true; });
    await Promise.resolve();
    expect(finished).toBe(false);
    target.dispatchEvent(mpvEvent('file-loaded'));
    await expect(wait.promise).resolves.toBeUndefined();
  });

  it('reports native loading and external-track failures', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('file-loaded', 'audio-add failed'));
    await expect(wait.promise).rejects.toThrow('audio-add failed');
  });

  it('ignores the previous source ending before the replacement starts', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('end-file', 'old source stopped'));
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvEvent('file-loaded'));
    await expect(wait.promise).resolves.toBeUndefined();
  });

  it('ignores a non-error end event that arrives after the replacement starts', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvEvent('end-file', undefined, 'stop'));
    target.dispatchEvent(mpvEvent('file-loaded'));
    await expect(wait.promise).resolves.toBeUndefined();
  });

  it('ignores an unclassified end event from an older native addon', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvEvent('end-file'));
    target.dispatchEvent(mpvEvent('file-loaded'));
    await expect(wait.promise).resolves.toBeUndefined();
  });

  it('reports a replacement source that ends with an mpv load error', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvEvent('end-file', 'network error', 'error'));
    await expect(wait.promise).rejects.toThrow('network error');
  });

  it('includes the sanitized native network diagnostic in a load error', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvLog('HTTP error 403 Forbidden'));
    target.dispatchEvent(mpvEvent('end-file', 'loading failed', 'error'));
    await expect(wait.promise).rejects.toThrow('loading failed (HTTP error 403 Forbidden)');
  });

  it('keeps an HTTP failure instead of a later youtube-dl fallback warning', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvLog('HTTP error 403'));
    target.dispatchEvent(mpvLog('Subprocess failed: init'));
    target.dispatchEvent(mpvEvent('end-file', 'loading failed', 'error'));
    await expect(wait.promise).rejects.toThrow('loading failed (HTTP error 403)');
  });

  it('keeps a TLS failure instead of the generic failed-to-open message', async () => {
    const target = new EventTarget();
    const wait = waitForMpvMediaReady(target, 1_000);
    target.dispatchEvent(mpvEvent('start-file'));
    target.dispatchEvent(mpvLog('tls: certificate verify failed'));
    target.dispatchEvent(mpvLog('Failed to open https://example.test/media'));
    target.dispatchEvent(mpvEvent('end-file', 'loading failed', 'error'));
    await expect(wait.promise).rejects.toThrow('loading failed (tls: certificate verify failed)');
  });

  it('times out instead of leaving a silent black player', async () => {
    vi.useFakeTimers();
    const wait = waitForMpvMediaReady(new EventTarget(), 250);
    const assertion = expect(wait.promise).rejects.toThrow('Timed out while loading media');
    await vi.advanceTimersByTimeAsync(250);
    await assertion;
    vi.useRealTimers();
  });

  it('can be cancelled during a source change', async () => {
    const wait = waitForMpvMediaReady(new EventTarget(), 1_000);
    wait.cancel();
    await expect(wait.promise).resolves.toBeUndefined();
  });
});
