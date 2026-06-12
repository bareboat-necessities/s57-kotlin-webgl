# S-52 0.5.4 vector label color signature fix

This incremental patch fixes the Kotlin/JS compilation failure in
`BrowserS52WebGlVectorLabelOverlay.kt`.

S-52 0.5.4 exposes `presLib.colors.color(palette, token)`. The previous
incremental patch accidentally called it as `color(token, palette)`, producing
compiler errors where `String` and `S52Palette` were reversed.

The label overlay remains WebGL-only. No Canvas2D renderer or decoded/debug
geometry fallback is restored.
