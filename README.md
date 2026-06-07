# s57-kotlin-webgl

Experimental Kotlin Multiplatform / Kotlin JS library for parsing, indexing, and rendering S-57 ENC chart data in the browser with WebGL.

> **Not for navigation.** This project is experimental software. It is not type-approved ECDIS, not a certified chartplotter, and must not be used for navigation or safety-critical decisions.

## Scope

This repository is intentionally smaller than a full chartplotter. It focuses on:

- ISO8211 parsing.
- S-57 record decoding.
- S-57 geometry reconstruction.
- Browser-side chart indexing.
- Static WebGL rendering through `s52-kotlin-webgl`.
- Basic mouse/touch event exposure.
- Higher-level object interaction events.
- Optional center-crosshair query contracts.
- Camera contracts for zoom, rotation, and small chart tilt.
- Future depth-mesh 3D rendering contracts.

Out of scope here:

- AIS.
- NMEA.
- GPS / ownship.
- Routes and waypoints.
- Quilting.
- Full pan/zoom chartplotter UX.
- Navigation alarms.

A larger chartplotter application can use this library later.

## Modules

- `s57-iso8211` — reusable ISO8211 reader.
- `s57-core` — S-57 data and geometry model.
- `s57-index` — browser indexing/cache contract.
- `s57-s52-adapter` — bridge to `s52-kotlin-webgl`.
- `s57-render-webgl` — static render request, event, camera, and WebGL integration contracts.
- `demo` — minimal browser demo.

## Phase checks

```bash
gradle phase0Check
gradle phase1Check
```
