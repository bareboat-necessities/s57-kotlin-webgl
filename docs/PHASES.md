# Phases

## Phase 0 — repository skeleton

- Kotlin Multiplatform project.
- Core module boundaries.
- Browser demo shell.
- File picker.
- WebGL placeholder.
- CI.

## Phase 1 — interaction and render contracts

Adds the public browser-facing contracts that the later S-57 pipeline will render into.

Deliverables:

- Low-level pointer and wheel event models.
- High-level chart UI events:
  - click/touch object,
  - center crosshair query,
  - drag,
  - hold,
  - scroll direction,
  - zoom,
  - rotate,
  - tilt.
- Hit-test interfaces for object click/touch and center-crosshair queries.
- Camera model with zoom, rotation, and bounded tilt.
- Render request extensions for center crosshair and depth mesh.
- Depth mesh data contracts for future 3D depth rendering.
- Browser input binding for pointer and wheel events.
- Demo event log.

Acceptance:

- Demo canvas accepts pointer/wheel/touch events.
- Common tests verify click, wheel zoom, camera rotation/tilt, and depth mesh validation.
- No S-57 parsing is required yet.

## Phase 2 — ISO8211 parser

Adds the first real parser layer while staying independent from S-57 semantics.

Deliverables:

- ISO8211 leader parsing.
- Directory entry parsing.
- Field byte-range extraction.
- Delimiter-separated subfield chunks.
- Multiple records in one byte stream.
- Field lookup by tag.
- Human-readable record dump diagnostics.
- JVM command-line dump entry point.

Acceptance:

- Synthetic ISO8211 records parse in common tests.
- Record dumps show record counts, field tags, lengths, positions, and text previews.
- No S-57 DSID/feature/vector semantic decoding is required yet.

## Phase 3 — S-57 raw decoder

Adds the first S-57 semantic layer on top of ISO8211 while still avoiding geometry reconstruction.

Deliverables:

- DSID / DSSI metadata extraction.
- Feature record decoding from FRID / FOID.
- Vector record decoding from VRID.
- Raw attribute decoding from ATTF / NATF.
- Feature-to-spatial pointer preservation from FSPT.
- Small built-in object/attribute acronym lookup for common ENC classes.
- Object-class count diagnostics.
- JVM raw-dump command entry point.

Acceptance:

- Synthetic S-57-like ISO8211 records decode into dataset metadata, feature records, vector records, raw attributes, and spatial references.
- Common classes such as DEPARE, DEPCNT, SOUNDG, BOYLAT, BCNLAT, LIGHTS, WRECKS, and OBSTRN are named when their OBJL numeric codes are present.
- Unknown object/attribute codes are preserved as OBJL_### / ATTL_### instead of being dropped.
- No area/line geometry reconstruction is required yet.

## Phase 4 — geometry reconstruction

Turns Phase 3 raw feature/vector records into usable chart geometries.

Deliverables:

- SG2D / SG3D raw coordinate preservation.
- COMF-scaled lon/lat conversion.
- Point and multipoint feature geometries.
- LineString reconstruction from FSPT edge/vector references.
- Basic area ring / polygon reconstruction.
- Orientation handling for reversed references.
- Dataset and feature bounding boxes.
- Geometry diagnostics for missing vectors and empty features.

Acceptance:

- DEPARE-like features can produce polygons.
- DEPCNT-like features can produce line strings.
- SOUNDG / buoy-like features can produce points or multipoints.
- Missing vector references are reported without crashing the decode pipeline.

## Phase 5 — IndexedDB storage and indexing

- Cells store.
- Features store.
- Geometry store.
- Spatial bins.
- View/bounds query.

## Phase 6 — S-52 adapter

- Convert S-57 decoded features into `s52-kotlin-webgl` portrayal features.
- Produce portrayal transcripts.

## Phase 7 — static WebGL chart render

- Query indexed features for one fixed render request.
- Submit S-52 draw commands to WebGL.
- Render a recognizable static chart image.

## Phase 8 — rendered diagnostics

- Rendered PNG/SVG snapshots.
- Fallback-placeholder detection.
- Empty-render detection.


## Phase 5 — browser indexing

Goal: persist decoded chart data and query it by geographic bounds without implementing full chartplotter UX.

Deliverables:

- common storage/index contracts
- deterministic in-memory store for tests and JVM diagnostics
- fixed-grid spatial bin index
- `S57FeatureQuery` with bbox, object-class filter, and limit
- browser IndexedDB schema boundary with stores for cells, features, geometries, and spatial bins

Out of scope:

- chart quilting
- live pan/zoom controls
- AIS / NMEA / ownship
- route planning

Acceptance:

- importing an `S57Dataset` creates cell metadata, stored features, and spatial bins
- bbox queries return only intersecting features
- object-class filters and limits are honored
- IndexedDB schema opener is present in `jsMain`

## Phase 7 — static WebGL chart render

Adds the first complete static rendering pipeline: query indexed features, adapt
through the S-52 bridge, project lon/lat geometry into a fixed screen, perform
basic hit testing, and draw a static WebGL frame. This is still not a full
chartplotter: continuous pan/zoom UX, quilting, AIS, NMEA, ownship, alarms, and
route planning remain out of scope.

Acceptance:

- `S57StaticChartRenderer` queries the Phase 5 index by cell and bounds.
- Projected features expose screen-space geometry and hit-test bounds.
- Center-crosshair hit tests work on the prepared static frame.
- Browser WebGL shell can draw points, lines, polygon fills/outlines, and the
  optional center crosshair.
- Gradle repository declarations stay in `settings.gradle.kts` so CI works with
  `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.


## Phase 8 — rendered artifact diagnostics

Adds renderer-independent artifact diagnostics for prepared static chart frames:
feature counts, visible geometry counts, empty geometry counts, center-crosshair hit counts,
depth-mesh counts, fallback-placeholder counts, and simple SVG snapshot export for CI/debugging.
This phase also keeps Gradle repository mode compatible with Kotlin/JS Node setup.

## Phase 9 — high-level engine facade

Adds a small reusable engine facade around the existing lower-level pieces. The
facade imports decoded `S57Dataset` values into the index, lists stored cells,
prepares static chart frames, exposes center-crosshair hit queries, and returns
rendered-artifact diagnostics/snapshots.

This phase also fixes the S-52 dependency boundary: the common/JS adapter now
emits S-52-shaped intermediate portrayal features without importing `io.github.s52`
classes directly. The v0.3.0 S-52 release artifacts are still downloaded and
checksum-verified in CI for JVM/local bridge work, but the browser build no
longer depends on unavailable JS S-52 klib artifacts.

Acceptance:

- `S57WebGlEngine.importDataset` imports decoded datasets through the Phase 5 index.
- `S57WebGlEngine.render` returns a static frame plus `RenderedArtifactReport`.
- Center-crosshair hit queries can be performed through the facade.
- Common/JS source sets compile without direct S-52 imports.


## Phase 10 — end-to-end import pipeline

Adds the first reusable import pipeline from S-57/ENC bytes to a decoded geometry dataset ready for the browser index.  The browser boundary can read a selected `File` into bytes and pass it to `S57WebGlEngine.importS57Bytes(...)`.  This remains a static chart engine layer: no quilting, AIS, NMEA, ownship, routing, or full chartplotter UX.
