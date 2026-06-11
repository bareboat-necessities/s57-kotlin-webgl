# Phase 27 — viewport catalog, quilting, and performance plan

Phase 27 is the plan for turning the current static, imported-cell renderer into
a fast browser chart library that can ingest an ENC portfolio, choose only the
cells needed for the current view, and keep pan/zoom responsive without making
full chartplotter features part of this repository.

This phase is still **not for navigation**. It adds library-level viewport cell
selection, cache management, and render preparation improvements only. AIS,
NMEA, GPS/ownship state, route planning, alarms, and certified ECDIS behavior
remain out of scope.

## Current baseline

The project already has useful building blocks:

- S-57 base-cell bytes can be decoded and imported through the common import
  pipeline.
- Imported features can be persisted/query-indexed through the in-memory and
  browser index abstractions.
- Browser IndexedDB can persist decoded chart payloads and restore them after a
  refresh.
- The static renderer can query one or more explicit `renderCellIds`, adapt the
  queried features, project them, and build a render frame.
- Phase 24 diagnostics can time decode, index, frame preparation, and artifact
  analysis.

The missing performance layer is a portfolio catalog above decoded feature
storage. Today the viewer still tends to think in terms of files that have been
fully imported. It needs to think in terms of cheap cell metadata first, and only
parse, query, clip, and draw geometry for cells that are useful for the current
viewport.

## Goals

1. Uploading a `.zip` or directory-like file set creates a cheap, cached catalog
   of ENC base cells before the user starts panning.
2. Catalog entries include cell path, file identity, cell id, navigational
   purpose band, footprint bounds, byte count, modified time when available,
   import status, and diagnostics.
3. Footprints are read from `M_COVR` coverage metadata whenever possible instead
   of forcing a full feature/geometry decode of every cell.
4. View changes select cells by viewport and target usage band, with coarser
   cells drawn underneath finer cells to provide gap-fill quilting.
5. Parsed cell results are retained in a bounded in-memory LRU cache so revisits
   avoid disk/IndexedDB reads and S-57 decode work.
6. Render preparation clips feature geometry to a hysteresis region larger than
   the visible viewport so large coarse cells do not dominate every frame.
7. Performance diagnostics make catalog scan time, footprint extraction time,
   cache hit rate, clip cost, selected cells, loaded cells, and evictions visible.
8. The implementation remains deterministic and testable in common code, with
   browser-specific IndexedDB, zip, worker, and file-handle details isolated to
   `jsMain`.

## Non-goals

- Do not add certified navigation behavior, alarms, route planning, GPS/ownship,
  AIS, NMEA, or ECDIS-type approval claims.
- Do not make the render path depend on a native UI toolkit. The reference app
  uses Qt-style scene items, but this project should map the same concepts to the
  existing Kotlin/WebGL render-frame pipeline.
- Do not persist clipped geometry as the primary cache artifact. Persist catalog
  metadata and optionally decoded full cells; clipped output is viewport-specific
  and should be rebuilt cheaply from cached parsed data.
- Do not require every browser to expose filesystem modified times. When missing,
  use a stable fallback identity based on root id, relative path, size, and an
  optional fast content hash.

## Proposed architecture

```text
.zip / file set
  -> PortfolioCatalogScanner        cheap async scan of *.000 files
  -> CellFootprintExtractor         M_COVR-first footprint + cell-name band
  -> PortfolioCatalogStore          IndexedDB catalog cache keyed by file identity
  -> ViewportCellSelector           viewport + zoom -> ordered cell set
  -> ParsedCellFeatureCache         bounded in-memory LRU, pins visible cells
  -> ViewportClipper                full parsed cell -> viewport-margin geometry
  -> Static/WebGL render pipeline   existing adapter/projection/draw behavior
```

### Data model additions

Add a small common model package, likely under `s57-index` or
`s57-render-webgl`, with the following concepts:

```kotlin
data class EncPortfolioRoot(
    val rootId: String,
    val label: String,
    val createdAtMillis: Double,
    val scannerVersion: Int
)

data class EncFileIdentity(
    val rootId: String,
    val relativePath: String,
    val byteCount: Int,
    val modifiedTimeMillis: Double?,
    val fastHash: String? = null
)

data class EncCellCatalogEntry(
    val identity: EncFileIdentity,
    val cellId: String,
    val usageBand: Int,
    val footprint: GeoBounds?,
    val coverageSource: EncCoverageSource,
    val status: EncCatalogStatus,
    val diagnostics: List<String> = emptyList()
)
```

