#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${PHASE26_ENC_OUT_DIR:-build/ci-enc-snapshot/input}"
mkdir -p "$OUT_DIR"
rm -rf "$OUT_DIR/extract"
mkdir -p "$OUT_DIR/extract"

DEFAULT_URLS=(
  "https://charts.noaa.gov/ENCs/US5MA11M.zip"
  "https://charts.noaa.gov/ENCs/US5NY1CE.zip"
  "https://charts.noaa.gov/ENCs/US5NY1BM.zip"
  "https://charts.noaa.gov/ENCs/US5NY1AM.zip"
)

urls=()
if [[ -n "${PHASE26_ENC_URLS:-}" ]]; then
  # Accept either whitespace-separated or newline-separated override values.
  while IFS= read -r url; do
    [[ -n "$url" ]] && urls+=("$url")
  done < <(printf '%s\n' "$PHASE26_ENC_URLS" | tr ' ' '\n')
else
  urls=("${DEFAULT_URLS[@]}")
fi

try_url() {
  local url="$1"
  local zip_path="$OUT_DIR/candidate.zip"
  echo "Trying Phase 26 ENC ZIP: $url"
  rm -f "$zip_path"
  rm -rf "$OUT_DIR/extract"
  mkdir -p "$OUT_DIR/extract"
  if ! curl -fL --retry 3 --connect-timeout 20 --max-time 180 "$url" -o "$zip_path"; then
    return 1
  fi
  unzip -q "$zip_path" -d "$OUT_DIR/extract"
  local enc_file
  enc_file="$(find "$OUT_DIR/extract" -type f -iname '*.000' | sort | head -1 || true)"
  if [[ -z "$enc_file" || ! -s "$enc_file" ]]; then
    echo "No non-empty .000 ENC file found in $url" >&2
    return 1
  fi
  cp "$enc_file" "$OUT_DIR/cell.000"
  printf '%s\n' "$url" > "$OUT_DIR/selected-url.txt"
  printf '%s\n' "$enc_file" > "$OUT_DIR/extracted-enc-path.txt"
  cat > "$OUT_DIR/metadata.json" <<JSON
{"selectedUrl":"$url","extractedEncFile":"$enc_file","encFile":"$OUT_DIR/cell.000"}
JSON
  echo "Selected Phase 26 ENC: $enc_file from $url"
  return 0
}

for url in "${urls[@]}"; do
  if try_url "$url"; then
    exit 0
  fi
done

echo "Could not download a usable NOAA ENC cell. Set PHASE26_ENC_URLS to one or more NOAA ENC ZIP URLs." >&2
exit 1
