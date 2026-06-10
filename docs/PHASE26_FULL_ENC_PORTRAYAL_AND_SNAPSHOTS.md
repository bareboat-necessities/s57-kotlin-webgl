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


## Completion execution plan

The following backlog turns the Phase 26 intent above into implementation slices
that can be landed independently while keeping the renderer usable after every
merge.  Each slice must leave the demo able to import the sample dataset and must
keep diagnostics visible rather than replacing them with silent fallbacks.

### P26.1 — structured diagnostic contract

Goal: define the common report shape before changing render behavior.

Tasks:

- Add a `RenderPipelineDiagnostic`/`RenderPipelineReport` contract in the render
  common source set with stable enums for stage and severity plus string codes
  for forward compatibility.
- Include optional source metadata fields for cell id, feature id, record id,
  object class, primitive, geometry type, attribute names, S-52 asset name,
  color token, palette, WebGL command, and fallback reason.
- Add report combinators for merging import, geometry, adapter, S-52, WebGL, and
  artifact diagnostics without losing original messages.
- Add deterministic JSON and plain-text exporters.  JSON is the machine-readable
  source of truth; the demo status panel and CI summary are derived views.
- Convert the existing Phase 16/artifact diagnostics into this report as the
  first producer, then add unit tests for aggregation by stage, severity, code,
  object class, and primitive.

Definition of done:

- Every existing diagnostic summary still appears in plain text.
- New JSON export contains `schemaVersion`, `cellId`, `palette`, `scale`,
  counters, diagnostics, and source-stage totals.
- Warning/error diagnostics can be routed to browser console logging from the
  shared report without duplicating classification logic.

### P26.2 — decode, geometry, and adapter accounting

Goal: prove that no feature disappears between S-57 decode and S-52 adaptation
without either being counted or receiving a diagnostic.

Tasks:

- Preserve raw S-57 identifiers from ISO8211 records into `S57Feature`, indexed
  records, projected features, adapter output, and rendered-frame summaries.
- Add decode/index/adapt accounting counters: decoded, indexed, queried,
  adapted, skipped, deferred, empty geometry, geometry diagnostic, and unsupported
  primitive counts.
- Emit structured diagnostics for unresolved FSPT references, suspicious ring
  topology, reversed edge orientation, unsupported collection/meta objects,
  unsupported primitives, dropped attributes, and empty geometries.
- Create fixture tests that assert
  `decoded = indexed + explicitlySkippedOrDeferred` for imported cells and
  `queried = adapted + explicitlySkippedOrDeferred` for render requests.
- Extend object-class coverage output so unknown `OBJL_###` and `ATTL_###`
  values remain visible in reports rather than becoming anonymous fallbacks.

Definition of done:

- A real ENC import prints per-object-class decode and geometry counts.
- Adapter diagnostics include enough metadata to locate the original S-57 record.
- Unit tests fail on silent drops at decode/index/adapt boundaries.

### P26.3 — S-52 symbology and color audit

Goal: make missing symbology and wrong colors observable before visual review.

Tasks:

- Inventory browser-available S-52 lookup tables, color tables, symbols, line
  styles, patterns, text rules, conditional symbology procedures, and supported
  draw-command variants at startup.
- Normalize S-57 attributes according to catalogue type information before
  handing them to the S-52 bridge; log every value that cannot be converted.
- Emit diagnostics for unsupported object classes, missing lookup rules,
  conditional symbology fallbacks, unsupported symbols/patterns/line styles/text,
  missing color tokens, and fallback colors.
- Add counters for `portrayed`, `symbolized`, `drawn`, `fallbackDrawn`,
  `hiddenByScale`, `clipped`, `missingSymbol`, `missingPattern`,
  `missingLineStyle`, `missingTextRule`, `missingColorToken`, and
  `fallbackColor`.
- Keep debug fallback rendering visually distinct from real S-52 portrayal and
  include it in reports as fallback output, not as successful portrayal.

Definition of done:

- Selecting day/dusk/night palettes reports the palette, color token, resolved
  RGB value, and fallback RGB when used.
- Missing S-52 assets appear in JSON, status text, and browser console warnings.
- Adapter and browser bridge tests cover at least common DEPARE, DEPCNT, SOUNDG,
  LIGHTS, BOYLAT/BCNLAT, WRECKS, and OBSTRN paths.

### P26.4 — demo diagnostics and exports

Goal: make the browser demo an inspection tool for imported real cells.

Tasks:

- Replace the single status string with a diagnostics panel that keeps import,
  cache, render, and S-52 warnings visible until the user clears them.
- Add filters by severity, stage, object class, and diagnostic code.
- Add coverage counters near the canvas for decode/index/query/adapt/portray/
  draw/fallback/hidden/clipped/empty/missing/error totals.
- Add download buttons for the active diagnostics JSON, canvas-only PNG, and
  optional full-page screenshot when the harness supports it.
- Expose a minimal browser-test hook, such as `window.s57Phase26Report()`, that
  returns the latest report and `window.s57Phase26RenderReady` or an equivalent
  promise/state flag for CI.

Definition of done:

- Import failures and render warnings survive cell and palette changes.
- Downloaded JSON and PNG are non-empty and identify the active cell id, palette,
  scale, and render timestamp.
- The existing bundled NOAA demo still auto-loads when present.

### P26.5 — headless PNG/JSON snapshot harness

Goal: create build artifacts that let every pull request inspect actual rendered
output from a public ENC cell.

Tasks:

