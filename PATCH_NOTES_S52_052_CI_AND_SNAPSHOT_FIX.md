# S-52 0.5.2 upgrade and Phase 26 CI snapshot fix

This incremental patch switches the project from `bareboat-necessities/s52-kotlin-webgl` `v0.5.0` to `v0.5.2` and fixes the Phase 26 snapshot failure shown in CI logs.

## Changes

- Updates `s52.version` to `0.5.2`.
- Updates GitHub Actions to download and verify the `v0.5.2` Maven release, source release, and critical OpenCPN symbol image bundle.
- Adds `build/s52-images`, `build/s52-maven`, and `build/s52-source-unpacked` to the runtime atlas search paths.
- Exports Phase 26 report helpers through plain JavaScript wrappers backed by a JSON string, avoiding Kotlin/JS dynamic function-object interop that can produce `P.asDynamic is not a function` in Chrome/Playwright.
- Fixes the Phase 26 snapshot harness so it does not wait forever for asynchronous raster redraw when headless Chromium has already reported no WebGL2 context but S-52 portrayal produced commands.
- Keeps `s52Commands >= 1` as a hard requirement.
- Downgrades remaining valid-but-unmodeled NOAA S-57 coverage from warnings to informational diagnostics:
  - `CATLND`
  - `CATSLO`
  - `LNDRGN` point aliases to `LNDARE` point metadata
  - `ACHBRT` / `ACHARE` line metadata
- Adds common adapter value kind coverage for `CATLND` and `CATSLO`.

## Validation performed locally

- `node --check tools/ci-render-snapshot/render-snapshot.mjs`
- `bash -n .github/scripts/run-ci-enc-snapshot.sh`
- `bash -n .github/scripts/package-noaa-demo-artifact.sh`

Full Gradle/Kotlin-JS execution was not run in the sandbox because Gradle is not installed and the uploaded repository has no Gradle wrapper.
