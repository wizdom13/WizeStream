# Play on home audio via yt-fifo

[yt-fifo](https://github.com/jagjordi/yt-fifo) is a small self-hosted server that resolves a video with yt-dlp, decodes its audio with ffmpeg and streams it into a [Snapcast](https://github.com/badaix/snapcast) multi-room setup. Run it on your home network and enter its URL as **Home audio server (yt-fifo)** in the video and audio settings, e.g. `http://192.168.1.10:8888`. The entries are hidden while no URL is set. This is an optional external-service integration, separate from Chromecast, FCast and TubeBeam; WizeStream only posts the stream link to the server.

Choose **Play on home audio** to interrupt whatever the server is playing, or **Queue on home audio** to append to its queue, from the long-press menu of any stream or from the Cast picker. The server answers once yt-dlp has resolved the stream, which can take a few seconds; a toast then shows the resolved title, or the server's error message for an unavailable video. Local playback keeps running, as for TubeBeam: pause locally once the home audio system plays.

The server is a plain-http LAN service, so the application allows cleartext traffic (`android:usesCleartextTraffic`); every WizeStream request to streaming services stays https.
