# Phase 16B/16C — structured S-52 render counters

Phase 16B adds a focused structured S-52 render helper without rewriting the large existing WebGL renderer file. Phase 16C wires the browser demo to that helper.

The helper is:

```text
s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52StructuredRender.kt
```

It exposes:

```text
BrowserS57WebGlRenderer.renderS52FrameWithSummary(canvasId, frame)
```

The helper mirrors the existing S-52 browser render path and returns `RenderedFrameSummary` with `s52` populated directly.

## Structured fields

The returned `RenderedFrameSummary.s52` includes:

```text
profile
encFeatureCount
commandCount
drawCallCount
areaCommandCount
lineCommandCount
symbolCommandCount
textCommandCount
soundingCommandCount
diagnosticCount
unsupportedObjectClassCount
unsupportedAttributeCount
failureStage
```

## Failure stages

The helper sets these structured stages:

```text
webgl2
projection
portrayal
webgl-render
none
```

## Demo wiring

The browser demo now calls:

```text
renderer.renderS52FrameWithSummary("chartCanvas", result.frame)
```

This means the existing Phase 16 diagnostics block receives structured S-52 counters from `summary.s52` instead of relying only on the human-readable S-52 render message.

The built-in sample dataset was moved out of `Main.kt` to keep the browser entry point small:

```text
demo/src/jsMain/kotlin/io/github/s57/demo/DemoSampleDataset.kt
```

## Why this is a separate helper

The original `BrowserS57WebGlRenderer.kt` is large and includes the WebGL shader body. The GitHub connector blocked full-file replacement attempts for that file. This phase avoids that risk by adding a small JS helper beside it and wiring the demo to use the helper.