Band extraction is intentionally cheap: for an ENC base-cell name such as
`US5FL14M.000`, the third character (`5`) is the navigational purpose band. A
base-cell scanner should accept only `*.000` files for catalog entries and ignore
update files such as `*.001` during the initial phase.

### IndexedDB catalog schema

Create a catalog store separate from the current payload cache so a root can be
listed without loading every chart payload:

```text
database: s57-kotlin-webgl-cache
version: 2 or later
stores:
  encPayloads       existing decoded/payload rows
  encCatalogRoots   root metadata and scanner version
  encCatalogCells   one row per root + relativePath + byteCount + modifiedTime
```

Recommended keys:

- root key: `rootId`
- cell key: `rootId#relativePath#byteCount#modifiedTimeOrUnknown#fastHashOrNone`
- root invalidation: if scanner version changes, rescan the root lazily in the
  background while continuing to show valid old rows with a stale badge.

### Background scanning flow

1. User uploads a `.zip` or multiple files.
2. Browser code enumerates candidate `.000` files without decoding all features.
3. A Web Worker or coroutine-like asynchronous queue scans in small batches so
   the UI thread stays responsive.
4. For each candidate:
   - derive `cellId` and `usageBand` from the filename;
   - look up a matching cached catalog entry by identity;
   - if found, reuse footprint and diagnostics;
   - otherwise, extract footprint from `M_COVR` using the cheapest decoder path
     available and persist the catalog row;
   - if `M_COVR` is missing or malformed, fall back to dataset bounds from a
     full decode, mark the source as fallback, and emit a warning diagnostic.
5. Publish progress after every batch: scanned files, cached hits, extracted
   footprints, fallback decodes, failed cells, and elapsed time.
6. Keep catalog failures non-fatal; a single bad cell should not block the rest
   of the portfolio.

### M_COVR-first footprint extraction

The ideal extractor should stop as soon as it can build the coverage bounds:

- Decode DSID/DSSI and feature records enough to find `M_COVR` features.
- Reconstruct only the geometry referenced by `M_COVR`, not every feature in the
  cell.
- Compute a bounding box over coverage polygons/rings.
- Record whether the footprint came from `M_COVR`, a dataset-wide decoded bound,
  or a filename/metadata placeholder.

This may require adding a partial-import mode to the core import pipeline. If a
partial mode is risky, ship the same public contract first with an internal full
import fallback, then optimize the extractor without changing the catalog API.

## Viewport selection and quilting

### Target band by zoom

Add a configurable mapping from render scale/zoom to target navigational purpose
band. The initial thresholds should be conservative and easy to tune:

```text
band 1 overview      world / very far out
band 2 general       regional
band 3 coastal       approach to coast
band 4 approach      port approach
band 5 harbour       harbour scale
band 6 berthing      berth scale / most detailed
```

The selector receives visible geographic bounds, camera zoom or scale
denominator, and the catalog entries. It returns an ordered render plan rather
than only a list of cell ids.

### Selection algorithm

For each debounced pan/zoom update:

1. Compute the visible world rectangle from the current camera and viewport.
2. Compute an inner load trigger rectangle by expanding the visible rectangle by
   `0.5x` the viewport width/height in each direction.
3. Compute the clip/unload rectangle by expanding the visible rectangle by
   `1.5x` the viewport width/height in each direction.
4. Determine the target band from zoom.
5. Select every cataloged cell with `usageBand in 1..targetBand` whose footprint
   intersects the load trigger rectangle.
6. Sort selected cells band-major and then deterministically by cell id/path:
   coarser bands first, finer bands later.
7. If no selected cell intersects part or all of the viewport at or below the
   target band, include the coarsest available finer-band cell that intersects
   the viewport so the view does not go blank where only detailed data exists.
8. Keep already-loaded cells if their footprint still intersects the larger
   clip/unload rectangle. Unload cells only after they move outside this larger
   region.

### Gap-fill behavior

Rendering order is the quilting mechanism:

- Band 1 draws first, then band 2, and so on through the target band.
- Finer cells draw on top of coarser cells where they have coverage.
- Missing intermediate bands are skipped naturally; the next coarser available
  cell remains visible in gaps.
- The fallback finer-band selection is only for otherwise blank regions and
  should be reported in diagnostics so threshold tuning is visible.

