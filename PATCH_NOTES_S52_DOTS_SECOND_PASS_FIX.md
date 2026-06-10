# S-52 dots second-pass fix

This incremental patch addresses the case where the S-52 browser renderer reports draw calls but the visible chart still collapses to dot/marker output.

## Changes

- Adds a decoded-ENC geometry overlay after successful S-52 rendering.
  - Runs after the initial S-52 draw.
  - Also runs inside the S-52 resource-ready callback so late symbol/texture reloads cannot wipe the overlay back to dots.
  - Draws decoded line strings and polygon outlines/fills directly from the S-57 projected frame.
  - Draws simple non-sounding point glyphs instead of plain WebGL point dots.
- Strengthens the plain fallback renderer so point features are no longer rendered as only square point dots.
- Extends geometry reconstruction so obvious area/line object classes decoded as `Point` are corrected when their referenced geometry has enough vertices.
  - Example: `DEPARE` with 3+ referenced points becomes a polygon.
  - Example: `DEPCNT` with 2+ referenced points becomes a line string.
- Adds common policy tests for the decoded-geometry overlay decision.
- Adds core geometry regression coverage for object-class-based primitive correction.

## Why this is needed

The previous patch only switched to fallback when S-52 reported zero draw calls or point-only commands. In the browser, the S-52 path can still visually degrade to dots even when it reports line/area command or draw counts. This patch makes decoded S-57 geometry visible independently of those S-52 renderer counters.

## Apply order

Apply after:

1. `s57-nonrendering-objects-incremental-fix.zip`
2. `s57-browser-file-importer-compile-fix-incremental.zip`
3. `s57-uploaded-cell-render-fix-incremental.zip`
4. `s57-s52-dots-fix-incremental.zip`

Then apply this zip over the tree.

## Validation performed here

- Compiled the changed common/core/render-policy sources locally with `kotlinc`.
- Ran a JVM smoke test confirming a `DEPARE` decoded as `Point` becomes `Polygon`, and `DEPCNT` decoded as `Point` becomes `LineString`.
- Verified zip integrity with `unzip -t`.

Full Gradle/JS build was not run in this sandbox because the uploaded baseline has no Gradle wrapper and Gradle is not installed.
