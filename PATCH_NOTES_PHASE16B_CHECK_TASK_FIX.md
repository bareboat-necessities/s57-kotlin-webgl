# Phase 16 CI task selection fix

This patch fixes the CI failure where Gradle is invoked with `phase16BCheck`,
but the attached baseline project did not define any task with that name.

## Fixed

- Adds a root `phase16BCheck` verification task in `build.gradle.kts`.
- The task verifies the existing Phase 16 render-pipeline diagnostics contract:
  - `RenderPipelineDiagnostics.kt` exists.
  - `Phase16Counters` remains present.
  - `phase16Metadata` remains present.
  - the Phase 16 diagnostics regression test remains present.
- Aligns CI S-52 release download URLs with the already configured
  `S52_VERSION=0.5.5` / `s52.version=0.5.5` baseline.
- Does not add checksum enforcement.

The error was task selection, not source compilation. The CI command can keep
calling `phase16BCheck` directly.