- Add `.github/scripts/download-first-enc-cell.sh` to try a short ordered list of
  public NOAA ENC ZIP URLs, extract the first `.000`, and write metadata about
  the selected source URL and cell file.
- Add a local snapshot harness under `tools/ci-render-snapshot/` that serves the
  Kotlin/JS demo output, imports the downloaded `.000` through the same browser
  path as the demo, waits for the Phase 26 ready hook, captures `render.png`,
  writes `diagnostics.json`, and writes `summary.txt`.
- Prefer a canvas-only PNG for stable visual comparison.  Optionally add a
  full-page screenshot later as a secondary artifact for debugging UI regressions.
- Make the harness fail on missing ENC download, import crash, render crash,
  empty PNG, missing diagnostics JSON, or malformed JSON.
- Initially warn, not fail, on nonzero missing-symbol, missing-color, fallback,
  or unknown-object counts until coverage thresholds are baselined.

Definition of done:

- A local command can produce `build/ci-enc-snapshot/render.png`,
  `diagnostics.json`, and `summary.txt` from a downloaded cell.
- The summary includes selected URL, extracted ENC file, cell id, feature counts,
  palette, image dimensions, warning count, error count, and fallback counters.

### P26.6 — GitHub Actions artifact upload and quality gates

Goal: publish the same visual smoke artifacts for every build and gradually turn
coverage regressions into failures.

Tasks:

- Run the snapshot harness after the normal Kotlin/JVM/JS build and after the
  demo distribution is available.
- Upload `build/ci-enc-snapshot/render.png`, `diagnostics.json`, and
  `summary.txt` as one artifact named `enc-render-snapshot` so reviewers can
  download a complete inspection bundle.
- Keep the current runnable demo ZIP artifact for manual reproduction.
- Add a checked-in threshold file for corpus cells with initial warning-only
  limits for missing symbols/colors/fallbacks/unknown objects.
- Promote threshold violations from warnings to failures after the diagnostic
  spine and S-52 asset inventory have stabilized.

Definition of done:

- CI artifacts contain both the runnable demo ZIP and the per-build ENC render
  snapshot bundle.
- CI fails only on hard download/import/render/artifact errors at first.
- A later tightening PR can update thresholds without changing the harness
  contract.

## Resolved implementation decisions for the first pass

- **Preferred public-cell source:** use NOAA ENC ZIP URLs because the existing CI
  already downloads NOAA data for the demo artifact.  Keep the URL list ordered
  and overridable through an environment variable so outages or cell renames do
  not require code changes.
- **Snapshot type:** capture a canvas-only PNG first for deterministic render
  inspection; add full-page screenshots later only as supplemental UI debugging
  artifacts.
- **First-pass failure policy:** fail hard only on download, import, render,
  empty PNG, missing JSON, or malformed JSON.  Treat missing symbols, missing
  colors, unknown objects, and fallback draws as warnings until baseline reports
  are stable.
- **Report ownership:** JSON diagnostics are canonical.  Status text, console
  messages, CI summaries, and future thresholds are derived from the same report.
- **Scope guard:** Phase 26 remains static ENC rendering and diagnostics only;
  no AIS, NMEA, ownship, route planning, navigation alarms, route monitoring, or
  certified ECDIS/chartplotter workflow is added.

## Current implementation status

The first Phase 26 implementation slice is now wired end-to-end:

- P26.1 structured diagnostics live in the render common source set with JSON
  and plain-text exporters plus aggregation tests.
- The demo keeps the latest diagnostics report, exposes
  `window.s57Phase26ReportJson()` / `window.s57Phase26Report()`, and offers
  diagnostics JSON plus canvas PNG downloads.
- P26.5 adds a NOAA-first download script and Playwright snapshot harness that
  produces `build/ci-enc-snapshot/render.png`, `diagnostics.json`, and
  `summary.txt`.
- P26.6 runs that harness in CI and uploads a single `enc-render-snapshot`
  artifact beside the runnable NOAA demo ZIP.
- Initial snapshot thresholds are checked in as warning-only limits so missing
  symbols/colors/fallbacks stay visible without blocking stabilization work.

## Next implementation checkpoint

The next coding checkpoint should deepen P26.2 and P26.3 coverage now that the
report and snapshot contracts are available:

1. Preserve raw S-57 record identifiers through decoded, indexed, projected,
   and adapted features.
2. Emit per-feature structured diagnostics for unresolved spatial
   relationships, suspicious topology, dropped attributes, unsupported
   primitives, and empty geometries.
3. Expand S-52 asset/color diagnostics from aggregate fallback counts into
   asset-specific warnings with palette and RGB metadata.
4. Add corpus fixtures that assert decode/index/adapt accounting never drops a
   feature silently.
5. Promote warning-only thresholds after the public-cell baselines stabilize.

This sequence builds on the stable Phase 26 report and CI artifact contract while
keeping missing-symbol/color work visible in every build.

## Remaining open questions before implementation

- What exact NOAA URL order should be checked in for the first snapshot job, and
  which cells best exercise coastline, depth areas, soundings, lights, buoys,
  beacons, restricted areas, bridges, cables, and metadata objects?
- Which threshold file format should be used for corpus quality gates: JSON for
  Kotlin/JS-native parsing, or YAML for easier human editing?
- What baseline warning limits are acceptable after the first corpus reports are
  generated: all decoded objects adapted, all adapted objects portrayed, or all
  portrayed objects drawn without fallback?
- Should the later full-page screenshot artifact include the diagnostics panel
  state, or should UI evidence stay separate from render-quality artifacts?
