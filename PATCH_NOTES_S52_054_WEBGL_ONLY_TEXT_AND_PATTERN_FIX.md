# S-52 0.5.4 WebGL-only text and pattern cleanup

This patch removes the browser demo's non-WebGL S-52 fallback path and fixes the remaining clutter introduced by raster/pattern tiles and browser-dependent text rendering.

## Changes

- Removes the runtime call to the S-52 Canvas2D command fallback.
- Replaces `BrowserS52CanvasCommandFallback.kt` with a non-rendering tombstone so incremental overwrite patches no longer leave an active fallback implementation behind.
- Disables Canvas2D fallback use in the older decoded-geometry renderer paths; they now fail with diagnostics if WebGL is unavailable.
- Keeps the S-52 path WebGL-only: S-52 WebGL renders fills/lines/symbols and a new WebGL vector label overlay renders labels.
- Removes S-52 `Text` and `Sounding` commands from the dependency renderer path to avoid Chromium's ugly/browser-dependent text rasterization.
- Draws de-conflicted labels with `BrowserS52WebGlVectorLabelOverlay`, a small vector stroke font rendered with WebGL line batches.
- Suppresses all `AreaPattern` tile overlays in the interactive browser display plan, while preserving/injecting solid area fills from S-52 background color tokens.
- Keeps diagnostic reporting through `s52.browser_display_filter`, now including `vectorLabelCommands`.

## Expected result

- No Canvas2D/debug fallback rendering on S-52 failure.
- Text labels are drawn via WebGL and should look the same in Chromium and Firefox.
- Repeated rounded pattern/tile frames should disappear.
- Area fills should remain visible where the S-52 pattern command carries a background fill token.
