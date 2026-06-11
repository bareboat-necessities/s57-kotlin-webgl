# OpenCPN symbology: raster atlas + blue canvas fix

This incremental patch fixes the follow-up failure where suppressing the decoded
S-57 debug fallback exposed a blank/blue canvas. The blank canvas was caused by
selecting S-52/OpenCPN portrayal while the OpenCPN raster symbol atlas was not
present in the served browser app, or was still loading asynchronously.

## Problem fixed

The browser S-52 renderer expects OpenCPN raster atlases at:

- `s52/opencpn/rastersymbols-day.png`
- `s52/opencpn/rastersymbols-dusk.png`
- `s52/opencpn/rastersymbols-dark.png`

Without those files, channel marks, buoys, beacons, and many other point symbols
cannot be drawn from the atlas. The previous no-debug-fallback patch correctly
stopped the red decoded-geometry renderer, but that made the missing-resource
failure show up as an empty chart.

## Changes

- Bundles the OpenCPN raster atlas PNGs under
  `demo/src/jsMain/s52RuntimeResources/s52/opencpn/` so the demo can serve them
  without relying on an adjacent checkout.
- Adds a Gradle resource preparation task that copies the bundled atlas into the
  processed Kotlin/JS browser resources. It can still use `S52_SOURCE_DIR`,
  `-Ps52SourceDir=...`, or download the matching release files if needed.
- CI/demo packaging scripts now look in the bundled runtime-resource directory
  before external S-52 checkout locations.
- Snapshot generation waits for the asynchronous S-52 atlas-ready redraw when
  raster commands are present, instead of capturing the first empty frame.
- The fallback policy no longer treats `drawCalls=0` as a hard failure when S-52
  produced raster-backed commands and the atlas load/redraw is still pending.
- Non-sounding `MultiPoint` features are split into point features before S-52
  portrayal so point symbols can render as atlas symbols instead of being lost.
- Diagnostics now include `s52RasterCommands` and browser globals for the initial
  and atlas-ready S-52 draw-call counts.

## Expected visible result

After a clean rebuild, the Chrome snapshot should no longer show the old red
fallback/debug glyphs, and it should not go to an empty blue canvas. Channel
markers, buoys, and beacons should be drawn through the OpenCPN/S-52 raster atlas
when the S-52 library maps their S-57 objects to point-symbol commands.
