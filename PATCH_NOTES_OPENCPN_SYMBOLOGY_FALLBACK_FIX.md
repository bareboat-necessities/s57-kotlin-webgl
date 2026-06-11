# OpenCPN symbology fallback/overlay fix

Incremental patch over the current `s57-kotlin-webgl` baseline.

## Problem

The browser and Phase 26 PNG snapshot could show repository-local decoded-geometry glyphs instead of the OpenCPN/S-52 presentation-library output.  Those fallback glyphs are intentionally simple diagnostic shapes and colors, so channel-marker buoys/beacons could appear missing or wrong even when the S-52 renderer had produced symbol draw calls.

The regression came from the earlier “dots” fixes: a successful S-52 render was still treated as failed when line/area draw counters looked incomplete, and the whole canvas was replaced/covered by the decoded geometry fallback renderer.

## Fixes

- Successful S-52/OpenCPN WebGL output now owns the canvas whenever it produced draw calls.
- Point-only or partial line/area S-52 counter reports no longer trigger the decoded debug fallback that hides real buoy/beacon symbols.
- The renderer still falls back to decoded geometry only for hard failures: no projected features, no portrayal commands, failed WebGL, or zero draw calls.
- Partial line/area counter mismatches are now reported as structured diagnostics without repainting over S-52 symbols.
- Updated fallback policy tests so they catch future regressions that would replace a successful OpenCPN frame with debug glyphs.

## Validation note

This sandbox still does not include a Gradle wrapper or a Gradle installation, so I could not run the full Kotlin/JS Gradle build locally.  The patch is constrained to the browser S-52/fallback policy and tests, and the incremental ZIP contains only new/changed files.
