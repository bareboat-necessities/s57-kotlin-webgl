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
gradle phase2Check
gradle phase3Check
gradle phase4Check
```


## Phase 2 status

Phase 2 adds the first real data parser layer: `s57-iso8211` now reads ISO/IEC 8211 record leaders, directory entries, field byte ranges, delimiter-separated subfield chunks, and record dump diagnostics. It still does not decode S-57 feature semantics; that begins in the next phase.


## Phase 3 status

Phase 3 adds the first S-57 semantic layer on top of ISO8211: dataset metadata, feature records (`FRID`/`FOID`/`ATTF`/`NATF`/`FSPT`), vector records (`VRID` plus coordinate counts), object-class counts, and a JVM raw-dump diagnostic. Geometry reconstruction is still reserved for Phase 4.


## Phase 4 status

Phase 4 reconstructs basic geometries from raw S-57 feature/vector records: points, multipoints, line strings, simple area rings/polygons, feature bounds, dataset bounds, orientation handling, and diagnostics for missing vectors. IndexedDB and WebGL chart rendering remain later phases.


## Current implementation phase

Phase 5 adds the first browser-indexing layer: decoded `S57Dataset` values can be imported into a fixed-grid spatial index, queried by geographic bounds, and prepared for a browser IndexedDB-backed cache.  The project still does not implement chartplotter pan/zoom UX, AIS, NMEA, or quilting.


## Phase 6 S-52 dependency

Phase 6 uses the published `s52-kotlin-webgl` v0.3.0 Maven release. CI downloads:

```text
https://github.com/bareboat-necessities/s52-kotlin-webgl/releases/download/v0.3.0/s52-kotlin-webgl-release-maven-0.3.0.zip
```

Local builds can either unpack that ZIP into `build/s52-maven` or pass:

```bash
gradle phase6Check -Ps52MavenRepo=/path/to/unpacked/s52-maven -Ps52.version=0.3.0
```

### Phase 7 static rendering

The library now has a static chart-frame pipeline: indexed S-57 features can be
queried, projected to a fixed viewport, hit-tested, and drawn by the browser
WebGL shell. This is intentionally a rendering primitive for a future larger
chartplotter, not a complete chartplotter UI.


```bash
gradle phase8Check
```

Phase 8 adds rendered artifact diagnostics and SVG snapshot export for static chart frames.

## Phase 9 status

Phase 9 adds `S57WebGlEngine`, a small facade for importing decoded S-57 datasets,
listing indexed cells, rendering fixed chart frames, querying the center crosshair,
and exporting diagnostics/SVG snapshots. The S-52 adapter common source now uses a
JS-safe intermediate portrayal model so Kotlin/JS can compile while the direct
S-52 runtime bridge remains a later integration step when JS artifacts are
available.

```bash
gradle phase9Check
```


## Phase 10 status

The library now has an end-to-end byte import boundary:

```text
S-57/ENC bytes -> ISO8211 -> raw S-57 records -> geometry dataset -> indexable static render input
```

Browser code can use `BrowserS57FileImporter` to read a selected `File` and call `S57WebGlEngine.importS57Bytes(...)`.


## S-52 integration status

Phase 11 wires this project to `s52-kotlin-webgl` v0.3.0 release artifacts. The demo sample render uses `BrowserS57WebGlRenderer.renderS52Frame(...)`, which adapts decoded S-57 features to real S-52 `EncFeature` values, runs `S52PortrayalSession`, and renders the resulting `S52DrawCommand` list through `WebGlS52Renderer`.

Local build with the S-52 release Maven ZIP:

```bash
mkdir -p build/s52-maven
curl -fL https://github.com/bareboat-necessities/s52-kotlin-webgl/releases/download/v0.3.0/s52-kotlin-webgl-release-maven-0.3.0.zip -o /tmp/s52-kotlin-webgl-release-maven.zip
unzip -q /tmp/s52-kotlin-webgl-release-maven.zip -d build/s52-maven
gradle phase11Check -Ps52.version=0.3.0 -Ps52MavenRepo="$PWD/build/s52-maven"
```

## Chart ZIP coverage test

Use the external chart ZIP coverage test when you have a `.zip` archive of S-57/ENC charts and want the JVM test suite to reject corpus gaps that would otherwise rely on fallback rendering. The ZIP can contain nested folders; every `*.000` base cell is decoded, and sibling `*.001`, `*.002`, ... updates with the same path/name stem are applied in numeric order.

Run it from the repository root with:

```bash
gradle :s57-render-webgl:jvmTest --tests io.github.s57.render.ChartZipCoverageTest -Ds57.chartZip=/absolute/path/to/charts.zip
```

The test fails if any chart in the archive produces:

- unknown raw records, object classes (`OBJL_###`), or attributes (`ATTL_###`);
- contour objects such as `DEPCNT` that do not decode to line geometry;
- area objects such as `DEPARE`, `LNDARE`, `SEAARE`, or coverage/metadata area classes that do not decode to polygon geometry;
- geometry warnings such as primitive correction or area-ring fallback; or
- adapter warnings that indicate unsupported objects/attributes or non-renderable geometry.

If `-Ds57.chartZip=...` is omitted, `ChartZipCoverageTest` prints a skip message and exits successfully so normal JVM test runs do not require private or large chart archives.
