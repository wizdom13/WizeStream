# Timeago parser maintenance resources

This directory contains JSON-formatted timeago strings gathered directly from YouTube and helper
programs used to regenerate the integrated sources under
`app/src/main/java/org/schabi/newpipe/extractor/timeago`.

#### Java directory

These classes generate an overview, check that every language has all time units, and regenerate
the resource bundle classes.

It also contains the resource bundle generator.

#### Times directory

All the units organized by their unit value and name (e.g. 1s = "1 second", 2y = "2 años", 4w = "4 semanas").
