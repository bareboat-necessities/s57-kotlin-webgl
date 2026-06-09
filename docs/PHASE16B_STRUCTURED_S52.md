# Phase 16B — structured S-52 render counters

Phase 16B adds a focused structured S-52 render helper without rewriting the large existing WebGL renderer file.

The new helper is:

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

## Why this is a separate helper

The original `BrowserS57WebGlRenderer.kt` is large and includes the WebGL shader body. The GitHub connector blocked full-file replacement attempts for that file. This phase avoids that risk by adding a small JS helper beside it.

## Follow-up mechanical change

The browser demo should call:

```text
renderer.renderS52FrameWithSummary("chartCanvas", result.frame)
```

instead of:

```text
renderer.renderS52Frame("chartCanvas", result.frame)
```

That is a one-line demo wiring change. The helper itself is now committed and can be used by the demo or any browser caller.
