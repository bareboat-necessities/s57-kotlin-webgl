# S-52 0.5.4 upgrade

This incremental patch switches the S-57 browser chartplotter integration from `bareboat-necessities/s52-kotlin-webgl` `v0.5.3` to `v0.5.4`.

## Changes

- Updates `s52.version` to `0.5.4`.
- Updates GitHub Actions to download and verify the `v0.5.4` Maven and source release artifacts.
- Uses the supplied release checksums:
  - `s52-kotlin-webgl-release-maven-0.5.4.zip`: `5d1b7ff9071c27800fa3d36e77b3da83fdfe22afbab29779aa99171a1856ac5b`
  - `s52-kotlin-webgl-0.5.4-symbology-source.zip`: `064f626364af548d51a00a5897af17ff1d4d0807f83013d81767cd7f6ea71816`
- Adds the local Maven repository fallback path for `s52-kotlin-webgl-release-maven-0.5.4` while keeping older fallback paths.
- Keeps S-52 symbology Kotlin/Maven/composite-build based; no runtime raster atlas or symbol image ZIP is downloaded.
