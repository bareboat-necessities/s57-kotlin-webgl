# Phase 24 — performance and timing diagnostics

Phase 24 adds first-class timing diagnostics to the import/render path. The goal is not optimization yet; it is to make slow stages visible and repeatable before Phase 25+ work.

## Added files

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/PerformanceDiagnostics.kt
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/PerformanceMetrics.kt
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase24PerformanceDiagnosticsTest.kt
s57-render-webgl/src/jvmMain/kotlin/io/github/s57/render/PerformanceClockJvm.kt
s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/PerformanceClockJs.kt
```

## Updated files

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57WebGlEngine.kt
s57-render-webgl/src/jvmMain/kotlin/io/github/s57/render/NoaaEncVisualSmokeMain.kt
```

## Engine timing

`S57EngineImportResult` now carries:

```text
EngineTimingReport(
  decodeMs,
  indexMs
)
```

`S57EngineRenderResult` now carries:

```text
EngineTimingReport(
  framePrepareMs,
  artifactAnalyzeMs
)
```

The existing API shape remains the same: callers still use `engine.importS57Bytes(...)`, `engine.importDataset(...)`, and `engine.render(...)`. Timing is additional metadata on the returned result.

## Metric collector

`PerformanceMetricCollector` stores named samples and reports:

```text
count
minMs
maxMs
avgMs
```

Typical names:

```text
file.read
import.decode
import.index
import.total
render.framePrepare
render.artifactAnalyze
render.total
cache.list
cache.put
cache.load
cache.clear
```

## Real NOAA smoke timing

The Phase 21 real-cell harness now prints performance data:

```bash
gradle :s57-render-webgl:phase21NoaaVisualSmoke \
  -Pphase21.noaaEncFile=/path/to/US5xxxxx.000 \
  -Pphase21.snapshotSvg=build/phase24/noaa-smoke.svg
```

Expected output includes:

```text
performance cell=... features=... onscreen=... offscreen=... scale=...
timing decodeMs=... indexMs=... framePrepareMs=... artifactAnalyzeMs=... totalMs=...
performanceMetrics samples=...
file.read count=1 minMs=... maxMs=... avgMs=...
import.decode count=1 minMs=... maxMs=... avgMs=...
render.framePrepare count=1 minMs=... maxMs=... avgMs=...
```

## Acceptance

Phase 24 is complete when timing data is available for:

```text
- raw ENC file read in the JVM smoke harness
- S-57 decode
- index import
- static frame preparation
- rendered artifact analysis
- aggregate named metrics
```

## Next work

Phase 25 should use these timings to guide optimization: batch decode/index profiling, IndexedDB write/read throughput, object-class filtering cost, projection cost, and S-52 portrayal/render breakdown.
