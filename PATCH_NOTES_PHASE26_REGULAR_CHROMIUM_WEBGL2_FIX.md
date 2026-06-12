# Phase 26 regular Chromium WebGL2 snapshot fix

This incremental patch fixes the CI snapshot failure where S-52 0.5.2
successfully produced thousands of portrayal commands, but the Playwright
browser was `chromium-headless-shell` and did not expose WebGL2.  That path
could only produce a blank blue canvas, so the snapshot correctly failed.

Changes:

- Installs Playwright's regular Chromium build with `--no-shell chromium`
  instead of relying on `chromium-headless-shell`.
- Launches the snapshot browser with `--browser-channel=chromium` under Xvfb
  so Playwright uses the real Chromium build for WebGL2 rendering.
- Adds a `browser-channel` option to the snapshot script and reports the active
  browser channel in WebGL2 failure messages.
- Triggers rendering through the DOM render button instead of calling a Kotlin
  function object from JavaScript.  This avoids Kotlin/JS dynamic bridge errors
  like `P.asDynamic is not a function`.
- Makes the render button use the Phase 26 snapshot viewport when
  `window.s57Phase26SnapshotMode` is enabled.
- Downgrades NOAA `NATQUA` attribute diagnostics to known/unmodeled handling.

Validation performed in this sandbox:

- `node --check tools/ci-render-snapshot/render-snapshot.mjs`
- `bash -n .github/scripts/run-ci-enc-snapshot.sh`

Full Gradle/Kotlin-JS execution was not run here because this uploaded baseline
has no Gradle wrapper and the sandbox does not include Gradle.
