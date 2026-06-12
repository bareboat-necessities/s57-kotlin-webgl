#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ -n "${PHASE26_APP_DIR:-}" ]]; then
  APP_DIR="$PHASE26_APP_DIR"
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
  # Use the regular Chromium build, not chromium-headless-shell.  The S-52
  # renderer needs WebGL2; Playwright's old headless shell often exposes no
  # usable WebGL2 on GitHub runners even when S-52 portrayal itself succeeds.
  npx playwright install --with-deps --no-shell chromium
  npx playwright install --list || true
  if command -v sudo >/dev/null 2>&1 && command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y xvfb libegl1 libgles2 libgl1-mesa-dri libglx-mesa0 mesa-utils
  fi
  if command -v xvfb-run >/dev/null 2>&1; then
    PHASE26_HEADLESS=false PHASE26_BROWSER_CHANNEL=chromium PHASE26_GL_MODE=auto \
      xvfb-run -a -s "-screen 0 1280x720x24" \
      npm run snapshot -- \
        --app-dir="$ROOT_DIR/$APP_DIR" \
        --enc-file="$ROOT_DIR/build/ci-enc-snapshot/input/cell.000" \
        --out-dir="$ROOT_DIR/build/ci-enc-snapshot" \
        --headless=false \
        --browser-channel=chromium \
        --gl-mode=auto
  else
    echo "xvfb-run is not available; running Chromium new-headless snapshot. The snapshot will fail instead of publishing a blank render.png if WebGL2 is unavailable." >&2
    PHASE26_BROWSER_CHANNEL=chromium PHASE26_GL_MODE=auto npm run snapshot -- --app-dir="$ROOT_DIR/$APP_DIR" --enc-file="$ROOT_DIR/build/ci-enc-snapshot/input/cell.000" --out-dir="$ROOT_DIR/build/ci-enc-snapshot" --browser-channel=chromium \
        --gl-mode=auto
  fi
)

test -s build/ci-enc-snapshot/render.png
test -s build/ci-enc-snapshot/diagnostics.json
test -s build/ci-enc-snapshot/summary.txt
