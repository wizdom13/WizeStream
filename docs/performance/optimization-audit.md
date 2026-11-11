# Size and performance audit

Tracking issue: https://github.com/wizdom13/WizeStream/issues/1284

## Measured package baseline

Measured on 2026-11-11 from the published `v1.40.1` ARM release asset
`wizestream_v1.40.1.apk`. The complete report and SHA-256 are in
[v1.40.1-arm-apk.json](v1.40.1-arm-apk.json).

| Component | Compressed bytes in ZIP | Uncompressed bytes |
| --- | ---: | ---: |
| DEX bytecode, all four files | 8,551,671 | 20,150,892 |
| Android resource table | 6,885,128 | 6,885,128 |
| Native libraries, both ARM ABIs | 4,465,200 | 4,465,200 |
| Bundled `org/` resources | 1,273,262 | 1,507,187 |
| `res/` entries | 747,794 | 1,677,045 |
| App assets | 108,403 | 329,543 |

The APK itself is **22,493,126 bytes**; the full uncompressed ZIP payload is
**35,671,933 bytes**. Neither number is Android's installed-size measurement.
Installed code, compiled artifacts, private data, and cache must be measured separately.
These measurements do not establish a startup, memory, stability, or scrolling problem.

Release builds already enable R8 minification and Android resource shrinking. The normal
ARM APK contains both `arm64-v8a` and `armeabi-v7a`. The FCast sender libraries account for
4,144,836 uncompressed bytes across those two ABIs. The three Bouncy Castle Picnic
parameter resources account for 1,214,815 compressed bytes. Their presence alone does not
prove they are unused: dependency reachability and supported cryptographic operations
must be checked before exclusion. Removing casting, translations, architectures, or
cryptographic resources without that evidence is not an approved optimization.

## Repeatable package comparison

Run against release builds with matching signing, ABI selection, R8 configuration, and
features. Debug APKs are not a valid release-size baseline.

```sh
python scripts/analyze-apk.py path/to/wizestream.apk --output /tmp/apk-size.json
```

Compare `apk_bytes`, component totals, and the largest entries against the checked-in
baseline. File-byte totals include ZIP/signature/alignment overhead; component totals
count the corresponding ZIP entries. The analyzer does not modify the APK.

## Runtime measurement procedure

Use the same physical device, Android version, app version/build type, thermal state,
network, profile, and copied test library for before/after measurements. Keep app data
when comparing normal use. Use a separate test installation for clean-install checks.
Record device details, data set size, and whether each measurement was cold or warm.

1. Record Android Settings' **App size**, **User data**, and **Cache** separately. A single
   total cannot identify which part needs reducing.
2. Capture ten cold and ten warm launches with `adb shell am start -W -n
   org.wisso.newpipematerial/org.schabi.newpipe.MainActivity`; force-stop between cold
   launches. Keep the device awake and report the median and slowest run separately.
3. Capture `adb shell dumpsys meminfo org.wisso.newpipematerial` after launch, after loading
   a fixed large subscription/local-media library, during playback, and after playback
   stops. Record PSS and the exact workload; do not compare an idle run with a playback run.
4. Reset frame statistics using `adb shell dumpsys gfxinfo org.wisso.newpipematerial reset`,
   scroll the same list for the same duration, then collect `adb shell dumpsys gfxinfo
   org.wisso.newpipematerial framestats`. Record frame counts and jank, and use a Perfetto
   trace to locate UI-thread, image-decoding, database, or layout work when warranted.
5. Repeat list navigation, playback start/stop, profile switching, and leaving/returning
   to the app. Record reproducible crashes or retained memory before selecting a fix.

No physical-device runtime measurements have been collected for this audit. Startup,
RAM, scrolling, and stability results remain **unmeasured**; no performance improvement
or size reduction is claimed by this change.

## Follow-up decisions

- Investigate DEX/library contribution using release dependency and R8 reachability reports.
- Inspect resource-table costs while preserving supported languages and configurations.
- Evaluate optional per-ABI distribution only with release/reproducibility metadata updates
  and a clear installation path for existing 32-bit devices.
- Profile the runtime workloads above before changing caches, database queries, image
  loading, or player lifecycle behavior.
- For each targeted optimization, publish before/after results and verify playback,
  synchronization, supported services, accessibility, and reproducible builds.

Keep #1284 open until targeted changes have demonstrated useful improvements.
