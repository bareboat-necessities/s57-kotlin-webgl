# WebGL2 performance compile fix

This incremental patch fixes the Kotlin/JS compile break introduced by the
WebGL2 performance/declutter patch.

## Fixed

- Added the missing `io.github.s52.core.geometry.Coordinate` import in
  `BrowserS52DisplayCommandFilter.kt`.
- Renamed display-plan-only pixel helper types so they do not collide with the
  private helper types in `BrowserS52WebGlVectorLabelOverlay.kt`:
  - `BrowserS52PixelPoint` -> `BrowserS52PlanPixelPoint`
  - `BrowserS52PixelBounds` -> `BrowserS52PlanPixelBounds`

## Why

Kotlin private top-level declarations are private to the file for visibility,
but their generated Kotlin/JS names still cannot be duplicated in the same
package/module. The display command filter and vector label overlay each had
private top-level `BrowserS52PixelPoint` / `BrowserS52PixelBounds` classes,
which caused CI redeclaration errors.

The WebGL2 batching, strict WebGL2 path, interaction throttling, and declutter
logic remain unchanged.
