# Phase 26 — full ENC portrayal diagnostics and CI PNG snapshots plan

Phase 26 is the hardening plan for moving the demo from “sometimes renders a
real ENC cell” to a repeatable renderer that can ingest arbitrary S-57 ENC base
cells and make every missing symbol, color, object, or geometry failure visible
in local logs and CI artifacts.

This is still **not for navigation**. The goal is complete diagnostic coverage
and visual regression confidence for engineering work, not certified ECDIS
behavior.

## Problem statement

Current real-cell rendering can fail silently in several places:

- S-57 object classes and attributes can decode to fallback names or values.
- Geometry reconstruction can drop relationships, malformed rings, multipoint
  soundings, or collection members without a user-facing error trail.
- The S-57 to S-52 adapter can skip unsupported geometry and attribute shapes.
- The browser S-52 bridge can fall back when a symbol, line style, area pattern,
  text rule, or color table entry is unavailable.
- The demo status panel only shows a small subset of diagnostics after render,
  so a wrong-color or missing-symbol render can still look like a success.
- CI uploads a runnable demo ZIP, but it does not create a PNG snapshot from a
  downloaded real ENC file for per-build visual inspection.

## Success criteria

A Phase 26 build is successful when all of the following are true:

1. Any valid S-57 ENC base cell that the project can read produces a render
   report with explicit counts for decoded, indexed, adapted, portrayed,
   symbolized, drawn, clipped, hidden-by-scale, and skipped objects.
2. Every skipped or degraded feature has a diagnostic containing at least:
   source stage, severity, cell id, record id or feature id, object class,
   primitive, geometry type, attributes involved, and a concise reason.
3. Missing S-52 assets are never silent: absent color tokens, symbol names, line
   styles, area patterns, text instructions, or lookup-table rules appear in the
   report and in the browser console/status panel.
4. Palette/color selection is deterministic and audited: the selected S-52 color
   table, token resolution, RGB output, and fallback color are visible in the
   report.
5. The demo exposes an exportable JSON diagnostics report alongside the visual
   canvas so failures can be attached to bug reports.
6. GitHub Actions downloads the first usable public ENC cell, renders it in a
   browser/WebGL environment, captures a PNG snapshot, uploads the PNG and JSON
   diagnostics as build artifacts, and fails only on hard import/render errors
   or configured coverage thresholds.
7. The renderer remains static-library scoped: no AIS, NMEA, ownship, route
   planning, navigation alarms, or certified chartplotter workflow is added.

## Workstream A — diagnostic spine

Create one structured diagnostic model that flows through import, indexing,
adaptation, portrayal, and WebGL drawing.

Planned changes:

- Add a common `RenderPipelineDiagnostic` model with stage, severity, stable
  code, source location, object metadata, and optional WebGL/S-52 metadata.
- Convert existing raw decoder, geometry builder, adapter, artifact, performance,
  and browser S-52 summary messages into this common shape without losing their
  existing human-readable text.
- Add aggregate counters by stage, severity, object class, primitive, and
  diagnostic code.
- Add JSON export for the full report and plain-text export for the demo panel.
- Ensure browser paths call `console.warn`/`console.error` for warning/error
  diagnostics while keeping the structured report as the source of truth.

Acceptance tests:

- Unit tests verify every pipeline stage can emit structured diagnostics.
- Golden tests verify no existing diagnostic text disappears from plain-text
  summaries.
- Browser tests verify warnings and errors are reflected in the status panel.

## Workstream B — S-57 decode and geometry completeness

Make arbitrary ENC cells observable before they reach S-52.

Planned changes:

- Expand the generated S-57 object and attribute catalogue to the full available
  public catalogue instead of the current partial generated-style table.
- Preserve raw S-57 record identifiers and object metadata through decoded
  `S57Feature`, index records, projected features, and adapter output.
- Audit feature-to-spatial relationships (`FSPT`, vector record references,
  orientation, usage, and ring membership) and report every unresolved or
  suspicious relationship.
- Add explicit support or explicit diagnostics for multi-polygons, interior
  rings, collection objects, meta objects, soundings, text placement candidates,
  and scale minimum filtering.
- Build a small corpus of public ENC cells that exercise coastline, depth areas,
  soundings, lights, buoys, beacons, restricted areas, bridges, cables, and
  metadata coverage.

Acceptance tests:

- Fixture tests assert zero silent feature drops: decoded feature count equals
  indexed plus explicitly skipped/deferred counts.
- Geometry tests cover multipoint soundings, edge orientation, closed rings,
  holes, multi-polygons, and missing-vector diagnostics.
- Corpus smoke tests print per-object-class coverage and fail on unexpected
  unknown catalogue codes.

## Workstream C — S-52 portrayal and symbology coverage

Make missing symbology and wrong colors first-class failures.

Planned changes:

- Inventory the S-52 release assets available to the browser build: lookup
  tables, conditional symbology procedures, color tables, symbols, line styles,
  patterns, text rules, and draw-command variants.
- Add an adapter compatibility matrix from S-57 object/attribute values to the
  S-52 feature/value types expected by the companion S-52 renderer.
- Normalize numeric, list, enumerated, and text attributes according to the S-57
  catalogue instead of ad hoc attribute-name sets.
- Emit diagnostics for every unsupported object class, attribute conversion,
  geometry primitive, conditional procedure, or S-52 draw command.
