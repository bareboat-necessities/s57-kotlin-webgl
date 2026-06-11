# OpenCPN S-52 CI snapshot and adapter coverage fix

This incremental patch fixes the build regression and reduces the large S-52
adapter warning flood seen with the NOAA US5NYCDF Phase 26 snapshot.

## Fixes

- Fixes the Phase 26 snapshot reader so it reads `window.s57Phase26ReportJson()`
  from the browser instead of recursing in `readPhase26Report()`.
- Makes the snapshot harness tolerate CI/headless Chromium WebGL2 environment
  failures **only after** S-52 portrayal has produced commands.  S-52 still must
  produce at least one command.
- Avoids the Kotlin/JS dynamic helper that produced the browser error
  `P.asDynamic is not a function` by publishing the parsed report helper as a
  plain JavaScript function.
- Adds S-52/OpenCPN compatibility aliases for common NOAA ENC object classes
  missing from the bundled S-52 0.5 catalogue:
  - `ACHBRT -> ACHARE`
  - `BUAARE -> BUILNG`
  - `BUISGL` point -> `BUILNG`
  - `CBLARE` -> `CBLSUB`/`RESARE`
  - `CTNARE -> RESARE`
  - `DRYDOC -> DOCARE`
  - `HRBFAC` point/area -> `MORFAC`/`HRBARE`
  - `LNDRGN -> LNDARE`
  - `PIPARE` -> `PIPSOL`/`RESARE`
  - `SLOTOP -> SLCONS`
  - `UNSARE -> M_QUAL`
- Keeps the previous shared-CSP recovery aliases:
  - `DRGARE -> DEPARE`
  - `UWTROC -> OBSTRN`
- Downgrades known valid S-57 attributes missing from the bundled S-52 0.5 typed
  catalogue from noisy warnings to informational diagnostics:
  - `CATAIR`, `CATSEA`, `CATSIL`, `CATSLC`, `CATSPM`, `NATSUR`, `TRAFIC`
- Downgrades known non-rendered ENC metadata primitives to informational
  diagnostics instead of treating them as adapter warnings:
  - `LNDARE` point
  - `MAGVAR` point
  - `SBDARE` point
  - `SLCONS` point

## Validation performed in this environment

- `node --check tools/ci-render-snapshot/render-snapshot.mjs`
- ZIP integrity check with `unzip -t`

The full Gradle/Kotlin-JS build was not run here because the uploaded baseline
has no Gradle wrapper and this sandbox does not have Gradle installed.
