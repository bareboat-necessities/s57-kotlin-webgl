# Phase 15 — build and smoke verification

Phase 15 verifies that the current browser demo wiring can be built with an external `s52-kotlin-webgl` checkout and that the common engine path still produces a visible static frame for the built-in sample data.

This phase does not attempt to fix real NOAA ENC topology yet. That belongs to the following S-57 geometry phases.

## Required external project

The S-57 browser demo resolves S-52 browser resources from an external `bareboat-necessities/s52-kotlin-webgl` checkout or release source tree.

Use one of these:

```text
-Ps52SourceDir=/path/to/s52-kotlin-webgl
```

or:

```text
S52_SOURCE_DIR=/path/to/s52-kotlin-webgl
```

## Local verification

Run:

```text
./gradlew :demo:phase12_13_14BCheck -Ps52SourceDir=/path/to/s52-kotlin-webgl
```

Then run:

```text
./gradlew phase11Check :s57-render-webgl:allTests -Ps52SourceDir=/path/to/s52-kotlin-webgl
```

The Phase 15 smoke test is:

```text
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase15EngineSmokeTest.kt
```

It verifies:

```text
- built-in sample dataset imports into S57WebGlEngine
- cell listing works
- static render produces visible projected features
- rendered artifact diagnostics are non-empty
- clear() removes previously imported cells
```

## Acceptance

Phase 15 is accepted when CI can run the external S-52 resource check and the S-57 WebGL engine tests without committing any S-52 atlas PNG files into this repository.
