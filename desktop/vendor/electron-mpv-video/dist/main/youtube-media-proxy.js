import http from 'node:http';
import https from 'node:https';
import { randomUUID } from 'node:crypto';

const GOOGLEVIDEO_SUFFIX = '.googlevideo.com';
const REQUEST_BODY = Buffer.from([0x78, 0x00]);
const MAX_REDIRECTS = 5;
const MAX_SOURCES = 256;

function isGoogleVideoPlayback(value) {
    let url;
    try {
        url = value instanceof URL ? value : new URL(value);
    }
    catch {
        return false;
    }
    return url.protocol === 'https:'
        && (url.hostname === 'googlevideo.com' || url.hostname.endsWith(GOOGLEVIDEO_SUFFIX))
        && url.pathname.startsWith('/videoplayback');
}

function requestHeaders(profile) {
    const headers = new Map();
    for (const line of profile.httpHeaders ?? []) {
        const separator = line.indexOf(':');
        if (separator <= 0)
            continue;
        const name = line.slice(0, separator).trim();
        const value = line.slice(separator + 1).trim();
        const lowerName = name.toLowerCase();
        if (lowerName === 'host' || lowerName === 'content-length' || lowerName === 'connection'
            || lowerName === 'range' || lowerName === 'transfer-encoding')
            continue;
        headers.set(lowerName, { name, value });
    }
    if (profile.userAgent)
        headers.set('user-agent', { name: 'User-Agent', value: profile.userAgent });
    if (profile.referrer) {
        const referrer = profile.referrer === 'https://www.youtube.com/'
            ? 'https://www.youtube.com'
            : profile.referrer;
        headers.set('referer', { name: 'Referer', value: referrer });
    }
    if (!headers.has('accept'))
        headers.set('accept', { name: 'Accept', value: '*/*' });
    if (!headers.has('accept-encoding'))
        headers.set('accept-encoding', { name: 'Accept-Encoding', value: 'identity' });
    return Object.fromEntries(Array.from(headers.values(), ({ name, value }) => [name, value]));
}

function parseRange(value) {
    if (typeof value !== 'string')
        return undefined;
    const match = /^bytes=(\d+)-(\d*)$/i.exec(value.trim());
    if (!match)
        return undefined;
    return { start: match[1], end: match[2] };
}

function isAdaptiveStream(url, entry) {
    if (entry.rangeMode)
        return entry.rangeMode === 'query';
    if (url.searchParams.has('range'))
        return true;
    const itag = Number(url.searchParams.get('itag'));
    return Number.isFinite(itag) && !new Set([17, 18, 22, 37, 38, 43, 44, 45, 46, 59, 78]).has(itag);
}

function prepareUpstreamRequest(entry, rangeHeader, requestNumber) {
    let source = entry.source;
    if (!source.includes('&rn='))
        source += `${source.includes('?') ? '&' : '?'}rn=${requestNumber}`;
    let url = new URL(source);
    const headers = requestHeaders(entry.profile);
    const range = parseRange(rangeHeader);
    if (range) {
        if (isAdaptiveStream(url, entry)) {
            source += `&range=${range.start}-${range.end}`;
            url = new URL(source);
        }
        else
            headers.Range = `bytes=${range.start}-${range.end}`;
    }
    return { url, headers };
}

function postHeaders(headers) {
    return {
        ...headers,
        // Android's HttpURLConnection supplies this default when setDoOutput(true)
        // is used without an explicit content type. Preserve that wire-level parity
        // together with the two-byte body.
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': String(REQUEST_BODY.length),
    };
}

function safeContext(url, entry, rangeHeader) {
    const safe = (value, fallback = 'unknown') => typeof value === 'string'
        && /^[A-Za-z0-9_.-]{1,48}$/.test(value) ? value : fallback;
    const range = parseRange(rangeHeader);
    return [
        `client=${safe(url.searchParams.get('c'))}`,
        `itag=${safe(url.searchParams.get('itag'))}`,
        `range=${range ? (isAdaptiveStream(url, entry) ? 'query' : 'header') : 'none'}`,
    ].join(', ');
}

function upstreamRequest(url, options, redirects = 0) {
    return new Promise((resolve, reject) => {
        const transport = url.protocol === 'https:' ? https : http;
        const request = transport.request(url, {
            method: 'POST',
            headers: postHeaders(options.headers),
        }, (response) => {
            const status = response.statusCode ?? 0;
            const location = response.headers.location;
            if (location && status >= 300 && status < 400) {
                response.resume();
                if (redirects >= MAX_REDIRECTS) {
                    reject(new Error('Too many redirects from the media server'));
                    return;
                }
                let redirected;
                try {
                    redirected = new URL(location, url);
                }
                catch (error) {
                    reject(error);
                    return;
                }
                if (!isGoogleVideoPlayback(redirected)) {
                    reject(new Error('The media server redirected outside googlevideo.com'));
                    return;
                }
                void upstreamRequest(redirected, options, redirects + 1).then(resolve, reject);
                return;
            }
            resolve(response);
        });
        request.once('error', reject);
        request.end(REQUEST_BODY);
    });
}

