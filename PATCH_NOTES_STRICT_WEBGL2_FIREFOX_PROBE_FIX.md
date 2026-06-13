# Strict WebGL2 Firefox probe fix

This incremental update supersedes the rejected WebGL1 compatibility patch.  The browser render path is strict WebGL2-only again.

## What changed

- Removed the temporary `webgl` / `experimental-webgl` fallback path from the early browser shim.
- Kept the S-52 renderer on `canvas.getContext("webgl2")` only.
- Changed the Kotlin/JS WebGL2 compatibility shim so it does not mutate native WebGL prototype chains with `Object.setPrototypeOf(WebGL2RenderingContext.prototype, WebGLRenderingContext.prototype)`.
- Uses a safer `Symbol.hasInstance` shim only when the browser exposes separate WebGL/WebGL2 constructor families and Kotlin/JS safe-casts need help.
- Probes strict WebGL2 through the original unwrapped `HTMLCanvasElement.getContext`, so diagnostics report the browser result rather than a shim side effect.
- Keeps the context retry strict: it may retry WebGL2 without `failIfMajorPerformanceCaveat` or without attributes, but it never switches to WebGL1.
- Keeps the S-52 source patch as a strict WebGL2 `unsafeCast<WebGLRenderingContext>()` patch to avoid Kotlin/JS cast failures against a real `WebGL2RenderingContext`.

## Expected diagnostics

A working Firefox WebGL2 path should now report one of these instead of `webgl2-unavailable`:

- `native-compatible`
- `installed-hasinstance`
- `needs-source-unsafe-cast`

If it still reports `strict webgl2 getContext returned null`, then the browser process is refusing WebGL2 context creation before project code gets involved.  In that case check `about:support` → Graphics/WebGL2 and any `webgl.disabled` / driver blocklist state.
