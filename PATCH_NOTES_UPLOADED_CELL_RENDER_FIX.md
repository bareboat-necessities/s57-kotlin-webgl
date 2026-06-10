# Uploaded cell render/activation fix

This incremental patch addresses the case where the built-in sample renders, but a successfully imported NOAA cell appears not to change the canvas.

## Fixes

- Decode real binary S-57 `DSID` metadata instead of treating the whole field as printable text.
  - This prevents bogus imported cell IDs such as `1600` from being selected/displayed when the real DSNM is `US5NY1CE`.
  - It also decodes binary `EDTN`, `UPDN`, `UADT`, `ISDT`, and `STED` from the DSID text subfields.
- Decode binary `DSPM` `COMF` and `SOMF` so NOAA coordinate/sounding multipliers come from the cell instead of only the defaults.
- When the S-52 browser render path receives a prepared frame with zero projected source features, it now runs the geometry fallback renderer instead of returning without touching the canvas. This avoids leaving the previous built-in sample image on screen.
- Added a common raw-decoder regression test for binary DSID/DSPM layout using a `US5NY1CE`-style cell name.

## Expected behavior after applying

After uploading `US5NY1CE.000`, the summary should show a real cell ID such as `US5NY1CE` rather than `1600`. If projection or S-52 portrayal still produces no visible output, the canvas should at least be cleared and the status should report the fallback reason instead of leaving the old sample drawing visible.

## Validation performed here

- Compiled the ISO8211/core/raw Kotlin sources with local `kotlinc`.
- Ran a small JVM metadata-decoder smoke program that builds an ISO8211 record containing binary `DSID`/`DSPM` fields and verifies the decoded metadata prints `cellName=US5NY1CE`, `coordinateMultiplier=10000000`, and `soundingMultiplier=10`.
- Verified the incremental ZIP with `unzip -t`.

Gradle is still not available in this sandbox and the uploaded baseline still does not include a Gradle wrapper, so the full multiplatform Gradle build could not be run locally here.
