# Invidious Companion media servers

Some instances serve API requests on one host and redirect playback to an Invidious
Companion on another HTTPS host. WizeStream blocks those redirects by default. A
blocked redirect does not establish that the destination is safe or that it is a
Companion: verify it with the instance operator before granting access.

In **Settings → Content**, select the instance, then open **Trusted Invidious media
servers**. Enter up to four verified HTTPS root URLs, one per line, for example
`https://media.example.org`. Paths, credentials, queries, fragments, HTTP URLs and
YouTube/Google video hosts are rejected. Changes while Invidious is enabled restart
the app so active requests cannot retain the previous policy.

The list belongs only to the selected instance. Switching instances does not transfer
its permissions. Clear the list to restore instance-only media access. Saved instance
choices are not automatically trusted as media servers. A malformed restored list
permits no additional servers.

These servers can see your media requests and IP address. Direct YouTube connections
remain blocked, and WizeStream does not automatically trust an arbitrary redirect or
fall back to direct playback. Media, manifest, segment and download requests must stay
on the selected instance or one of its explicitly trusted servers. Thumbnail routing
and API selection continue to use the selected instance.

Errors report only the blocked origin, without signed media paths or query parameters.
If the instance itself is unavailable, adding servers will not repair that outage.
