export interface MediaNetworkProfile {
  userAgent?: string;
  referrer?: string;
  httpHeaders?: string[];
}

const FIREFOX_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0';
const SAFARI_CLIENT_VERSION = '2.20260114.08.00';
const SAFARI_USER_AGENT =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)';
const ANDROID_USER_AGENT =
  'com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip';
const IOS_USER_AGENT =
  'com.google.ios.youtube/19.45.4(iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X; GB)';

function isGoogleVideoPlayback(url: URL): boolean {
  return (url.hostname === 'googlevideo.com' || url.hostname.endsWith('.googlevideo.com'))
    && url.pathname.startsWith('/videoplayback');
}

/**
 * Replays an extracted YouTube media URL with the identity of the client which created it.
 * YouTube can reject an otherwise valid signed URL when its client-specific headers change.
 */
export function mediaNetworkProfile(source: string): MediaNetworkProfile {
  let url: URL;
  try {
    url = new URL(source);
  } catch {
    return {};
  }
  if (!isGoogleVideoPlayback(url)) return {};

  const client = url.searchParams.get('c')?.toUpperCase();
  const clientVersion = url.searchParams.get('cver');
  const userAgent = client === 'WEB' && clientVersion === SAFARI_CLIENT_VERSION
    ? SAFARI_USER_AGENT
    : client === 'ANDROID' || client === 'ANDROID_VR'
      ? ANDROID_USER_AGENT
      : client === 'IOS'
        ? IOS_USER_AGENT
        : FIREFOX_USER_AGENT;
  const isWebClient = client === 'WEB' || client === 'TVHTML5_SIMPLY_EMBEDDED_PLAYER';

  return {
    userAgent,
    referrer: isWebClient ? 'https://www.youtube.com/' : undefined,
    httpHeaders: [
      ...(isWebClient ? [
        'Origin: https://www.youtube.com',
        'Sec-Fetch-Dest: empty',
        'Sec-Fetch-Mode: cors',
        'Sec-Fetch-Site: cross-site',
      ] : []),
      'TE: trailers',
      'Accept-Encoding: identity',
    ],
  };
}
