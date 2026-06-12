# S-52 0.5.4 demo interaction and clutter fix

This incremental patch addresses the first usability problems observed once the
S-52 WebGL renderer started drawing real cells in the browser.

## Interaction fixes

- Mouse wheel zoom is now armed only after the chart canvas is clicked/focused.
  Hovering over the chart no longer steals normal page scrolling.
- Wheel input in the demo is zoom-only; it no longer emits a scroll/pan event
  before zooming.
- The demo inverts the wheel delta used by the chart command path to match the
  requested zoom direction.
- The chart canvas is focusable and shows a focus ring so the user can tell when
  wheel zoom is active.

## S-52 display cleanup

- Adds a browser display command filter between S-52 portrayal and the WebGL
  renderer.
- Injects solid area fills before `AreaPattern` commands when the pattern carries
  a background color. This prevents pattern-only areas from looking unfilled.
- Suppresses raster area-pattern tiles in the browser demo. These tiles were the
  repeated rounded-frame artifacts visible in Chrome. Vector/simple line/fill
  commands and point symbols are still rendered.
- De-clutters `Text` and `Sounding` commands before passing them to the S-52
  WebGL renderer. Labels are capped and overlap-filtered using viewport screen
  coordinates, so Chrome no longer draws hundreds of overlapping line-glyph
  labels in one viewport.
- Adds diagnostic code `s52.browser_display_filter` with counts for injected
  fills and suppressed clutter.

## Files

- `demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt`
- `demo/src/jsMain/resources/index.html`
- `s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/InteractionModels.kt`
- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartInput.kt`
- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52StructuredRender.kt`
- `s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52DisplayCommandFilter.kt`
