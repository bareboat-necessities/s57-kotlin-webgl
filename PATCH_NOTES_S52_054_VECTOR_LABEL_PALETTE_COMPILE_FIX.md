# S-52 0.5.4 vector-label palette compile fix

Fixes the Kotlin/JS compile failure introduced by the WebGL-only vector label overlay.

## Problem

`BrowserS52WebGlVectorLabelOverlay.kt` passed `MarinerSettings.palette` to a helper typed as `String`, and then called `presLib.colors.color(...)` with the old argument order.

The S-52 0.5.4 API expects the color token first and `S52Palette` second.

## Fix

- Import `S52Palette`.
- Change `colorFor(...)` to accept `S52Palette`.
- Call `presLib.colors.color(token, palette)` and `presLib.colors.color("CHBLK", palette)`.

This keeps label drawing WebGL-only and does not restore Canvas2D fallbacks.
