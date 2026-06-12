package io.github.s57.render

/*
 * Intentionally empty.
 *
 * No Canvas2D S-52 command fallback is allowed.  All successful chart drawing
 * must go through io.github.s52.render.webgl.WebGlS52Renderer only.  Failures
 * must report diagnostics instead of drawing a second non-WebGL representation.
 */
