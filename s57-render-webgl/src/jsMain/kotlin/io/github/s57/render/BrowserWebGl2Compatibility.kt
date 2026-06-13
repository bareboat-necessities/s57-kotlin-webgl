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
 * and the native instanceof check currently fails.  It also patches WebGL2
 * context creation to retry without `failIfMajorPerformanceCaveat` when a
 * browser (notably Firefox on some software/remote GPUs) supports WebGL2 but
 * rejects stricter context attributes used by third-party renderers.
 */
internal fun installWebGl2KotlinJsCompatibilityShim() {
    js(
        """
        (function () {
          var w = (typeof window !== 'undefined') ? window : null;
          if (!w) return;

          function installContextRetryShim() {
            var htmlCanvas = w.HTMLCanvasElement && w.HTMLCanvasElement.prototype;
            if (!htmlCanvas || !htmlCanvas.getContext) {
              w.s57WebGl2ContextRetryShim = 'canvas-unavailable';
              return;
            }
            if (htmlCanvas.__s57WebGl2ContextRetryInstalled) {
              w.s57WebGl2ContextRetryShim = 'already-installed';
              return;
            }
            var originalGetContext = htmlCanvas.getContext;
            htmlCanvas.__s57WebGl2OriginalGetContext = originalGetContext;
            htmlCanvas.getContext = function (type, attrs) {
              if (type !== 'webgl2') {
                return originalGetContext.apply(this, arguments);
              }
              var firstError = null;
              var context = null;
              try {
                context = originalGetContext.apply(this, arguments);
              } catch (error) {
                firstError = error;
              }
              if (context) return context;

              var retriedRelaxed = false;
              if (attrs && attrs.failIfMajorPerformanceCaveat === true) {
                var relaxed = {};
                for (var key in attrs) relaxed[key] = attrs[key];
                relaxed.failIfMajorPerformanceCaveat = false;
                retriedRelaxed = true;
                try {
                  context = originalGetContext.call(this, type, relaxed);
                } catch (_) {
                  context = null;
                }
                if (context) {
                  w.s57WebGl2ContextRetryShim = 'used-relaxed-performance-caveat';
                  return context;
                }
              }

              if (attrs) {
                try {
                  context = originalGetContext.call(this, type);
                } catch (_) {
                  context = null;
                }
                if (context) {
                  w.s57WebGl2ContextRetryShim = retriedRelaxed ? 'used-attribute-free-after-relaxed' : 'used-attribute-free';
                  return context;
                }
              }

              if (firstError) throw firstError;
              return context;
            };
            htmlCanvas.__s57WebGl2ContextRetryInstalled = true;
            w.s57WebGl2ContextRetryShim = 'installed';
          }

          installContextRetryShim();

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
            if (!probeContext) {
              w.s57WebGl2KotlinJsShim = 'webgl2-unavailable';
              w.s57WebGl2LastProbe = 'webgl2 getContext returned null';
              return;
            }
            var renderer = 'unknown';
            try {
              var debugInfo = probeContext.getExtension && probeContext.getExtension('WEBGL_debug_renderer_info');
              if (debugInfo) renderer = String(probeContext.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL));
            } catch (_) {
              renderer = 'unavailable';
            }
            w.s57WebGl2LastProbe = 'webgl2 available; renderer=' + renderer;
            if (!(probeContext instanceof w.WebGLRenderingContext)) {
              if (Object.getPrototypeOf(webgl2Prototype) !== webgl1Prototype) {
                Object.setPrototypeOf(webgl2Prototype, webgl1Prototype);
              }
              w.s57WebGl2KotlinJsShim = 'installed';
            } else {
              w.s57WebGl2KotlinJsShim = 'native-compatible';
            }
          } catch (error) {
            w.s57WebGl2KotlinJsShim = 'failed: ' + (error && (error.message || error));
            w.s57WebGl2LastProbe = 'failed: ' + (error && (error.message || error));
          }
        })();
        """
    )
}

internal fun webGl2KotlinJsCompatibilityShimStatus(): String {
    val castShim = (window.asDynamic().s57WebGl2KotlinJsShim as? String) ?: "unknown"
    val contextRetryShim = (window.asDynamic().s57WebGl2ContextRetryShim as? String) ?: "unknown"
    val probe = (window.asDynamic().s57WebGl2LastProbe as? String) ?: "probe-not-run"
    return castShim + "; contextRetry=" + contextRetryShim + "; probe=" + probe
}
