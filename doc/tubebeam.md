# Send to TV via TubeBeam

Install [TubeBeam](https://github.com/jagjordi/tubebeam) on your phone and SmartTube on your Android TV. Configure a TV in TubeBeam and follow its [TV setup instructions](https://github.com/jagjordi/tubebeam#tv-setup-once-per-tv), including enabling network ADB debugging and approving the phone on the TV. Use a trusted home network. This is an optional external-app integration, separate from Chromecast and FCast; WizeStream does not manage ADB connections or keys.

For YouTube videos, choose **Send to TV via TubeBeam** from the long-press menu or the Cast picker. The action is hidden when TubeBeam is unavailable. Sending the currently playing video from the Cast picker includes its playback position, except for live streams.

TubeBeam can show a TV picker, setup screen or connection error. It does not report remote playback success back to WizeStream, so WizeStream leaves local playback running. Pause locally once playback starts on the TV. If TubeBeam cannot be opened, WizeStream displays an error.

**Queue on TV is deliberately not offered:** TubeBeam currently requires a patched SmartTube build for queue support; stock SmartTube instead starts the video immediately.
