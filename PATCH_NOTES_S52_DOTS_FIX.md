# S-52 dots / point-only uploaded-cell fix

Incremental patch over the previous uploaded-cell rendering fixes.

## Problem

Some real uploaded ENC cells imported successfully, but the browser view showed the chart as dots instead of line and area geometry.

Two failure modes are addressed:

1. A decoded feature can claim `PRIM=Point` while its spatial references point at S-57 edge records (`RCNM=130`). Rendering such a feature as a point feature flattens edge geometry into dots.
2. The S-52 WebGL path can report successful draw calls even when all rendered output is point-like while the projected source frame contains lines or polygons.

## Fixes

- `S57GeometryBuilder` now corrects point/unknown primitives that reference edge vectors:
  - closed edge chains or known area object classes become area geometry;
  - open edge chains become line geometry.
- Browser S-52 rendering now falls back to the geometry renderer when S-52 output is point-only but the source frame contains line/area features.
- Added regression tests for primitive correction and point-only S-52 fallback policy.

## Expected behavior

For a cell like `US5NY1CE.000`, imported edge-backed objects should no longer be displayed only as dots. If S-52 cannot produce corresponding line/area draw output, the browser should visibly render decoded line/polygon geometry through the fallback path instead of accepting the point-only result.
