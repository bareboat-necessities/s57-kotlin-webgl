# S-52 0.5.4 demo render fallback fix

This incremental patch fixes the browser demo staying blue even when S-52
portrayal succeeds.

## Problem

The diagnostics showed S-52 portrayal was already producing commands for both
the built-in sample and NOAA cells, but the S-52 WebGL backend still failed in
some browsers with:

```text
S-52 WebGL render failed after portrayal: WebGL2 is not available in this browser
```

That left `s52DrawCalls=0` and the canvas blank blue.

## Fix

- Stop pre-probing/replacing the chart canvas before constructing
  `WebGlS52Renderer`.  The S-52 WebGL renderer is now the first path to request
  the WebGL2 context.
- Add a last-resort Canvas2D renderer for **S-52 draw commands**.  This is not
  the old decoded geometry/debug fallback; it consumes the S-52 command list
  after portrayal and uses S-52 colors where possible.
- When S-52 WebGL construction/rendering fails but commands exist, render those
  commands through Canvas2D instead of leaving the demo blank.
- Emit diagnostic code `s52.webgl_backend_canvas2d_fallback` so it remains clear
  that the fallback path was used.

## Changed files

- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52StructuredRender.kt`
- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52CanvasCommandFallback.kt`
