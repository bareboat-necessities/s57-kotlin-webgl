#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

ARTIFACT_ROOT="build/noaa-statue-liberty-demo"
APP_DIR="$ARTIFACT_ROOT/app"
DATA_DIR="$APP_DIR/data"
ZIP_PATH="build/noaa-statue-liberty-demo.zip"
mkdir -p "$APP_DIR" build

copy_s52_browser_resources() {
  local app_dir="$1"
  local candidates=()
  if [[ -n "${S52_SOURCE_DIR:-}" ]]; then
    candidates+=("$S52_SOURCE_DIR/s52-render-webgl/src/jsMain/resources")
    candidates+=("$S52_SOURCE_DIR/s52-render-webgl/build/processedResources/js/main")
  fi
  candidates+=("demo/src/jsMain/s52RuntimeResources")
  candidates+=("../s52-kotlin-webgl/s52-render-webgl/src/jsMain/resources")
  candidates+=("../s52-kotlin-webgl/s52-render-webgl/build/processedResources/js/main")
  candidates+=("demo/build/processedResources/js/main")
  candidates+=("build/s52-images")
  candidates+=("build/s52-maven")
  candidates+=("build/s52-source-unpacked")
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
rm -rf "$APP_DIR"
mkdir -p "$APP_DIR"
if [[ -d "$RESOURCE_DIR" ]]; then
  rsync -a "$RESOURCE_DIR/" "$APP_DIR/"
fi
rsync -a "$JS_DIR/" "$APP_DIR/"
copy_s52_browser_resources "$APP_DIR"
mkdir -p "$DATA_DIR"

# Keep the artifact self-describing even when opened outside GitHub Actions.
cat > "$APP_DIR/README.txt" <<'README'
S57 Kotlin WebGL NOAA Statue of Liberty demo

Serve this directory through a local web server, for example:
  python3 -m http.server 8080
Then open:
  http://localhost:8080/

The bundled NOAA ENC is copied to:
  data/statue-liberty.000

If the viewer does not auto-load bundled data, use the file picker and choose
that .000 file from this extracted artifact folder.
README

# The workflow may override this URL if NOAA changes cell naming.
NOAA_STATUE_LIBERTY_ENC_URL="${NOAA_STATUE_LIBERTY_ENC_URL:-}"
TMP_ENC_DIR="build/noaa-statue-liberty-enc"
rm -rf "$TMP_ENC_DIR"
mkdir -p "$TMP_ENC_DIR"

try_download_zip() {
  local url="$1"
  local out="$TMP_ENC_DIR/chart.zip"
  echo "Trying NOAA ENC ZIP: $url"
  if curl -fL --retry 3 --connect-timeout 20 --max-time 180 "$url" -o "$out"; then
    rm -rf "$TMP_ENC_DIR/extract"
    mkdir -p "$TMP_ENC_DIR/extract"
    unzip -q "$out" -d "$TMP_ENC_DIR/extract"
    local enc_file
    enc_file="$(find "$TMP_ENC_DIR/extract" -type f -iname '*.000' | sort | head -1 || true)"
    if [[ -n "$enc_file" && -s "$enc_file" ]]; then
      mkdir -p "$DATA_DIR"
      cp "$enc_file" "$DATA_DIR/statue-liberty.000"
      echo "Bundled NOAA ENC file: $enc_file"
      return 0
    fi
  fi
  return 1
}

if [[ -n "$NOAA_STATUE_LIBERTY_ENC_URL" ]]; then
  try_download_zip "$NOAA_STATUE_LIBERTY_ENC_URL" || {
    echo "NOAA_STATUE_LIBERTY_ENC_URL did not yield a .000 ENC: $NOAA_STATUE_LIBERTY_ENC_URL" >&2
    exit 1
  }
else
  # Candidate NOAA ENC cells for New York Harbor / Statue of Liberty vicinity.
  # NOAA occasionally renames/reissues cells; keep this list local to CI and let
  # NOAA_STATUE_LIBERTY_ENC_URL override it when needed.
  candidates=(
    "https://charts.noaa.gov/ENCs/US5NY1CE.zip"
    "https://charts.noaa.gov/ENCs/US5NY51M.zip"
    "https://charts.noaa.gov/ENCs/US5NY50M.zip"
    "https://charts.noaa.gov/ENCs/US5NY1BM.zip"
    "https://charts.noaa.gov/ENCs/US5NY1AM.zip"
    "https://charts.noaa.gov/ENCs/US4NY1AM.zip"
  )
  downloaded=0
  for url in "${candidates[@]}"; do
    if try_download_zip "$url"; then
      downloaded=1
      break
    fi
  done
  if [[ "$downloaded" != "1" ]]; then
    echo "Could not download a Statue of Liberty NOAA ENC candidate. Set NOAA_STATUE_LIBERTY_ENC_URL in the workflow to a NOAA ENC ZIP URL." >&2
    exit 1
  fi
fi

if [[ ! -f "$DATA_DIR/statue-liberty.000" ]]; then
  echo "Missing bundled data/statue-liberty.000" >&2
  exit 1
fi

rm -f "$ZIP_PATH"
(
  cd "$APP_DIR"
  zip -qr "$ROOT_DIR/$ZIP_PATH" .
)

echo "Created $ZIP_PATH"
ls -lh "$ZIP_PATH"