- Add color audit logging for palette name, color token, resolved RGB, and
  fallback RGB. Wrong or missing color tokens should increment a dedicated
  `missingColorTokenCount`/`fallbackColorCount` counter.
- Add deterministic fallback rendering only as a debug layer, visually distinct
  from real S-52 portrayal and counted separately.

Acceptance tests:

- Adapter tests cover common ENC objects that currently disappear or render with
  wrong styles.
- Palette tests validate known S-52 color tokens resolve to expected RGB values
  for day/dusk/night tables.
- Browser S-52 bridge tests verify unsupported symbols, patterns, line styles,
  text rules, and colors produce warnings in the structured report.

## Workstream D — demo observability

Turn the browser demo into a useful failure collector.

Planned changes:

- Add a diagnostics panel with filters by severity, stage, object class, and
  diagnostic code.
- Add “download diagnostics JSON” and “download PNG snapshot” buttons.
- Show coverage counters near the canvas: decoded, indexed, queried, adapted,
  portrayed, drawn, fallback-drawn, hidden-by-scale, clipped, empty geometry,
  missing symbols, missing colors, and errors.
- Preserve import and render failures across cell changes instead of replacing
  the status text with the latest render only.
- Add an optional debug overlay for fallback features, feature ids, and object
  class labels.

Acceptance tests:

- Kotlin/JS browser tests verify failed imports and render warnings remain
  visible after switching cells.
- Snapshot export tests verify PNG/JSON downloads are non-empty and include the
  active cell id and palette.

## Workstream E — GitHub Actions PNG snapshot artifact

Add a visual smoke job that downloads a public ENC cell and uploads render
artifacts for every build.

Planned implementation:

1. Add a script such as `.github/scripts/download-first-enc-cell.sh` that tries a
   short ordered list of public NOAA ENC URLs and exits after the first `.000`
   file that downloads and imports successfully.
2. Add a headless browser harness, for example `demo/src/jsTest` or a Playwright
   script under `tools/ci-render-snapshot`, that:
   - builds the Kotlin/JS demo,
   - serves the static demo output locally,
   - imports the downloaded `.000` file through the same browser file-import path
     used by the demo,
   - selects the default S-52 palette,
   - waits until rendering and diagnostics are complete,
   - captures `canvas.toDataURL("image/png")` or a Playwright page screenshot,
   - writes `build/ci-enc-snapshot/render.png`, `diagnostics.json`, and a small
     `summary.txt`.
3. Add a GitHub Actions step after the normal build that runs the snapshot
   harness with the same S-52 Maven/source release inputs as the demo build.
4. Upload `build/ci-enc-snapshot/*` using `actions/upload-artifact@v4` with
   `if-no-files-found: error`.
5. Start with warning thresholds for visual coverage, then tighten to failures
   once the corpus is stable.

Initial artifact names:

- `enc-render-snapshot-png`
- `enc-render-diagnostics-json`
- `enc-render-summary`

CI failure policy:

- Fail immediately when no ENC cell downloads, import crashes, render crashes,
  the PNG is empty, or diagnostics JSON is missing.
- Warn, but do not initially fail, when fallback symbol/color counts are nonzero.
- Later fail on configured maximums for unknown objects, missing symbols,
  missing colors, fallback draws, and silent skips.

## Workstream F — regression corpus and quality gates

Use real cells and small fixtures to prevent backsliding.

Planned changes:

- Keep tiny synthetic fixtures in-repo for deterministic unit tests.
- Download larger public ENC cells in CI instead of committing them.
- Store expected coverage thresholds per corpus cell in a small YAML/JSON file.
- Record PNG snapshots as CI artifacts, not committed binary goldens at first.
- Add optional local commands to render the corpus and compare diagnostics across
  branches.

Candidate staged gates:

1. `phase26DiagnosticsCheck` — structured diagnostic model and exporters.
2. `phase26GeometryCoverageCheck` — decode/index/adapt accounting tests.
3. `phase26S52CoverageCheck` — S-52 asset/color/symbol diagnostics tests.
4. `phase26BrowserSnapshotCheck` — local headless PNG/JSON snapshot generation.
5. GitHub Actions artifact upload — PNG and diagnostics for one public ENC cell.

## Suggested implementation order

1. Land the diagnostic model and JSON/plain-text exporters.
2. Thread diagnostics through the existing import, geometry, adapter, and render
   reports.
3. Add S-52 asset/color/symbol fallback counters in the browser bridge.
4. Update the demo panel and add manual JSON/PNG export buttons.
5. Create the headless snapshot harness and CI artifact upload.
6. Expand the S-57 catalogue and geometry coverage tests.
7. Add a public-cell corpus and tighten warning thresholds into failure gates.

## Open questions before implementation

- Which NOAA/public ENC URLs should be preferred for the first CI cell, and do
  they have stable availability and redistribution terms suitable for build-time
  download?
- Should the PNG snapshot be canvas-only, full-page screenshot, or both?
- Should missing S-52 assets be treated as warnings until the S-52 dependency is
  upgraded again, or should any missing asset fail CI immediately?
- What is the minimum acceptable first-pass coverage threshold for real cells:
  all decoded objects adapted, all adapted objects portrayed, or all portrayed
  objects drawn without fallback?
