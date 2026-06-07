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

- Points.
- Multipoint soundings.
- Lines.
- Area rings.
- Bounding boxes.

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
