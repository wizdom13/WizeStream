import { afterEach, describe, expect, it } from 'vitest';
import {
  YoutubeMediaProxy,
  youtubeMediaProxyInternals,
} from '../../vendor/electron-mpv-video/dist/main/youtube-media-proxy.js';

const proxies: YoutubeMediaProxy[] = [];

afterEach(async () => {
  await Promise.all(proxies.splice(0).map((proxy) => proxy.close()));
});

describe('YoutubeMediaProxy', () => {
  it('rewrites only signed googlevideo playback URLs to loopback', async () => {
    const proxy = new YoutubeMediaProxy();
    proxies.push(proxy);
    const source = 'https://r1---sn.example.googlevideo.com/videoplayback?itag=137&c=ANDROID';

    const rewritten = await proxy.rewriteMediaRequest({
      source,
      userAgent: 'android-client',
      audio: { url: `${source}&itag=140`, userAgent: 'android-client' },
    });

    expect(rewritten.source).toMatch(/^http:\/\/127\.0\.0\.1:\d+\/[0-9a-f-]+$/);
    expect(rewritten.audio?.url).toMatch(/^http:\/\/127\.0\.0\.1:\d+\/[0-9a-f-]+$/);
    expect(await proxy.rewriteUrl('https://example.com/video.mp4', {}, 'header'))
      .toBe('https://example.com/video.mp4');
  });

  it('converts adaptive reads to Android range query parameters', () => {
    const prepared = youtubeMediaProxyInternals.prepareUpstreamRequest({
      source: 'https://r1---sn.example.googlevideo.com/videoplayback?itag=137&c=ANDROID',
      profile: { userAgent: 'android-client', httpHeaders: ['Accept-Encoding: identity'] },
      rangeMode: 'query',
    }, 'bytes=1024-2047', 7);

    expect(prepared.url.searchParams.get('rn')).toBe('7');
    expect(prepared.url.searchParams.get('range')).toBe('1024-2047');
    expect(prepared.headers.Range).toBeUndefined();
    expect(prepared.headers['User-Agent']).toBe('android-client');
  });

  it('keeps progressive reads in the HTTP Range header', () => {
    const prepared = youtubeMediaProxyInternals.prepareUpstreamRequest({
      source: 'https://r1---sn.example.googlevideo.com/videoplayback?itag=18&c=ANDROID',
      profile: {},
      rangeMode: 'header',
    }, 'bytes=0-', 2);

    expect(prepared.url.searchParams.has('range')).toBe(false);
    expect(prepared.headers.Range).toBe('bytes=0-');
  });

  it('preserves an existing request number without rewriting the signed query', () => {
    const prepared = youtubeMediaProxyInternals.prepareUpstreamRequest({
      source: 'https://r1---sn.example.googlevideo.com/videoplayback?itag=140&rn=42&sig=a~b',
      profile: {},
      rangeMode: 'query',
    }, undefined, 9);

    expect(prepared.url.toString()).toContain('rn=42');
    expect(prepared.url.toString()).not.toContain('rn=9');
    expect(prepared.url.toString()).toContain('sig=a~b');
  });

  it('rejects non-googlevideo and non-playback targets', () => {
    expect(youtubeMediaProxyInternals.isGoogleVideoPlayback(
      'https://googlevideo.com.evil.example/videoplayback?itag=18',
    )).toBe(false);
    expect(youtubeMediaProxyInternals.isGoogleVideoPlayback(
      'https://r1---sn.example.googlevideo.com/not-playback?itag=18',
    )).toBe(false);
  });
});