For WebGL, this should be expressed as an ordered render plan with per-cell draw
layers and coverage masks/clipping metadata, not as Qt scene item stacking.

## Parsed-cell LRU cache

Add a common `ParsedCellFeatureCache` abstraction with a browser implementation
that can use memory plus optional IndexedDB decoded-payload restore.

Required behavior:

- Key entries by file identity, not only by display filename.
- Store the full parsed dataset or a render-ready plain-vector representation.
- Track approximate byte cost: points, rings, attributes retained, labels, and
  object metadata.
- Default budget: 256 MiB soft memory target and 256 parsed cells, configurable
  for tests and low-memory devices.
- Pin cells in the active render plan so visible cells are never evicted.
- Evict least-recently-used unpinned cells when the soft budget or count budget
  is exceeded.
- Emit diagnostics for hits, misses, load time, pin count, evictions, and budget
  pressure.

The first implementation can approximate memory cost deterministically in common
code; exact JS heap size is not necessary for correctness.

## Per-region clipping

The parsed cache should store full-cell geometry, independent of the viewport.
The render path should build frame geometry by clipping cached parsed features to
the clip/unload rectangle.

Planned clipping rules:

- Polygons and area rings: Sutherland-Hodgman clipping against the rectangle.
- Lines and contours: Cohen-Sutherland or Liang-Barsky segment clipping.
- Soundings and point features: rectangle containment test.
- Multipoints: filter member points to the clip region while preserving feature
  metadata for labels/hit tests.
- Empty clipped output should be counted as clipped-away, not as an import loss.

The clip region remains larger than the visible viewport. Reclip a cell only
when the current inner trigger rectangle is no longer contained by the stored
clip rectangle. This gives a full viewport-width of margin between visible pixels
and clipping edges, preventing blank slivers during panning.

## Render pipeline changes

The current render request already supports multiple explicit cell ids. Phase 27
should evolve that into a render plan:

```kotlin
data class ViewportRenderPlan(
    val visibleBounds: GeoBounds,
    val loadTriggerBounds: GeoBounds,
    val clipBounds: GeoBounds,
    val targetBand: Int,
    val layers: List<ViewportRenderLayer>,
    val diagnostics: List<String>
)

data class ViewportRenderLayer(
    val cellId: String,
    val identityKey: String,
    val usageBand: Int,
    val footprint: GeoBounds,
    val clipBounds: GeoBounds,
    val reason: ViewportCellSelectionReason
)
```

`S57StaticChartRenderer` can then keep its static behavior while accepting the
ordered layers. Internally it should query/build features layer by layer, preserve
layer order in projected features, and allow the browser WebGL renderer to draw
coarse layers before fine layers.

## Other improvements to include in the same roadmap

### Zip and file handling

- Add a single import UX for `.zip`, multiple `.000` files, and restored cached
  roots.
- Show portfolio scan progress separately from cell decode/render progress.
- Preserve relative paths inside zips so catalog cache keys are stable.
- Detect duplicate cell ids in one root and prefer the newest/largest base cell
  only after reporting a warning.

### Scale filtering and label density

- Apply `SCAMIN` and render-scale filtering before projection where possible.
- Add label-density budgets for soundings and names so fine-band cells do not
  create excessive text draw calls.
- Keep diagnostic counters for hidden-by-scale and hidden-by-label-budget objects.

### Geometry simplification

- Add optional viewport-tolerance simplification for lines and polygon rings
  after clipping and before projection.
- Preserve unsimplified geometry in the parsed cache.
- Disable simplification for safety-critical-looking point/sounding data unless
  tests prove the presentation remains stable.

### WebGL batching

- Group draw commands by layer, primitive type, style, texture/symbol atlas, and
  color token.
- Avoid rebuilding buffers when the view pans inside the existing clip rectangle;
  update transforms where possible and rebuild only when reclip is required.
- Add metrics for projected feature count, vertex count, buffer rebuild count,
  draw call count, and frame preparation time.

### Diagnostics and observability

- Extend Phase 24 metrics with catalog, selection, cache, clipping, and WebGL
  batching timers.
- Add a browser debug overlay showing selected cells, footprints, clip boxes,
  target band, loaded cells, pinned cache entries, and LRU evictions.
- Export the current render plan as JSON so performance bugs can be reproduced.

## Implementation slices

### P27.1 — catalog model and selector tests

- Add common catalog data models for roots, identities, cell entries, coverage
  sources, statuses, viewport render plans, and selection reasons.
