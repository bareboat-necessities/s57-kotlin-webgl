# Phase 17 — S-57 geometry reconstruction

Phase 17 improves real NOAA ENC geometry reconstruction while keeping the existing raw/index/render boundaries intact.

## What changed

```text
- Raw vector records now preserve VRPT vector-to-vector references.
- Geometry builder expands edge endpoints from connected-node references.
- Multi-edge lines and area rings are stitched by matching endpoints.
- Area features can assemble closed polygon rings from multiple edges.
- Reversed FSPT orientation still reverses the segment before stitching.
- SG3D z values are scaled with SOMF and copied to SOUNDG VALSOU.
```

## New raw model

```text
S57VectorReference
S57RawVectorRecord.vectorReferences
```

This preserves VRPT records in decoded vector records.

## Geometry behavior

For an edge with connected-node references:

```text
start node -> edge control points -> end node
```

If the edge has no internal control points, it still becomes a valid segment from connected node to connected node.

For area features, segments are stitched into one or more closed rings. If no closed ring can be assembled, the builder emits a diagnostic and falls back to a line geometry instead of silently producing an invalid polygon.

## SOUNDG behavior

When a SOUNDG feature references SG3D coordinates, z values are scaled by SOMF and added as:

```text
VALSOU
```

A single z value becomes `S57Value.Decimal`; multiple z values become `S57Value.ListValue`.

## Tests

```text
s57-core/src/commonTest/kotlin/io/github/s57/core/geometry/Phase17GeometryBuilderTest.kt
```

Covers:

```text
- DEPARE ring assembly from edges with connected-node references.
- SOUNDG depth preservation from SG3D/SOMF into VALSOU.
```

## Still left for later phases

Phase 17 does not yet generate complete S-57 object/attribute lookup tables. That belongs to Phase 18.

Phase 17 also does not yet split SOUNDG multipoints into per-sounding S-52 features. That belongs to Phase 19.
