# Upstream tracking

WizeStream has an independent public identity and semantic release cycle. NewPipe version numbers
must not be used as WizeStream version names or as inputs to WizeStream Android version codes.

## Current application baseline

- Project: NewPipe
- Upstream repository: <https://github.com/TeamNewPipe/NewPipe>
- Tracked baseline: tag `v0.28.8`

## Extractor source

WizeStream includes its extractor source directly in the `app` module. The source is derived from
NewPipeExtractor and later incorporated PipePipeExtractor and WizeStream-specific changes. It is
not the unmodified official NewPipeExtractor and is no longer consumed as an external
PipePipeExtractor or WizeStreamExtractor dependency.

The BitChute and Rumble service implementations are derived from BravePipeExtractor commit
`6e3e3f9769bf35963f79fdac2df8e85aa292de6e` and adapted to WizeStream's integrated extractor API.

Each WizeStream commit and release records the exact extractor source together with the app. This
allows application, player, and service compatibility changes to be developed and tested together.

## Issue responsibility

Problems observed in WizeStream, including extractor and service failures, belong in the
WizeStream issue tracker first. Reproduction in official NewPipe can be requested to determine
whether a defect is shared with upstream, but WizeStream is responsible for failures caused by its
integrated changes. An issue should be reported to an upstream project only when it is reproducible
there and complies with that project's reporting rules.

## Maintenance rule

When upstream changes are imported, record the upstream tag, commit, or pull request in the
WizeStream commit or pull request. Update this file when the application baseline changes. This
tracking is internal development metadata and does not change WizeStream's semantic version.
