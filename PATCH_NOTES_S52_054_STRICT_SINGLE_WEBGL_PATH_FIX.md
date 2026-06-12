# S-52 0.5.4 strict single WebGL path fix

This incremental patch removes the extra browser-side rendering paths that could
make a chart object look like it was drawn twice.

Changes:

- S-52 chart rendering now uses one successful rendering path only:
  `io.github.s52.render.webgl.WebGlS52Renderer`.
- Removed the active vector-label overlay path. Labels are no longer drawn by a
  second custom browser overlay after the S-52 renderer finishes.
- Kept `BrowserS52WebGlVectorLabelOverlay.kt` as an empty tombstone so older
  incremental worktrees overwrite the previous implementation cleanly.
- Kept `BrowserS52CanvasCommandFallback.kt` as an empty tombstone. No Canvas2D
  S-52 command fallback is allowed.
- Legacy S-57 WebGL helper paths now request `webgl2` only; they no longer fall
  back to a `webgl` context on the chart canvas.
- Browser S-52 display planning now only filters the command stream before the
  single WebGL render:
  - suppress repeated raster area pattern tiles that create rounded-frame clutter;
  - keep only one `PointSymbol` command per S-52 feature id, preferring the
    highest-priority non-placeholder symbol.
- Added diagnostics through `s52.strict_single_webgl_display_plan` with counts
  for suppressed pattern tiles and duplicate point symbols.

No decoded-geometry fallback, Canvas2D fallback, or secondary label overlay is
used by the S-52 success path.
