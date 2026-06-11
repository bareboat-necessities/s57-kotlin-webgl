# Phase 26 snapshot build fix

This incremental patch fixes the CI/runtime failure introduced by the CSP recovery patch.

## Fixed

- Replaces the accidental recursive `readPhase26Report()` implementation in
  `tools/ci-render-snapshot/render-snapshot.mjs`. The previous version called
  itself until Node threw `RangeError: Maximum call stack size exceeded`.
- Reads the browser report from `window.s57Phase26Report()` or
  `window.s57Phase26ReportJson()` instead.
- Launches Playwright Chromium with software WebGL flags so CI has the best
  chance of exposing WebGL/WebGL2 in headless mode.
- Treats missing WebGL2 in headless Chromium as a renderer-environment warning
  only when S-52 portrayal already produced commands. This keeps the hard gate
  on the important regression: `s52Commands` must still be nonzero.

## Why

The uploaded log shows S-52 portrayal was recovering enough to produce commands,
but the snapshot script then failed in its own report reader recursion. Some CI
headless Chromium runners also do not expose WebGL2 even when the S-52 command
stream is valid; that should not make the Gradle build fail as a symbology
regression.

## Validation

- `node --check tools/ci-render-snapshot/render-snapshot.mjs`
