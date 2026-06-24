#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${CHART1_OUT_DIR:-build/ci-chart1-snapshot}"
INPUT_DIR="$OUT_DIR/input"
CHART1_ZIP="$INPUT_DIR/chart1-enc.zip"
CHART1_FILE_LIST="$INPUT_DIR/chart1-enc-file-list.txt"
CHART1_ENC_ROOT="${CHART1_ENC_ROOT:-$ROOT_DIR/build/s52-chart1-source/s52/ECDIS_Chart_1/ENC_ROOT}"

if [[ ! -d "$CHART1_ENC_ROOT" ]]; then
  echo "Chart 1 ENC_ROOT directory not found: $CHART1_ENC_ROOT" >&2
  echo "Check out bareboat-necessities/s52-kotlin-webgl main to build/s52-chart1-source or set CHART1_ENC_ROOT." >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$INPUT_DIR"

# The browser demo imports NOAA-style ENC payload names from either individual
# .000/.001/... files or ZIP archives.  Chart 1's ENC_ROOT may also contain
# CATALOG metadata; keep the snapshot input focused on actual cell/update files.
(
  cd "$CHART1_ENC_ROOT"
  find . -type f \
    -regextype posix-extended \
    -iregex '.*/[^/]+\.[0-9][0-9][0-9]' \
    ! -iname 'CATALOG.*' \
    | sed 's#^./##' \
    | sort > "$ROOT_DIR/$CHART1_FILE_LIST"
)

if [[ ! -s "$CHART1_FILE_LIST" ]]; then
  echo "No ENC .000/.001/... payload files found under $CHART1_ENC_ROOT" >&2
  find "$CHART1_ENC_ROOT" -maxdepth 4 -type f | sort | head -80 >&2 || true
  exit 1
fi

(
  cd "$CHART1_ENC_ROOT"
  zip -q "$ROOT_DIR/$CHART1_ZIP" -@ < "$ROOT_DIR/$CHART1_FILE_LIST"
)

if [[ ! -s "$CHART1_ZIP" ]]; then
  echo "Failed to create Chart 1 ENC ZIP: $CHART1_ZIP" >&2
  exit 1
fi

echo "Created Chart 1 ENC snapshot input: $CHART1_ZIP"
echo "Chart 1 ENC payloads:"
sed 's/^/  /' "$CHART1_FILE_LIST"

if [[ -n "${PHASE26_APP_DIR:-}" ]]; then
  APP_DIR="$PHASE26_APP_DIR"
else
  # Reuse the app assembled by the regular ENC snapshot when the workflow runs
  # both snapshots.  Otherwise assemble the app from the current Kotlin/JS build.
  if [[ -d "build/ci-enc-snapshot/app" ]]; then
    APP_DIR="build/ci-enc-snapshot/app"
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
    APP_DIR="$OUT_DIR/app"
    mkdir -p "$APP_DIR"
    if [[ -d "$RESOURCE_DIR" ]]; then
      rsync -a "$RESOURCE_DIR/" "$APP_DIR/"
    fi
    rsync -a "$JS_DIR/" "$APP_DIR/"
  fi
fi

if [[ ! -f "$APP_DIR/index.html" || ! -f "$APP_DIR/demo.js" ]]; then
  echo "Snapshot app directory must contain index.html and demo.js: $APP_DIR" >&2
  find "$APP_DIR" -maxdepth 3 -type f | sort | head -80 >&2 || true
  exit 1
fi

(
  cd tools/ci-render-snapshot
  npm install
  # Use regular Chromium under Xvfb like the Statue of Liberty snapshot; Chart 1
  # should fail rather than publish a blank image when WebGL2 is unavailable.
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
        --enc-file="$ROOT_DIR/$CHART1_ZIP" \
        --out-dir="$ROOT_DIR/$OUT_DIR" \
        --snapshot-fractions="${CHART1_SNAPSHOT_FRACTIONS:-overview=1,comparison=0.70,detail=0.45}" \
        --headless=false \
        --browser-channel=chromium \
        --gl-mode=auto
  else
    echo "xvfb-run is not available; running Chromium new-headless Chart 1 snapshot. The snapshot will fail instead of publishing a blank render.png if WebGL2 is unavailable." >&2
    PHASE26_BROWSER_CHANNEL=chromium PHASE26_GL_MODE=auto npm run snapshot -- \
      --app-dir="$ROOT_DIR/$APP_DIR" \
      --enc-file="$ROOT_DIR/$CHART1_ZIP" \
      --out-dir="$ROOT_DIR/$OUT_DIR" \
      --snapshot-fractions="${CHART1_SNAPSHOT_FRACTIONS:-overview=1,comparison=0.70,detail=0.45}" \
      --browser-channel=chromium \
      --gl-mode=auto
  fi
)

test -s "$OUT_DIR/render.png"
test -s "$OUT_DIR/diagnostics.json"
test -s "$OUT_DIR/summary.txt"
