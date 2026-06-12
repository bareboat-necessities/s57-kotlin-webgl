#!/usr/bin/env bash
set -euo pipefail

S52_SOURCE_DIR="${1:-${S52_SOURCE_DIR:-}}"
if [[ -z "$S52_SOURCE_DIR" ]]; then
  echo "Usage: $0 <s52-source-dir>" >&2
  exit 2
fi
if [[ ! -d "$S52_SOURCE_DIR" ]]; then
  echo "S-52 source directory does not exist: $S52_SOURCE_DIR" >&2
  exit 2
fi

renderer_file="$(find "$S52_SOURCE_DIR" -path '*/s52-render-webgl/src/jsMain/kotlin/io/github/s52/render/webgl/WebGlS52Renderer.kt' -type f | head -1 || true)"
if [[ -z "$renderer_file" ]]; then
  echo "Could not find s52-render-webgl WebGlS52Renderer.kt under $S52_SOURCE_DIR" >&2
  find "$S52_SOURCE_DIR" -maxdepth 6 -type f -name 'WebGlS52Renderer.kt' -print >&2 || true
  exit 1
fi

python3 - "$renderer_file" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
if '.unsafeCast<WebGLRenderingContext>()' in text:
    print(f"S-52 WebGL2 Kotlin/JS cast patch already present: {path}")
    raise SystemExit(0)
old = 'private val gl: WebGLRenderingContext = canvas.getContext("webgl2") as? WebGLRenderingContext ?: error("WebGL2 is not available in this browser")'
new = '''private val gl: WebGLRenderingContext = (canvas.getContext("webgl2")
        ?: error("WebGL2 is not available in this browser"))
        .unsafeCast<WebGLRenderingContext>()'''
if old not in text:
    raise SystemExit(f"Expected WebGL2 safe-cast line was not found in {path}; refusing blind patch")
path.write_text(text.replace(old, new))
print(f"Patched S-52 WebGlS52Renderer WebGL2 Kotlin/JS cast: {path}")
PY
