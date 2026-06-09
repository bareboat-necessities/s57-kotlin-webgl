# Phase 20 — viewport and zoom-to-cell correctness

Phase 20 makes the first browser render target the imported cell extent instead of relying on a hard-coded scale and raw cell bounds.

## What changed

```text
- Added ChartViewportFit helpers.
- chartRenderRequestForCell() now fits cell bounds to the canvas aspect ratio.
- Initial scale denominator is estimated from fitted geographic extent and viewport size.
- Demo render path no longer hard-codes scale=40000.
- Render diagnostics now separate onscreen, offscreen, and clipped features.
- Phase16 diagnostics now report viewport stage when features exist but are offscreen.
```

## New helper

```text
s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ChartViewportFit.kt
```

Key API:

```text
fitChartBoundsToViewport(sourceBounds, viewport, paddingFraction)
chartViewportFitForBounds(sourceBounds, viewport, paddingFraction)
estimateScaleDenominator(bounds, viewport)
GeoBounds.center()
```

## Render request behavior

```text
chartRenderRequestForCell(cell, widthPx, heightPx)
```

now:

```text
1. Requires cell bounds.
2. Expands bounds to match viewport aspect ratio.
3. Applies padding.
4. Estimates scale denominator.
5. Sets camera center to fitted bounds center.
```

Manual scale and center overrides still work through optional arguments.

## Diagnostics

`RenderedArtifactReport` now includes:

```text
onscreenFeatureCount
offscreenFeatureCount
clippedFeatureCount
```

`StaticChartFrame.summary()` now includes:

```text
onscreen=...
offscreen=...
clipped=...
```

`Phase16Counters.stage()` now returns:

```text
viewport
```

when features project but all are offscreen.

## Tests

```text
s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/Phase20ViewportFitTest.kt
```

Covers:

```text
- Aspect-ratio fitting with padding.
- Auto-fit render request creation from cell bounds.
- Offscreen and clipped feature diagnostics.
```

## Acceptance

After loading a NOAA `.000` cell, first render should be centered on the imported cell extent. If nothing appears, diagnostics can now distinguish:

```text
- no decoded features
- no indexed/query result
- no projected geometry
- projected but offscreen viewport
- S-52 portrayal/render failure
```
