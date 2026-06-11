# OpenCPN symbology: suppress decoded debug glyph fallback

This incremental patch fixes the browser path that could still repaint the
canvas with the S-57 decoded-geometry/debug renderer after the S-52/OpenCPN path
had been selected.

## Problem fixed

Chrome snapshots showing red diamonds, plus signs, crosses, and pale repository
colors are not OpenCPN symbols. They are produced by `BrowserS57WebGlRenderer`'s
parser/debug renderer. That renderer is useful for checking decoded geometry,
but it must not be used as the normal fallback for OpenCPN/S-52 presentation.

## Changes

- The S-52 route no longer calls `renderFrame()` on failures or partial output.
- The top-level demo catch block no longer falls back to the decoded debug
  renderer when S-52 throws.
- If S-52 genuinely fails, the canvas is cleared and diagnostics are emitted
  with `s52.debug_geometry_fallback_suppressed` instead of silently drawing debug
  glyphs.
- Successful S-52 output is kept even if the current S-52 library reports only
  point/symbol draw calls for a mixed source chart.
- Mariner settings use `PaperChart` symbols and `Symbolized` boundaries instead
  of simplified/plain styles.
- Packaging and CI snapshot scripts now require the OpenCPN raster atlases
  (`rastersymbols-day.png`, `rastersymbols-dusk.png`, `rastersymbols-dark.png`)
  to be present in the served browser app.
- Snapshot tooling fails if the browser cannot fetch the OpenCPN atlas files,
  rather than producing a PNG that looks like fallback/debug symbology.

## Expected visible result

After rebuilding the demo with the S-52 resources available, channel markers and
buoys should be drawn by the OpenCPN/S-52 symbol atlas. If those resources are
missing or S-52 throws, the chart should now fail visibly/diagnostically instead
of showing the old red diamond/cross debug glyphs.
