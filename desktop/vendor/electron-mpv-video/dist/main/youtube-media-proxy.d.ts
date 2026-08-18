interface NetworkProfile {
    userAgent?: string;
    referrer?: string;
    httpHeaders?: string[];
}

interface MediaTrack extends NetworkProfile {
    url: string;
    title?: string;
    language?: string;
}

interface MediaRequest extends NetworkProfile {
    source: string;
    audio?: MediaTrack;
    subtitle?: MediaTrack;
}

interface ProxySource {
    source: string;
    profile: NetworkProfile;
    rangeMode?: 'header' | 'query';
}

export declare class YoutubeMediaProxy {
    rewriteMediaRequest<T extends MediaRequest>(request: T): Promise<T>;
    rewriteTrack<T extends MediaTrack>(track: T | undefined, rangeMode?: 'header' | 'query'): Promise<T | undefined>;
    rewriteUrl(source: string, profile: NetworkProfile, rangeMode?: 'header' | 'query'): Promise<string>;
    close(): Promise<void>;
}

export declare const youtubeMediaProxyInternals: {
    isGoogleVideoPlayback(value: string | URL): boolean;
    isAdaptiveStream(url: URL, entry: ProxySource): boolean;
    parseRange(value: unknown): { start: string; end: string } | undefined;
    postHeaders(headers: Record<string, string>): Record<string, string>;
    prepareUpstreamRequest(
        entry: ProxySource,
        rangeHeader: string | undefined,
        requestNumber: number,
    ): { url: URL; headers: Record<string, string> };
    requestHeaders(profile: NetworkProfile): Record<string, string>;
    safeContext(url: URL, entry: ProxySource, rangeHeader: string | undefined): string;
    upstreamRequest(
        url: URL,
        options: { headers: Record<string, string> },
        redirects?: number,
    ): Promise<import('node:http').IncomingMessage>;
};
