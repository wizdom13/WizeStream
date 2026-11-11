# Deterministic R8 retarget interfaces

Issue: [#243](https://github.com/wizdom13/WizeStream/issues/243).

The R8 9.2.14 compiler bundled in AGP 9.2.1 injects interfaces for subclasses of
`java.io.ByteArrayInputStream` by iterating an `IdentityHashMap`. Different JVM object identity
hashes can reverse `ByteArrayInputStreamRetargetInterface` and `InputStreamRetargetInterface` in
the output DEX. Limiting processors does not control this map order.

The equivalent change to the [upstream source](https://r8.googlesource.com/r8/+/9.2.14/src/main/java/com/android/tools/r8/ir/desugar/desugaredlibrary/retargeter/DesugaredLibraryRetargeterPostProcessor.java)
in `ensureInterfacesAndForwardingMethodsSynthesized` is:

```diff
- Map<DexType, List<DexMethod>> map = Maps.newIdentityHashMap();
+ Map<DexType, List<DexMethod>> map = new java.util.TreeMap<>();
```

`DexType` implements `Comparable` through `StructuralItem` and compares type descriptors.
The map groups canonical compiler types, so descriptor equality preserves the grouping while
fixing its iteration order. Existing interfaces retain their order; only the order in which
the retargeter appends generated interfaces changes.

The release init script applies this change to the embedded compiler's obfuscated class
`com/android/tools/r8/internal/o90.class`. Its exact SHA-256 is checked before replacing the
single `java/util/IdentityHashMap` class constant with `java/util/TreeMap`. This also updates the
constructor, casts, method owners and verification frames without editing instructions. Other
compiler classes and Gradle's downloaded artifacts remain unchanged. The transformed compiler
contains `META-INF/wizestream-stable-retarget-interfaces.txt` to identify the local correction.

Both settings and project buildscript classpaths are transformed, because Android's settings
plugin can load the compiler before the project plugin does. The correction is enabled by
`scripts/reproducible-build.sh`, which independent rebuilders must also use.

## Validation and compiler upgrades

`scripts/reproducible-build.sh --verify` builds the full APK with HotSpot identity-hash strategies
5 and 3. Identical outputs are required. Strategy 3 changes map iteration without the performance
penalty of forcing every object to the same hash. This runs in the release and reproducibility
workflows, including pull requests that change this script.

When upgrading AGP/R8, the checksum guard must be reviewed. Once upstream fixes the retargeter,
remove the init-script argument and this workaround, then require the two-strategy verification
and an independent rebuild to pass before considering the issue resolved. Do not merely update
the checksum to accept a new compiler.
