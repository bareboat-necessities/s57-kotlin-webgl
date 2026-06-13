# WebGL2 performance path update

This update keeps the renderer strict WebGL2-only and removes remaining per-label GPU churn from the S-57-owned S-52 text/sounding postpass.

## What changed

- S-52 WebGL text/sounding postpass now builds one packed vertex buffer for all visible labels in a frame.
- Labels/soundings are drawn with one `drawArrays(TRIANGLES, 0, labelCount * 6)` call instead of one buffer upload and one draw call per label.
- The text postpass requests the existing WebGL2 context with high-performance attributes when possible:
  - `stencil: true`
  - `preserveDrawingBuffer: false`
  - `powerPreference: high-performance`
  - `desynchronized: true`
- The strict WebGL2 compatibility status now reports capability bits for:
  - VAO support
  - instanced drawing support
  - UBO support
  - fence sync support
  - framebuffer invalidation support
  - texture/attribute limits
- The debug/placeholder S-57 renderer paths no longer use Kotlin/JS safe-casts for WebGL2 contexts.

## Diagnostics

The text postpass now exports:

- `window.s57S52TextPostpassBatchDrawCalls`
- `window.s57S52TextPostpassBatchVertices`
- `window.s57S52TextPostpassBatchLabels`

A healthy text frame should show `s57S52TextPostpassBatchDrawCalls = 1` when labels are visible.

## Still external to S-57

The main S-52 geometry renderers live in the S-52 library. To fully exploit WebGL2 there as well, S-52 should batch strokes/fills/symbols using VAOs, instanced symbol quads, persistent program/buffer caches, and packed atlas texture draws. This update applies the WebGL2 batching improvements to the S-57-owned text/sounding overlay path.
