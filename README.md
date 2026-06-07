# s57-kotlin-webgl

`s57-kotlin-webgl` is a Kotlin Multiplatform / Kotlin JS browser project for importing, indexing, and statically rendering S-57 ENC chart data with WebGL.

The project is intentionally **not** a full chartplotter. It is the lower-level reusable engine that a larger chartplotter can later use.

## Not for navigation

This project is experimental software. It is **Not for navigation**, not type-approved ECDIS software, and must not be used as the primary or backup means of navigation.

## Phase 0 scope

Phase 0 creates the repository skeleton, module boundaries, demo shell, CI, and safety/scope documentation.

In scope for the full `s57-kotlin-webgl` project:

- ISO8211 parsing
- S-57 raw record decoding
- S-57 geometry reconstruction
- browser-side indexing and cache API
- basic IndexedDB-oriented architecture
- static chart rendering with WebGL
- adapter layer for the separate `s52-kotlin-webgl` portrayal/rendering library
- diagnostics and regression artifacts

Out of scope here:

- full chartplotter UI
- continuous pan/zoom UX
- chart quilting
- AIS
- NMEA 0183 / NMEA 2000
- GPS / ownship
- routes and waypoints
- alarms
- ENC download/update manager
- ECDIS certification or type approval

## Modules

```text
s57-iso8211       reusable ISO8211 parser foundation
s57-core          S-57 models, decoder, geometry model
s57-index         browser index/cache interfaces and test implementation
s57-s52-adapter   bridge from decoded S-57 features to S-52 portrayal inputs
s57-render-webgl  WebGL static chart-render orchestration
demo              browser demo shell
```

## Build

Without a Gradle wrapper, use Gradle 8.11+:

```bash
gradle phase0Check
```

GitHub Actions installs Gradle automatically and runs the same command.

## Demo

The Phase 0 demo is a browser shell. It lets the user choose local files and shows their names/sizes. Rendering is currently a placeholder clear of a WebGL canvas; real parsing/rendering starts in later phases.
