# S-52 0.5.5 upgrade without checksum enforcement

This incremental patch switches the S-57 browser chartplotter integration from
S-52 `0.5.4` to S-52 `0.5.5`.

Changes:

- Updates `gradle.properties` to `s52.version=0.5.5`.
- Updates the Phase 6 audit guard to require the `0.5.5` Maven release ZIP.
- Adds the local Maven repository fallback path for
  `s52-kotlin-webgl-release-maven-0.5.5` while keeping older fallback paths.
- Updates GitHub Actions to download the `v0.5.5` Maven release ZIP and the
  `v0.5.5` symbology source ZIP.
- Removes checksum verification from the S-52 download steps, per request.
- Keeps the S-52 source WebGL2 cast patch step, so composite source builds still
  receive the local WebGL2-safe constructor patch when needed.

No renderer fallback behavior is changed by this patch.
