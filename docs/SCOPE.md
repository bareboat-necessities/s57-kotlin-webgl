# Scope

`s57-kotlin-webgl` is a reusable Kotlin/JS browser library for importing, indexing, and rendering S-57 ENC charts with WebGL.

It is **not** a full chartplotter. A larger chartplotter application can be built on top later.

## In scope

- ISO8211 parsing.
- S-57 raw record decoding.
- S-57 geometry reconstruction.
- Browser-side indexing and persistent cache, initially using IndexedDB.
- Static chart rendering using the `s52-kotlin-webgl` portrayal/rendering library.
- Basic browser mouse/touch event exposure.
- Higher-level UI events:
  - clicking/touching chart objects,
  - optional center crosshair query,
  - zoom in/out gestures,
  - rotation gestures,
  - wheel/trackpad scroll directions,
  - hold and drag events.
- Render contracts for a small chart tilt.
- Render contracts for a depth-based 3D mesh.
- Diagnostics and artifact generation that can detect empty rendering or excessive fallback placeholders.

## Out of scope

- Full chartplotter application UX.
- Continuous chart quilting.
- AIS.
- NMEA 0183 / NMEA 2000.
- GPS / ownship integration.
- Route planning, waypoints, and alarms.
- Real-time navigation state.
- ECDIS certification or type approval.

## Phase 1 addition

Phase 1 establishes the event and render contracts before parsing begins. This keeps later parser/index/render work aligned with how a future chartplotter will consume the library, without moving chartplotter-only concerns into this project.


## Phase 2 addition

Phase 2 implements the reusable ISO8211 reader. It parses record structure, directory entries, raw fields, and delimiter-separated subfield chunks. It deliberately does not interpret S-57 object classes, feature records, vector topology, or ENC update rules yet.


## Phase 3 addition

Phase 3 decodes raw S-57 semantics from ISO8211 records: dataset metadata, feature/vector records, raw attributes, and feature-to-spatial references. It intentionally stops before geometry reconstruction, panning/zooming UI, quilting, AIS, or NMEA integration.


## Phase 4 geometry reconstruction

The core library reconstructs basic S-57 geometries from decoded feature/vector records: point features, multipoint soundings, line strings, simple polygon rings, feature bounds, dataset bounds, and diagnostics for unresolved spatial references. This is still not chart quilting, pan/zoom UX, AIS, or NMEA handling.