function copyResponseHeaders(upstream, response) {
    for (const name of [
        'accept-ranges', 'cache-control', 'content-length', 'content-range', 'content-type',
        'etag', 'expires', 'last-modified',
    ]) {
        const value = upstream.headers[name];
        if (value !== undefined)
            response.setHeader(name, value);
    }
}

/**
 * Bridges mpv's local GET/Range reads to the POST transport used by WizeStream Android's
 * YoutubeHttpDataSource. Signed googlevideo URLs never leave the loopback-only server.
 */
export class YoutubeMediaProxy {
    server = null;
    address = null;
    sources = new Map();
    requestNumber = 0;
    startPromise = null;

    async rewriteMediaRequest(request) {
        return {
            ...request,
            source: await this.rewriteUrl(request.source, request),
            audio: request.audio ? {
                ...request.audio,
                url: await this.rewriteUrl(request.audio.url, request.audio, 'query'),
            } : undefined,
            subtitle: request.subtitle ? {
                ...request.subtitle,
                url: await this.rewriteUrl(request.subtitle.url, request.subtitle),
            } : undefined,
        };
    }

    async rewriteTrack(track, rangeMode = 'query') {
        if (!track)
            return track;
        return { ...track, url: await this.rewriteUrl(track.url, track, rangeMode) };
    }

    async rewriteUrl(source, profile, rangeMode) {
        if (!isGoogleVideoPlayback(source))
            return source;
        await this.start();
        const token = randomUUID();
        this.sources.set(token, { source, profile, rangeMode });
        while (this.sources.size > MAX_SOURCES)
            this.sources.delete(this.sources.keys().next().value);
        return `${this.address}/${token}`;
    }

    async start() {
        if (this.address)
            return;
        if (this.startPromise)
            return this.startPromise;
        this.startPromise = new Promise((resolve, reject) => {
            const server = http.createServer((request, response) => void this.handle(request, response));
            const fail = (error) => {
                server.close();
                reject(error);
            };
            server.once('error', fail);
            server.listen(0, '127.0.0.1', () => {
                server.off('error', fail);
                server.on('error', (error) => console.warn(`[media-proxy] ${error.message}`));
                const address = server.address();
                if (!address || typeof address === 'string') {
                    fail(new Error('Unable to determine the media proxy address'));
                    return;
                }
                this.server = server;
                this.address = `http://127.0.0.1:${address.port}`;
                resolve();
            });
        }).finally(() => {
            this.startPromise = null;
        });
        return this.startPromise;
    }

    async handle(request, response) {
        const token = request.url?.slice(1).split(/[?#]/, 1)[0];
        const entry = token ? this.sources.get(token) : undefined;
        if (!entry || (request.method !== 'GET' && request.method !== 'HEAD')) {
            response.writeHead(404).end();
            return;
        }
        let upstreamUrl;
        try {
            const prepared = prepareUpstreamRequest(entry, request.headers.range, this.requestNumber++);
            upstreamUrl = prepared.url;
            const headers = prepared.headers;
            const upstream = await upstreamRequest(upstreamUrl, { headers });
            if ((upstream.statusCode ?? 0) >= 400) {
                console.warn(`[media-proxy] Upstream HTTP ${upstream.statusCode} (${safeContext(upstreamUrl, entry, request.headers.range)})`);
            }
            copyResponseHeaders(upstream, response);
            response.writeHead(upstream.statusCode ?? 502);
            if (request.method === 'HEAD') {
                upstream.resume();
                response.end();
                return;
            }
            upstream.once('error', (error) => response.destroy(error));
            upstream.pipe(response);
        }
        catch (error) {
            const status = response.headersSent ? undefined : 502;
            console.warn(`[media-proxy] ${error instanceof Error ? error.message : String(error)}`);
            if (status)
                response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' });
            response.end('Media proxy request failed');
        }
    }

    async close() {
        this.sources.clear();
        this.address = null;
        const server = this.server;
        this.server = null;
        if (!server)
            return;
        await new Promise((resolve) => server.close(() => resolve()));
    }
}

export const youtubeMediaProxyInternals = {
    isGoogleVideoPlayback,
    isAdaptiveStream,
    parseRange,
    postHeaders,
    prepareUpstreamRequest,
    requestHeaders,
    safeContext,
    upstreamRequest,
};
