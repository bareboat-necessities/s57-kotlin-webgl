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
  find "$S52_SOURCE_DIR" -maxdepth 8 -type f -name 'WebGlS52Renderer.kt' -print >&2 || true
  exit 1
fi

python3 - "$renderer_file" <<'PY'
from pathlib import Path
import re
import sys
path = Path(sys.argv[1])
text = path.read_text()
if '.unsafeCast<WebGLRenderingContext>()' in text and 'WebGL2 is not available in this browser' in text:
    print(f"S-52 WebGL2 Kotlin/JS cast patch already present: {path}")
    raise SystemExit(0)
replacement = '''private val gl: WebGLRenderingContext = (canvas.getContext("webgl2")
        ?: error("WebGL2 is not available in this browser"))
        .unsafeCast<WebGLRenderingContext>()'''
patterns = [
    r'private\s+val\s+gl\s*:\s*WebGLRenderingContext\s*=\s*canvas\.getContext\("webgl2"\)\s+as\?\s+WebGLRenderingContext\s*\?:\s*error\("WebGL2 is not available in this browser"\)',
    r'private\s+val\s+gl\s*:\s*WebGLRenderingContext\s*=\s*\(\s*canvas\.getContext\("webgl2"\)\s+as\?\s+WebGLRenderingContext\s*\)\s*\?:\s*error\("WebGL2 is not available in this browser"\)',
]
for pattern in patterns:
    text2, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count:
        path.write_text(text2)
        print(f"Patched S-52 WebGlS52Renderer WebGL2 Kotlin/JS cast: {path}")
        raise SystemExit(0)
raise SystemExit(f"Expected WebGL2 safe-cast line was not found in {path}; refusing blind patch")
PY
