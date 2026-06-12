# S-52 0.5.4 resource redraw de-duplication fix

This incremental patch fixes a browser rendering artifact where the same ENC object
could appear twice with different symbols stacked on top of each other.

The S-52 WebGL renderer can request an asynchronous resource redraw when raster
symbols or patterns become available.  In the demo wrapper, that callback could
re-enter `WebGlS52Renderer.render(...)` while the first render pass was still in
progress.  That allowed the resource-ready pass and the original pass to interleave,
which made symbols/patterns look like they were rendered twice.

Changes:

- Adds a re-entrancy guard around the cached `WebGlS52Renderer`.
- Coalesces resource-ready callbacks into one deferred redraw with `setTimeout(..., 0)`.
- Runs the deferred resource redraw only after the current render stack has returned.
- Tracks suppressed re-entrant callbacks through `window.s57S52SuppressedReentrantResourceCallbacks`.
- Keeps the normal S-52 WebGL path as the only successful render path; this does not
  re-enable decoded/debug geometry fallback.

Validation performed in the patch environment:

- ZIP integrity check.
