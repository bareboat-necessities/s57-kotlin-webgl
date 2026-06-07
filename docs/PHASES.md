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

- ISO8211 leader, directory, field, and subfield parsing.
- Record dump diagnostics.

## Phase 3 — S-57 raw decoder

- DSID / DSSI metadata.
- Feature and vector records.
- Attribute decoding.
- Object-class counts.

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
