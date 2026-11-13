WizeStream adapts its controls to phones, tablets and Android TV, and offers audio-oriented Android Auto integration. Casting and home-audio handoff send media information to another device or server; they are different from displaying the Android app itself on that device.

## Phones, tablets and TV controls

Phone layouts prioritize direct touch controls. Tablet and landscape layouts can provide more room for lists, navigation rails and adaptive settings panes. Grid columns, title sizes and navigation choices can be adjusted in appearance settings.

On Android TV, use D-pad focus and playback shortcuts instead of assuming every touch gesture has an identical remote action. Miniplayer actions and direct seeking have their own focus paths. If you report a TV problem, include the TV model, Android version, remote action and whether the player was fullscreen.

## Android Auto is audio-first

WizeStream provides audio browsing, supported voice search and playback resume on Android Auto. It does not present a video player or visualizer on the driving surface.

1. Install a trusted WizeStream build and open it on the phone at least once.
2. Connect the phone to Android Auto using your normal connection method.
3. Look for WizeStream in the media-app launcher and start supported audio content.
4. If a sideloaded, debug or nightly build is hidden, inspect Android Auto’s developer-only Unknown sources setting.

Android Auto’s Unknown sources setting is distinct from Android’s permission to install an APK. Developer settings are not normally needed just to play audio from a recognized supported installation. Check the linked project guide before changing them.

## Cast to a receiver

Casting is supported on **Android 8 and newer** with compatible FCast and Chromecast-compatible receivers on the same local network. It does not require Google Play Services for WizeStream’s discovery and casting path.

Open a compatible stream’s details and choose **Cast to TV**. Select a receiver, then use the available play, pause, stop and receiver-switching actions.

The receiver requests the selected media from its original service. Some videos expose no compatible castable URL or format, and a receiver may support fewer codecs than your phone. Kodi and external-player handoffs are separate options rather than the same casting protocol.

## Local-network checks

Guest Wi-Fi, client isolation, VPN routing and firewalls can prevent discovery even when both devices appear connected. Confirm local reachability before assuming the media source itself has failed.

Device Sync also uses the local network, but it transfers supported library records rather than controlling a TV player. Its setup is covered in [profiles and synchronization](profiles-sync.html).

## Home audio with yt-fifo and Snapcast

You can optionally send a stream link to your own yt-fifo server for a Snapcast home-audio setup. Configure **Home audio server (yt-fifo)** in Video and audio settings. A trusted HTTPS endpoint is required; leaving the setting empty hides the actions.

Use Play now or the queue action from a stream menu or Cast picker. The link is handed to the server; local playback continues, so pause it separately if both outputs would be audible.

Keep the server on a trusted home network or a suitably protected VPN. When delivery is uncertain, check the server queue before retrying: automatically replaying an ambiguous request could queue the same item twice.

## Floating playback permissions

The WizeStream popup needs overlay access where Android requires it. Native Android PiP is a separate Android 8+ feature with its own app/system controls. Grant a permission for the floating mode you actually intend to use rather than treating all display permissions as interchangeable.