- Add `usageBandFromEncBaseCellName` with tests for valid/invalid filenames.
- Add a pure `ViewportCellSelector` with tests for:
  - selecting bands `1..targetBand`;
  - footprint intersection;
  - deterministic band-major ordering;
  - missing intermediate bands;
  - fallback to the coarsest finer band when no at-or-below-target cell covers
    the viewport;
  - load and unload hysteresis boxes.

### P27.2 — browser catalog persistence

- Upgrade IndexedDB schema with root and cell catalog stores.
- Implement catalog list, put, stale-version detection, clear-root, and
  clear-all APIs.
- Use file path, byte count, modified time, and optional hash in cache keys.
- Add common tests for key generation and JS/browser tests for schema migration.

### P27.3 — zip/file scan worker

- Add browser zip enumeration and a scan queue that yields progress.
- Reuse cached catalog rows before touching cell bytes.
- Persist scan diagnostics and expose them in the demo panel.
- Keep the UI responsive during large uploads by batching work off the main
  render loop.

### P27.4 — M_COVR footprint extraction

- Add a partial coverage extraction API in `s57-core`.
- Implement `M_COVR`-first bounds extraction with full-decode fallback.
- Add fixtures for normal coverage, multiple coverage polygons, missing M_COVR,
  malformed coverage geometry, and fallback diagnostics.

### P27.5 — parsed-cell LRU cache

- Add common cache contracts and deterministic LRU implementation.
- Integrate browser restore from persisted payload/decoded dataset rows.
- Pin active render-plan cells and evict only unpinned entries.
- Add tests for budget enforcement, pinning, hit/miss accounting, and eviction
  ordering.

### P27.6 — viewport clipping

- Add rectangle clipping utilities for polygons, lines, points, and multipoints.
- Clip from cached full parses into render-plan clip bounds.
- Count clipped-away features separately from import/index losses.
- Add geometry tests for edge crossings, holes/interior rings, degenerate rings,
  antimeridian-adjacent bounds, and soundings on rectangle edges.

### P27.7 — render-plan integration

- Extend render requests or add a new render-plan entry point while keeping the
  current static `ChartRenderRequest` API working.
- Preserve layer order through adaptation, projection, frame construction, and
  WebGL drawing.
- Add diagnostics for selected cells, target band, fallback finer-band cells,
  skipped cells, query counts, clipped feature counts, and render cost.

### P27.8 — demo controls and debug overlay

- Add portfolio scan/restore controls.
- Show scan progress, selected cells, target band, cache stats, and render-plan
  JSON export.
- Add optional footprint/clip-box overlays for debugging performance and gaps.

### P27.9 — performance gates

- Add synthetic catalog/selector benchmarks in common/JVM tests.
- Add browser smoke tests for a zip with several cells and a scripted pan/zoom.
- Track thresholds for catalog cache hit startup, viewport selection latency,
  parsed-cache hit latency, frame preparation time, and draw call count.

## Acceptance criteria

Phase 27 is complete when:

1. Reopening a previously scanned root restores catalog entries without rereading
   all cell geometry.
2. A pan/zoom operation can produce a deterministic render plan from viewport,
   zoom, and catalog entries without decoding unrelated cells.
3. The renderer draws coarser bands under finer bands and naturally fills gaps
   with the next available coarser band.
4. Cells outside the hysteresis unload rectangle are removed from the active set,
   while cells inside it do not thrash during small pans.
5. Revisiting an unloaded-but-cached cell avoids S-57 decode work through the LRU
   parsed-cell cache unless it has been evicted.
6. Large coarse-cell geometry is clipped to a viewport-margin region before WebGL
   frame construction.
7. Catalog, selection, cache, clipping, and render metrics are visible in tests
   and in the browser demo.
8. Existing static single-cell rendering tests and demo behavior still work.

## Suggested implementation order

1. Land pure common models and selector tests first; they are the core behavior
   and do not require browser APIs.
2. Add browser catalog persistence and cached scan progress.
3. Implement M_COVR footprint extraction, initially allowing a full-decode
   fallback behind the same API.
4. Add parsed-cell LRU and wire it to existing import/restore paths.
5. Add clipping utilities and integrate them before projection.
6. Move static rendering from explicit cell ids to ordered render plans while
   preserving the old API as a compatibility wrapper.
7. Improve demo observability and add performance gates once behavior is stable.
