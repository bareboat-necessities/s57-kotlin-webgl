# Scope

`s57-kotlin-webgl` is a browser-capable S-57 engine, not a navigation application.

## In scope

- User-selected local S-57 / NOAA ENC files
- ISO8211 parser
- S-57 decoder
- geometry reconstruction
- local browser indexing
- static WebGL chart rendering
- integration boundary for `s52-kotlin-webgl`

## Out of scope

- Pan/zoom chartplotter UX
- Quilting
- AIS
- NMEA
- Ownship / GPS
- Route planning
- Alarms
- Certified ECDIS behavior

## Design rule

Keep this project small, testable, and reusable. Anything involving live navigation state belongs in a higher-level chartplotter project.
