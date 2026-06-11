# Phase 26 no-blank render.png fix

This incremental patch fixes the regression where Phase 26 CI accepted S-52 portrayal
success even though the browser did not actually draw the chart and `render.png` was
only the cleared blue/white canvas.

Changes:

- Removed the early WebGL2 pre-check from the S-52 browser render path.  The code now
  always runs S-52 portrayal first, so diagnostics and command counts represent the
  Kotlin S-52 pipeline instead of stopping at `webgl2` before portrayal.
- Runs the CI snapshot under headed Chromium inside `xvfb-run` when available.  This
  avoids Playwright's headless-shell WebGL2 limitations on GitHub Actions.
- Keeps S-52 0.5.2 Kotlin symbology: no runtime raster-symbol PNG atlas is downloaded
  or required.
- Removed the bad "commands exist, so blank screenshot is OK" behavior.  A snapshot is
  now accepted only when:
  - S-52 produced commands,
  - the browser exposes WebGL2,
  - S-52 produced draw calls,
  - the saved PNG is visually non-blank.
- Adds an internal PNG pixel-stat check using Node's built-in zlib.  This catches the
  blue-canvas artifact even if the PNG file exists and has nonzero size.
- Deletes stale `render.png` before starting a snapshot, so a failed run cannot upload
  an old image.
