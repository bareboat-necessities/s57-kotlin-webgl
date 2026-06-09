# Phase 21 — first real NOAA ENC visual smoke milestone

Phase 21 adds the harness and gates for testing one real NOAA ENC `.000` file end-to-end through import, indexing, viewport fit, projection, and SVG snapshot generation.

## Important boundary

No NOAA ENC fixture is committed to this repository in Phase 21. The repo now contains the smoke harness and validation tests. To complete a real-cell run, point the task at a local NOAA `.000` file.

## Run with a real NOAA cell

```bash
gradle :s57-render-webgl:phase21NoaaVisualSmoke \
  -Pphase21.noaaEncFile=/path/to/US5xxxxx.000 \
  -Pphase21.snapshotSvg=build/phase21/noaa-smoke.svg \
  -Ps52.version=0.3.0
```

The task prints a structured smoke report and writes an SVG snapshot.

## Harness files

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/NoaaEncVisualSmoke.kt
s57-render-webgl/src/jvmMain/kotlin/io/github/s57/render/NoaaEncVisualSmokeMain.kt
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase21VisualSmokeTest.kt
```

## Validation gates

The Phase 21 smoke report checks:

```text
- cell has bounds
- raw feature/vector decode is nonzero when running from a real .000
- decoded feature count is nonzero
- indexed feature count is nonzero
- queried feature count is nonzero
- adapted feature count is nonzero
- projected feature count is nonzero
- onscreen feature count is nonzero
- object-class diversity is nonzero
- rendered artifact passes viewport-aware minimum validation
```

## Output report

The task prints lines like:

```text
phase21 cell=US5xxxxx hasBounds=true
rawFeatures=... rawVectors=... decodedFeatures=... geometryDiagnostics=...
indexedFeatures=... queriedFeatures=... adaptedFeatures=... projectedFeatures=...
onscreenFeatures=... offscreenFeatures=... clippedFeatures=... emptyGeometry=...
objectClasses=DEPARE=...,DEPCNT=...,SOUNDG=...
renderedArtifact 1280x800 features=... visible=... onscreen=... offscreen=... clipped=...
snapshot=/absolute/path/to/build/phase21/noaa-smoke.svg
```

## What this catches

The smoke harness distinguishes these failure stages:

```text
- raw decode failed or produced no features/vectors
- geometry build produced no bounds
- index contains no usable features
- viewport query misses all features
- adapter rejects everything
- projection creates no screen geometry
- features project entirely offscreen
- object classes all collapse to unknown/unsupported buckets
```

## Next phase

Phase 22 should turn this harness into browser usability: list imported cells, select active cell, expose bounds/feature summaries, and add reload/clear/palette controls.
