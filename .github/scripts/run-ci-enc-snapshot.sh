#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"


copy_s52_browser_resources() {
  local app_dir="$1"
  local candidates=()
  if [[ -n "${S52_SOURCE_DIR:-}" ]]; then
    candidates+=("$S52_SOURCE_DIR/s52-render-webgl/src/jsMain/resources")
    candidates+=("$S52_SOURCE_DIR/s52-render-webgl/build/processedResources/js/main")
  fi
  candidates+=("../s52-kotlin-webgl/s52-render-webgl/src/jsMain/resources")
  candidates+=("../s52-kotlin-webgl/s52-render-webgl/build/processedResources/js/main")
  candidates+=("demo/build/processedResources/js/main")
  candidates+=("s57-render-webgl/build/processedResources/js/main")

  local copied=0
  local dir
  for dir in "${candidates[@]}"; do
    if [[ -f "$dir/s52/opencpn/rastersymbols-day.png" && -f "$dir/s52/opencpn/rastersymbols-dusk.png" && -f "$dir/s52/opencpn/rastersymbols-dark.png" ]]; then
      mkdir -p "$app_dir"
      rsync -a "$dir/" "$app_dir/"
      copied=1
      echo "Bundled S-52/OpenCPN browser resources from $dir"
      break
    fi
  done

  if [[ "$copied" != "1" ]]; then
    local found=""
    found="$(find build .. -path '*/s52/opencpn/rastersymbols-day.png' -printf '%h\n' 2>/dev/null | head -1 || true)"
    if [[ -n "$found" ]]; then
      local root="${found%/s52/opencpn}"
      if [[ -f "$root/s52/opencpn/rastersymbols-dusk.png" && -f "$root/s52/opencpn/rastersymbols-dark.png" ]]; then
        rsync -a "$root/" "$app_dir/"
        copied=1
        echo "Bundled S-52/OpenCPN browser resources from discovered root $root"
      fi
    fi
  fi

  if [[ "$copied" != "1" || ! -f "$app_dir/s52/opencpn/rastersymbols-day.png" || ! -f "$app_dir/s52/opencpn/rastersymbols-dusk.png" || ! -f "$app_dir/s52/opencpn/rastersymbols-dark.png" ]]; then
    echo "Missing S-52/OpenCPN raster atlases in packaged browser app." >&2
    echo "Set S52_SOURCE_DIR or -Ps52SourceDir to an s52-kotlin-webgl checkout/release containing s52-render-webgl/src/jsMain/resources/s52/opencpn/rastersymbols-{day,dusk,dark}.png." >&2
    return 1
  fi
}

if [[ -n "${PHASE26_APP_DIR:-}" ]]; then
  APP_DIR="$PHASE26_APP_DIR"
  copy_s52_browser_resources "$APP_DIR"
else
  JS_DIR="demo/build/dist/js/productionExecutable"
  if [[ ! -d "$JS_DIR" ]]; then
    JS_DIR="demo/build/kotlin-webpack/js/productionExecutable"
  fi
  if [[ ! -d "$JS_DIR" ]]; then
    JS_DIR="$(find demo/build -type f -name 'demo.js' -printf '%h\n' | head -1 || true)"
  fi
  if [[ -z "${JS_DIR:-}" || ! -d "$JS_DIR" ]]; then
    echo "Could not locate Kotlin/JS browser distribution containing demo.js" >&2
    find demo/build -maxdepth 5 -type f | sort | head -80 >&2 || true
    exit 1
  fi
  RESOURCE_DIR="demo/build/processedResources/js/main"
  APP_DIR="build/ci-enc-snapshot/app"
  rm -rf "$APP_DIR"
  mkdir -p "$APP_DIR"
  if [[ -d "$RESOURCE_DIR" ]]; then
    rsync -a "$RESOURCE_DIR/" "$APP_DIR/"
  fi
  rsync -a "$JS_DIR/" "$APP_DIR/"
  copy_s52_browser_resources "$APP_DIR"
fi
if [[ ! -f "$APP_DIR/index.html" || ! -f "$APP_DIR/demo.js" ]]; then
  echo "Snapshot app directory must contain index.html and demo.js: $APP_DIR" >&2
  find "$APP_DIR" -maxdepth 3 -type f | sort | head -80 >&2 || true
  exit 1
fi

bash .github/scripts/download-first-enc-cell.sh
(
  cd tools/ci-render-snapshot
  npm install
  npx playwright install --with-deps chromium
  npm run snapshot -- --app-dir="$ROOT_DIR/$APP_DIR" --enc-file="$ROOT_DIR/build/ci-enc-snapshot/input/cell.000" --out-dir="$ROOT_DIR/build/ci-enc-snapshot"
)

test -s build/ci-enc-snapshot/render.png
test -s build/ci-enc-snapshot/diagnostics.json
test -s build/ci-enc-snapshot/summary.txt
