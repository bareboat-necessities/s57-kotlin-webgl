# Phase 26 WebGL2 preflight and canvas-context fix

This incremental patch fixes the remaining blank-blue `render.png` failure after switching to S-52 `0.5.2`.

## Root cause

The previous CI change still launched Chromium with one forced SwiftShader/ANGLE configuration.  On the GitHub runner shown in the log, S-52 portrayal succeeded and produced thousands of renderer-independent S-52 commands, but the browser did not expose a usable WebGL2 context, so the WebGL backend could not draw the commands.

There was also a canvas-context hazard: older fallback/diagnostic code could claim the chart canvas with WebGL1 or Canvas2D after a WebGL2 failure.  Once a canvas has one context type, browsers do not allow switching that same canvas to WebGL2 later.

## Changes

- `tools/ci-render-snapshot/render-snapshot.mjs`
  - Adds a WebGL2 preflight before loading the chart app.
  - Tries multiple Chromium GL backends in order:
    - native/default GL
    - EGL
    - desktop GL
    - SwiftShader GL
    - ANGLE SwiftShader
  - Adds both SwiftShader opt-in flags:
    - `--allow-unsafe-swiftshader`
    - `--enable-unsafe-swiftshader`
  - Ignores Playwright's `--disable-gpu` default argument if present.
  - Fails before import/render if no backend exposes WebGL2, instead of importing the ENC and later publishing/attempting a blank blue canvas.
  - Reads Phase 26 diagnostics only from string JSON exports and no longer calls Kotlin/JS function objects from Playwright.

- `.github/scripts/run-ci-enc-snapshot.sh`
  - Installs Xvfb plus Mesa/EGL/GLES packages used by regular Chromium on GitHub runners.
  - Runs snapshot with `PHASE26_GL_MODE=auto`.

- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt`
  - Prefers `webgl2` before `webgl` in legacy/debug renderer paths so those paths do not poison the chart canvas with WebGL1 before S-52 rendering.

- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52StructuredRender.kt`
  - S-52 failure reporting no longer calls `getContext("webgl")` or `getContext("2d")` on the chart canvas.  This avoids permanently preventing a later WebGL2 retry on the same canvas.

- `demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt`
  - Removes wrapper functions for Phase 26 diagnostics and keeps the CI handoff as a plain JSON string property.

## Runtime notes

S-52 `0.5.2` produces renderer-independent Kotlin draw commands, but the browser demo still uses the optional WebGL2 backend to draw them.  This patch does not reintroduce raster-symbol downloads.

If GitHub's runner/browser still cannot provide WebGL2 after all probed modes, the snapshot will now fail early with the list of attempted GL modes.  The chart code itself will no longer silently produce a blue-only `render.png`.
