package io.github.s57.render

import kotlinx.browser.window

/**
 * Compatibility shim for Kotlin/JS external DOM casts around WebGL2.
 *
 * Some generated Kotlin/JS code and older S-52 WebGL2 artifacts ask the
 * browser for `canvas.getContext("webgl2")`, then safe-cast the returned
 * JavaScript object to Kotlin's `WebGLRenderingContext`.  In real browsers
 * that object is a `WebGL2RenderingContext`.  It has all WebGL1 methods used
 * by the current S-52 renderer, but JavaScript `instanceof
 * WebGLRenderingContext` can still be false, so Kotlin's safe-cast reports
 * "WebGL2 is not available" even when WebGL2 exists.
 *
 * Install this before constructing the S-52 WebGL renderer.  It is deliberately
 * narrow: it only links WebGL2RenderingContext.prototype to
 * WebGLRenderingContext.prototype when the browser exposes both constructors
 * and the native instanceof check currently fails.
 */
internal fun installWebGl2KotlinJsCompatibilityShim() {
    js(
        """
        (function () {
          var w = (typeof window !== 'undefined') ? window : null;
          if (!w) return;
          if (typeof w.WebGL2RenderingContext === 'undefined' || typeof w.WebGLRenderingContext === 'undefined') {
            w.s57WebGl2KotlinJsShim = 'not-needed-or-unavailable';
            return;
          }
          var webgl2Prototype = w.WebGL2RenderingContext && w.WebGL2RenderingContext.prototype;
          var webgl1Prototype = w.WebGLRenderingContext && w.WebGLRenderingContext.prototype;
          if (!webgl2Prototype || !webgl1Prototype || typeof Object.setPrototypeOf !== 'function') {
            w.s57WebGl2KotlinJsShim = 'prototype-unavailable';
            return;
          }
          try {
            var probeCanvas = document.createElement('canvas');
            var probeContext = probeCanvas.getContext('webgl2');
            if (probeContext && !(probeContext instanceof w.WebGLRenderingContext)) {
              if (Object.getPrototypeOf(webgl2Prototype) !== webgl1Prototype) {
                Object.setPrototypeOf(webgl2Prototype, webgl1Prototype);
              }
              w.s57WebGl2KotlinJsShim = 'installed';
            } else {
              w.s57WebGl2KotlinJsShim = 'native-compatible';
            }
          } catch (error) {
            w.s57WebGl2KotlinJsShim = 'failed: ' + (error && (error.message || error));
          }
        })();
        """
    )
}

internal fun webGl2KotlinJsCompatibilityShimStatus(): String =
    (window.asDynamic().s57WebGl2KotlinJsShim as? String) ?: "unknown"
