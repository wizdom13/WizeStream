# Play on home audio via yt-fifo

[yt-fifo](https://github.com/jagjordi/yt-fifo) is an optional self-hosted server that resolves a
video with yt-dlp, decodes its audio with ffmpeg and streams it into a
[Snapcast](https://github.com/badaix/snapcast) multi-room setup. WizeStream sends the stream link;
normal playback does not require this server.

## Setup

1. Run yt-fifo and Snapcast on your home network.
2. Put yt-fifo behind an HTTPS reverse proxy with a certificate trusted by Android. The public
   certificate chain must be valid for the hostname entered in WizeStream. A self-signed certificate
   is not accepted by this integration. Keep the service private to your trusted home network or VPN.
3. In **Settings → Video and audio → Home audio server (yt-fifo)**, enter its HTTPS base URL,
   for example `https://audio.example.org` or `https://audio.example.org/yt-fifo`.
   The proxy must forward `POST /queue` (or `/yt-fifo/queue` for the second example) to yt-fifo's
   `POST /queue`, returning its JSON reply. Do not redirect this request to a different URL.
4. Leave the setting empty to hide the home audio actions. Invalid URLs are rejected when saving.

Scheme-less addresses default to HTTPS. Plain HTTP, embedded credentials, query strings, fragments,
and redirects are not supported. WizeStream keeps Android's existing cleartext policy and TLS
certificate validation. This integration adds no authentication protocol; yt-fifo is intended for a
trusted network and should not be exposed as an unauthenticated public queue.

## Playback

Choose **Play on home audio** to interrupt whatever the server is playing, or **Queue on home audio**
to append to its queue. Both actions are available in stream long-press menus and the Cast picker,
including while connected to a TV. Sending audio does not disconnect that TV.

The server answers once yt-dlp has resolved the stream, which can take several seconds. A toast shows
its resolved title after a valid, nonempty `enqueued` reply, or an error. Requests have a 95-second
overall timeout and replies are limited to 64 KiB. If confirmation fails after sending, check the
server's queue before retrying: it may already have accepted the request. Handoffs are not retried
by WizeStream automatically.

Local playback keeps running, as for TubeBeam: pause locally once the home audio system plays.
This integration is separate from Chromecast, FCast and TubeBeam. It only sends links; it does not
provide a server browser, pairing flow, remote transport controls or direct speaker discovery.
