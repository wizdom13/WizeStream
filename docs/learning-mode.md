# Learning Mode

Learning Mode adds optional study tools to WizeStream without changing normal playback. It is
disabled by default, and only content that you explicitly designate as Learning contributes to study
activity.

## Enable Learning Mode

1. Open **Settings > Learning Mode**.
2. Enable **Learning Mode**.
3. Choose whether to enable timestamped notes, playlist progress, and background-listening time.

Enabling Learning Mode makes the Learning actions and dashboard available. It does not automatically
classify everything you watch or listen to as study activity. The **Learning** dashboard appears in
the navigation drawer while Learning Mode is enabled.

## Select Learning content

WizeStream supports individual videos, local media, local playlists, and remote playlists as Learning
content.

### Individual videos and local media

Open a video's context menu, usually by long-pressing its card or opening its item menu, and select
**Mark as Learning**. Use **Remove from Learning** in the same menu to remove its individual mark.

### Local playlists

Open the playlist menu and select **Mark as Learning**. The playlist and its current contents become
Learning content. When playlist-progress tools are enabled, the playlist also provides controls to
mark all videos as watched or reset its learning progress.

Use **Remove from Learning** in the playlist menu to remove the playlist designation.

### Remote playlists

Open the remote playlist menu and select **Mark as Learning**. WizeStream indexes the playlist's
contents as its pages load and includes them in the Learning dashboard and progress calculations.

Use **Remove from Learning** in the playlist menu to remove the playlist designation.

## How overlapping selections work

WizeStream remembers why a video is Learning content. A video can be designated directly, through a
local playlist, through a remote playlist, or through more than one of these sources.

Removing one mark removes only that source. The video remains Learning content while another marked
source still includes it. To exclude the video completely, remove every direct or playlist mark that
designates it.

## Tracked activity

Only playback of designated Learning content is included in:

- Study time for today, the current week, and all time
- Current and longest study streaks
- The 28-day activity calendar
- Learning progress and completion
- Continue Learning and other dashboard sections

Background playback is included only when **Count background listening** is enabled under
**Settings > Learning Mode**. Normal, unmarked playback remains available as usual but does not
affect Learning statistics or streaks.

Timestamped notes are available for designated, non-live videos when learning notes are enabled. A
note records the associated playback position so the video can be reopened at that point.

## Learning dashboard

Open **Learning** from the navigation drawer to see:

- Study-time totals, streaks, and recent activity
- All designated Learning content
- Videos ready to continue
- Active and completed Learning playlists
- Recently annotated videos

The exact sections shown depend on the Learning Mode options enabled in Settings and the Learning
content available on the device.

## Existing data after an upgrade

Upgrading preserves existing learning notes and session history. A video that already has a
timestamped learning note is automatically designated as Learning content, and its related historical
sessions remain included. Historical activity for other unmarked content is preserved in the database
but is not counted as designated Learning activity.

## Device synchronization

Learning-content selections are currently local to each device and are not synchronized. Mark the
required videos and playlists separately on every device where you want them treated as Learning
content.

Timestamped learning notes have their own optional synchronization setting under
**Settings > Device synchronization**. Note synchronization does not synchronize Learning-content
selections.
