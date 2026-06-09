# External S-52 browser resources

The S-57 browser demo uses S-52 WebGL resources from the external `bareboat-necessities/s52-kotlin-webgl` project.

Do not commit OpenCPN raster symbol atlas PNG files into this repository. They are resolved from an external checkout at build time.

## Local checkout layout

The simplest local layout is:

```text
workspace/
  s57-kotlin-webgl/
  s52-kotlin-webgl/
```

With that layout, the demo build uses `../s52-kotlin-webgl` automatically.

For another layout, pass:

```text
-Ps52SourceDir=/path/to/s52-kotlin-webgl
```

or set:

```text
S52_SOURCE_DIR=/path/to/s52-kotlin-webgl
```

The expected resource directory is:

```text
s52-render-webgl/src/jsMain/resources/s52/opencpn
```

It should contain:

```text
rastersymbols-day.png
rastersymbols-dusk.png
rastersymbols-dark.png
```

## Checks

Run:

```text
./gradlew :demo:phase12_13_14BCheck -Ps52SourceDir=/path/to/s52-kotlin-webgl
```

This verifies that the external S-52 atlas resources exist and that the S-57 demo does not vendor atlas files under `demo/src/jsMain/resources/s52/opencpn`.

Then build the demo:

```text
./gradlew :demo:build -Ps52SourceDir=/path/to/s52-kotlin-webgl
```
