# S-52 0.5.2 no-runtime-raster-download fix

This incremental patch removes the legacy OpenCPN `rastersymbols-*.png` runtime-resource path from the S-57 demo and CI scripts.

S-52 `0.5.2` supplies the presentation catalogue and symbol/color handling through Kotlin artifacts/composite build code.  The `s52-kotlin-webgl-symbology-images-0.5.2-critical.zip` release contains generated SVG inspection assets, not the old `s52/opencpn/rastersymbols-{day,dusk,dark}.png` sprite atlas.

Changes:

- Removed the CI download/check for `s52-kotlin-webgl-symbology-images-0.5.2-critical.zip`.
- Removed Gradle runtime atlas download logic from `demo/build.gradle.kts`.
- Removed packaging/snapshot checks that required `rastersymbols-day.png`, `rastersymbols-dusk.png`, and `rastersymbols-dark.png`.
- Removed the Playwright atlas fetch assertion and the async raster redraw wait that caused CI timeouts.
- Kept S-52 `0.5.2` Maven/source setup and the hard snapshot requirement that S-52 must produce commands.

The Maven/source downloads remain only to provide the S-52 Kotlin dependencies to the build; no browser runtime symbol image download is required.
