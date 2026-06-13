# S-52 0.5.4 WebGL2 render recovery fix

This incremental patch addresses the browser failure where S-57 decode/index and
S-52 portrayal succeed, but the chart stays blank because the S-52 WebGL renderer
throws:

```text
S-52 WebGL render failed after portrayal: WebGL2 is not available in this browser
```

Changes:

- CI now applies the existing S-52 WebGL2 Kotlin/JS cast patch to the unpacked
  S-52 0.5.4 source before the composite build, and `settings.gradle.kts`
  applies the same patch for local `-Ps52SourceDir` / `S52_SOURCE_DIR` builds.
  This replaces the unsafe
  Kotlin safe-cast path in `WebGlS52Renderer` with an explicit non-null WebGL2
  context check plus `unsafeCast`.
- The S-57 browser S-52 bridge now retries once on a fresh chart canvas if the
  first WebGL2 renderer construction fails because of a stale/lost/poisoned
  context.
- Cached S-52 WebGL renderers are now tied to the actual canvas DOM instance,
  not only the canvas id, so a replaced canvas cannot accidentally reuse a
  renderer bound to the old element.
- If the chart canvas is replaced for recovery, the demo re-attaches pointer and
  wheel handlers to the new canvas.
- WebGL2 diagnostics no longer report a null probe as `native-compatible`; the
  status now includes an explicit probe result and renderer string when
  available.

No Canvas2D or decoded-geometry fallback was reintroduced. Failed S-52 frames
remain blank with diagnostics instead of drawing debug glyphs.
