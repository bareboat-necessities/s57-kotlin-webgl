# Phase 16 — browser ENC smoke diagnostics

Phase 16 adds structured counters that make an empty browser render easier to localize.

The goal is not to fix NOAA topology yet. The goal is to expose where the selected `.000` path stops:

```text
S-57 raw decode
S-57 bounds
S-57 feature decode
index query
projection
visible geometry
adapter
S-52 portrayal
S-52 WebGL
```

## Added counters

The common model is `Phase16Counters` in:

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/Phase16Diagnostics.kt
```

It carries:

```text
rawFeatures
rawVectors
decodedFeatures
hasBounds
geometryDiagnostics
indexedFeatures
queriedFeatures
adaptedFeatures
projectedFeatures
visibleFeatures
emptyGeometry
adapterDiagnostics
s52 profile and command counts
```

`Phase16Counters.stage()` returns the first likely failing stage.

## Browser demo

The browser demo status panel now prints a `Phase16 diagnostics` block after rendering a selected ENC or the built-in sample.

It includes the S-57 import counters, frame counters, artifact counters, adapter diagnostic count, and the S-52 render message.

## Known limitation

The large WebGL renderer body still returns detailed S-52 counters in the message string. A follow-up commit should move those values into `RenderedFrameSummary.s52` directly. That follow-up is deliberately small and mechanical; Phase 16 already exposes the shared counter model and the demo diagnostic block.

## Tests

The common diagnostics tests are in:

```text
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase16DiagnosticsTest.kt
```

They verify stage selection for raw decode failure, missing bounds, projection failure, S-52 portrayal failure, and a successful fully counted pipeline.
