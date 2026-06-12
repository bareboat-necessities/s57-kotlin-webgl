# S-52 0.5.4 demo canvas/context and IndexedDB cache fix

This incremental patch fixes two regressions seen in the browser demo after the
S-52 0.5.4 upgrade.

## Firefox blue/empty canvas

The demo could still report:

```text
S-52 WebGL render failed after portrayal: WebGL2 is not available in this browser ... webgl2Shim=native-compatible
```

The important detail is `native-compatible`: the browser itself has WebGL2, but
the chart canvas can be poisoned if earlier debug/demo code claimed the same
canvas with a WebGL1 or Canvas2D context.  Once that happens, asking the same
canvas for `webgl2` returns `null` even though a fresh canvas would support
WebGL2.

The S-52 render path now preflights the actual chart canvas.  If the canvas does
not return a usable WebGL2 context, the path replaces only that DOM canvas element
with a fresh canvas carrying the same id, class, size, and style, clears the
cached renderer, and lets S-52 create a new WebGL2 context.

## Chrome stuck on IndexedDB decoded cache check

The decoded IndexedDB lookup is now bounded.  If IndexedDB is slow, blocked, or
stalls during version upgrade/cache lookup, import falls back to byte decoding
instead of leaving the UI at:

```text
Checking decoded IndexedDB cache for US5NYCDF (base .000) ...
```

Also removes the duplicate `onblocked` callback in the IndexedDB open path.
