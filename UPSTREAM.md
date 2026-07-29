# Upstream tracking

WizeStream has an independent public identity and semantic release cycle. NewPipe version numbers
must not be used as WizeStream version names or as inputs to WizeStream Android version codes.

## Current application baseline

- Project: NewPipe
- Upstream repository: <https://github.com/TeamNewPipe/NewPipe>
- Tracked baseline: tag `v0.28.8`

## Extractor source

WizeStream includes its extractor source directly in the `app` module. Each
WizeStream commit and release therefore records the exact extractor source together with the app.

## Maintenance rule

When upstream changes are imported, record the upstream tag, commit, or pull request in the
WizeStream commit or pull request. Update this file when the application baseline changes. This
tracking is internal development metadata and does not change WizeStream's semantic version.
