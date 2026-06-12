# S-52 0.5.3 demo WebGL2 Kotlin/JS compatibility fix

The demo can still report:

```text
S-52 WebGL render failed after portrayal: WebGL2 is not available in this browser
```

even after Chromium/WebGL2 preflight succeeds and S-52 portrayal produces
commands.  This is caused by Kotlin/JS runtime type checks around DOM WebGL
external types: a real browser returns a `WebGL2RenderingContext`, while older
or cached S-52 WebGL code may safe-cast it to `WebGLRenderingContext` and treat
a failed cast as WebGL2 absence.

This patch installs a narrow compatibility shim before the S-52 renderer is
constructed.  The shim only links `WebGL2RenderingContext.prototype` to
`WebGLRenderingContext.prototype` if both constructors exist and a real WebGL2
probe is not already an instance of `WebGLRenderingContext`.

Changes:

- Adds `BrowserWebGl2Compatibility.kt`.
- Calls the shim before `WebGlS52Renderer` construction.
- Adds shim status to WebGL failure diagnostics.
- Installs the same shim in `index.html` before `demo.js` so packaged browser
  demos and stale local Maven builds get the compatibility layer early.

This does not reintroduce runtime raster atlas downloads and does not use the
old decoded-geometry debug renderer as fallback.
