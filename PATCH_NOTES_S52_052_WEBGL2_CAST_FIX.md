# S-52 0.5.2 WebGL2 cast fix

The CI browser now proves WebGL2 exists before rendering, but the S-52 0.5.2
`WebGlS52Renderer` still threw `WebGL2 is not available in this browser`.
The root cause is not the GitHub runner GL stack: S-52 0.5.2 obtains a
`webgl2` context and then safe-casts it to Kotlin's `WebGLRenderingContext`.
In Chromium, `WebGL2RenderingContext` is a separate JavaScript constructor, so
that safe cast can fail even when WebGL2 is working.

This patch does two things:

1. The CI source-release composite build patches
   `s52-render-webgl/.../WebGlS52Renderer.kt` after unpacking S-52 0.5.2,
   replacing the safe cast with a non-null WebGL2 check plus `unsafeCast`.
2. The S-57 browser bridge installs a small runtime compatibility shim before
   constructing `WebGlS52Renderer`, so local Maven/metadata builds have the same
   chance to run until S-52 publishes the upstream fix.

No runtime raster-symbol downloads are added. S-52 portrayal still comes from
Kotlin draw commands.
