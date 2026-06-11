# OpenCPN S-52 CSP recovery fix

This incremental patch fixes the blue-canvas regression where a single S-52
conditional-symbology exception blocked the whole imported ENC frame.

Observed failure examples from US5NYCDF:

- `DEPARE CSP received DRGARE`
- `OBSTRN CSP received UWTROC`
- `s52Commands=0`
- `s52DrawCalls=0`

## Changes

- `BrowserS52Bridge` no longer lets one OpenCPN CSP exception abort the whole
  frame.
- S-52 portrayal first tries the normal full batch for performance.
- If that throws, the bridge bisects the feature batch and retries smaller
  chunks.
- If only a single feature still throws, that feature is skipped with a
  `s52.csp_feature_skipped` warning while the rest of the chart continues to
  render with S-52/OpenCPN commands.
- Known S-52 v0.5 shared-CSP object-class aliases are applied before portrayal:
  - `DRGARE` is portrayed through the shared `DEPARE` CSP binding.
  - `UWTROC` is portrayed through the shared `OBSTRN` CSP binding.
- The CI snapshot harness now fails on the regression signatures instead of
  accepting a blue canvas:
  - `s52Commands < 1`
  - `pipeline-blocked`
  - `s52.debug_geometry_fallback_suppressed`

## Expected result

For the same US5NYCDF viewport, diagnostics should no longer show
`s52Commands=0` or the whole-frame `s52.debug_geometry_fallback_suppressed`
error. Any remaining isolated CSP incompatibility should appear as a warning,
not as a blocked render pipeline.
