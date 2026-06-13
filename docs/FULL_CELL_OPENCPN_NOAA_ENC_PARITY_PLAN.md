# Full Cell Rendering, OpenCPN Parity, and NOAA ENC Coverage Plan

This plan tracks the remaining work to render ENC cells fully enough that the
browser viewer displays every renderable object currently visible in OpenCPN for
public NOAA ENCs, while preserving the project safety boundary: this renderer is
for inspection and development only, not for navigation.

## Goals

1. **Full-cell import:** load complete NOAA `.000` base cells and update files
   without dropping valid S-57 features during ISO 8211 decode, feature assembly,
   geometry construction, or S-52 adaptation.
2. **OpenCPN parity:** portray each renderable S-57 object class, primitive, and
   attribute combination with the same S-52/OpenCPN display intent whenever the
   bundled `s52-kotlin-webgl` profile can express it.
3. **Observable gaps:** report every remaining unsupported object class,
   primitive, attribute, conditional-symbology failure, missing color, missing
   symbol, and missing pattern as structured diagnostics instead of silent loss.
4. **NOAA ENC confidence:** keep a rotating corpus of representative NOAA harbor,
   approach, coastal, and general cells in automated visual and diagnostic smoke
   coverage.

## Phased implementation

### Phase A — Baseline corpus and parity harness

- Select a small but diverse NOAA corpus covering harbor, approach, coastal, and
  general usage bands, including cells that exercise dense soundings, land
  regions, restricted areas, aids to navigation, bridges, cables, pipelines,
  dredged areas, obstructions, wrecks, quality metadata, and publication/coverage
  metadata.
- Add a repeatable headless render harness that imports each cell through the
  browser code path, exports the canvas PNG, diagnostics JSON, and render summary,
  and stores them as CI artifacts.
- Capture matching OpenCPN screenshots for the same viewport, scale, palette,
  and display-category settings to serve as human-review references.

### Phase B — Loss accounting before drawing changes

- Add counters for decoded records, assembled features, geometry-bearing
  features, S-52-adapted features, portrayed commands, WebGL draw calls, and
  suppressed commands per cell.
- Gate regressions on hard failures first: import crashes, render crashes, empty
  frames for non-empty cells, and unexpected increases in warning diagnostics.
- Keep metadata-only objects such as `M_NPUB` out of visible area fills so
  publication coverage polygons cannot masquerade as land.

### Phase C — Object-class and primitive coverage

- Triage unsupported NOAA object classes by frequency and visual importance.
- Prefer first-class S-52/OpenCPN portrayal support. Use local compatibility
  aliases only when the OpenCPN display intent is materially equivalent and the
  alias cannot mislead users.
- Add focused tests for every newly supported class/primitive pair, including a
  negative test for each metadata-only class that must remain non-portrayed.

### Phase D — Attribute and conditional-symbology parity

- Map NOAA attributes that are valid S-57 but absent from the bundled S-52 typed
  catalogue, then decide whether each should be passed through, normalized, or
  intentionally diagnosed.
- Expand conditional-symbology resilience so one bad feature cannot blank a full
  frame, while ensuring skipped features are isolated with feature id, object
  class, primitive, geometry type, and exception details.
- Add golden unit tests for text labels, light descriptions, sounding values,
  depth-area styling, restricted-area symbols, and scale-dependent `SCAMIN` /
  `SCAMAX` behavior.

### Phase E — Presentation resources and WebGL completeness

- Inventory every S-52 command produced by the corpus and verify that the WebGL
  backend can draw its symbol, line style, pattern, text, or sounding form.
- Replace temporary raster-resource suppression with vector, atlas, or supported
  WebGL paths where possible, and retain diagnostics for resources that cannot
  yet be drawn.
- Add screenshot tests that flag obvious land/sea inversion, missing aids to
  navigation, missing labels, missing soundings, and all-blue/all-land frames.

### Phase F — CI gates and release criteria

- Publish per-cell render summaries in CI and trend the unsupported/skipped
  counts over time.
- Require no hard failures across the selected NOAA corpus before calling the
  renderer “full-cell capable.”
- Require documented explanations for every remaining non-zero unsupported or
  intentionally skipped diagnostic before claiming OpenCPN parity for a cell.

## Definition of done

A NOAA ENC cell is considered fully renderable when the browser imports it
without loss-causing errors, produces a non-empty frame at representative
viewports/scales, emits no unexplained warning diagnostics, and has a human
reviewed PNG that matches OpenCPN’s visible content closely enough for the same
palette and display settings.
