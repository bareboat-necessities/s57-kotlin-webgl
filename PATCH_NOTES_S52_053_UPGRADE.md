# S-52 0.5.3 upgrade

This incremental patch switches the S-57 browser chartplotter integration from `bareboat-necessities/s52-kotlin-webgl` `v0.5.2` to `v0.5.3`.

## Why

S-52 `0.5.3` contains the WebGL2 Kotlin/JS context fix in `WebGlS52Renderer`, so the S-57 project should consume the release directly instead of patching S-52 `0.5.2` in CI.

## Changes

- Updates `s52.version` to `0.5.3`.
- Updates GitHub Actions to download `v0.5.3` Maven and source release artifacts.
- Uses the provided SHA256 values:
  - `s52-kotlin-webgl-release-maven-0.5.3.zip`: `0baaa43302e1b11326bae73957dddb2099918bb3b326f8cddb8c72c618fd1cc0`
  - `s52-kotlin-webgl-0.5.3-symbology-source.zip`: `693c5bf26eaceef3bbaa33f84406d7dca738fd831d496d997f7b8edea035b601`
- Adds the local Maven repository fallback path for `s52-kotlin-webgl-release-maven-0.5.3`.
- Removes the CI call to the old S-52 `0.5.2` WebGL2 cast patch step.
- Keeps the no-runtime-raster-download path: S-52 symbology stays supplied by Kotlin artifacts/source.

## Note for cleanup

If `.github/scripts/patch-s52-052-webgl2-cast.sh` exists in your working tree from the previous incremental patch, it is now obsolete and no longer called by CI. It can be deleted from git after applying this patch.
