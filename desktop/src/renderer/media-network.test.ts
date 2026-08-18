import { describe, expect, it } from 'vitest';
import { mediaNetworkProfile } from './media-network';

const stream = (query: string) =>
  `https://rr1---sn.example.googlevideo.com/videoplayback?itag=18&${query}`;

describe('mediaNetworkProfile', () => {
  it('uses the Safari identity for Safari-created WEB streams', () => {
    const profile = mediaNetworkProfile(stream('c=WEB&cver=2.20260114.08.00'));

    expect(profile.userAgent).toContain('Version/15.5 Safari/605.1.15');
    expect(profile.referrer).toBe('https://www.youtube.com/');
    expect(profile.httpHeaders).toContain('Origin: https://www.youtube.com');
    expect(profile).not.toHaveProperty('httpMethod');
    expect(profile).not.toHaveProperty('httpPostDataHex');
  });

  it('uses the normal desktop identity for regular WEB streams', () => {
    const profile = mediaNetworkProfile(stream('c=WEB&cver=2.20241126.01.00'));

    expect(profile.userAgent).toContain('Firefox/140.0');
    expect(profile.httpHeaders).toContain('Sec-Fetch-Site: cross-site');
  });

  it.each([
    ['ANDROID', 'com.google.android.youtube/21.03.36'],
    ['ANDROID_VR', 'com.google.android.youtube/21.03.36'],
    ['IOS', 'com.google.ios.youtube/19.45.4'],
  ])('uses the %s client identity', (client, expectedUserAgent) => {
    const profile = mediaNetworkProfile(stream(`c=${client}`));

    expect(profile.userAgent).toContain(expectedUserAgent);
    expect(profile.referrer).toBeUndefined();
  });

  it('does not attach YouTube headers to unrelated sources', () => {
    expect(mediaNetworkProfile('https://media.example.com/video.mp4')).toEqual({});
  });
});
