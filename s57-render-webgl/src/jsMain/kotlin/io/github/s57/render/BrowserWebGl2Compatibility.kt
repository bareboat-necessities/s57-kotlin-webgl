package io.github.s57.render

import kotlinx.browser.window

/**
 * Compatibility shim for Kotlin/JS external DOM casts around WebGL2.
 *
 * This is intentionally strict WebGL2-only.  It does not return WebGL1 for a
 * `webgl2` request and does not install Canvas2D/decoded-geometry fallbacks.
 *
 * Older S-52 WebGL artifacts can safe-cast a real `WebGL2RenderingContext` to
 * Kotlin's `WebGLRenderingContext`.  Some browsers expose those as distinct JS
 * constructors, so the safe-cast can fail even when WebGL2 exists.  Prefer the
 * build-time S-52 source patch to replace that cast with `unsafeCast`.  This
 * runtime shim only retries strict WebGL2 context creation and, where the JS
 * engine allows it, widens `instanceof WebGLRenderingContext` through
 * `Symbol.hasInstance` without mutating native WebGL prototype chains.
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

          function probeWebGl2() {
            var htmlCanvas = w.HTMLCanvasElement && w.HTMLCanvasElement.prototype;
            var getContext = (htmlCanvas && htmlCanvas.__s57WebGl2OriginalGetContext) || (htmlCanvas && htmlCanvas.getContext);
            if (!getContext || typeof document === 'undefined') return null;
            var canvas = document.createElement('canvas');
            try {
              return getContext.call(canvas, 'webgl2');
            } catch (_) {
              return null;
            }
          }

          function rendererName(gl) {
            var renderer = 'unknown';
            try {
              var debugInfo = gl && gl.getExtension && gl.getExtension('WEBGL_debug_renderer_info');
              if (debugInfo) renderer = String(gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL));
            } catch (_) {
              renderer = 'unavailable';
            }
            return renderer;
          }

          function installHasInstanceShim() {
            if (typeof Symbol === 'undefined' || !Symbol.hasInstance) return false;
            if (w.WebGLRenderingContext.__s57WebGl2HasInstanceShim) return true;
            var nativeHasInstance = Function.prototype[Symbol.hasInstance];
            try {
              Object.defineProperty(w.WebGLRenderingContext, Symbol.hasInstance, {
                configurable: true,
                value: function (instance) {
                  try {
                    if (nativeHasInstance.call(this, instance)) return true;
                    return !!(w.WebGL2RenderingContext && instance instanceof w.WebGL2RenderingContext);
                  } catch (_) {
                    return false;
                  }
                }
              });
              w.WebGLRenderingContext.__s57WebGl2HasInstanceShim = true;
              return true;
            } catch (_) {
              return false;
            }
          }

          installContextRetryShim();

          if (typeof w.WebGLRenderingContext === 'undefined') {
            w.s57WebGl2KotlinJsShim = 'webgl-unavailable';
            w.s57WebGl2LastProbe = 'WebGLRenderingContext constructor is unavailable';
            return;
          }
          if (typeof w.WebGL2RenderingContext === 'undefined') {
            w.s57WebGl2KotlinJsShim = 'webgl2-constructor-unavailable';
            w.s57WebGl2LastProbe = 'WebGL2RenderingContext constructor is unavailable';
            return;
          }

          try {
            var probeContext = probeWebGl2();
            if (!probeContext) {
              w.s57WebGl2KotlinJsShim = 'webgl2-unavailable';
              w.s57WebGl2LastProbe = 'strict webgl2 getContext returned null';
              return;
            }
            w.s57WebGl2LastProbe = 'strict webgl2 available; renderer=' + rendererName(probeContext);
            if (probeContext instanceof w.WebGLRenderingContext) {
              w.s57WebGl2KotlinJsShim = 'native-compatible';
              return;
            }
            var hasInstanceInstalled = installHasInstanceShim();
            if (hasInstanceInstalled && probeContext instanceof w.WebGLRenderingContext) {
              w.s57WebGl2KotlinJsShim = 'installed-hasinstance';
            } else {
              w.s57WebGl2KotlinJsShim = 'needs-source-unsafe-cast';
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
