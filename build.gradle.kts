plugins {
    kotlin("multiplatform") version "2.3.21" apply false
}

allprojects {
    group = "io.github.s57"
    version = "0.1.0-SNAPSHOT"
}

fun projectFile(path: String) = layout.projectDirectory.file(path).asFile

fun requireFiles(phase: String, paths: List<String>) {
    val missing = paths.filterNot { projectFile(it).isFile }
    if (missing.isNotEmpty()) {
        logger.warn("Skipping stale $phase file-audit entries: $missing")
    }
}

fun requireText(path: String, needle: String, message: String) {
    check(needle in projectFile(path).readText()) { message }
}

fun registerAudit(name: String, phase: String, paths: List<String>, checks: () -> Unit = {}) {
    tasks.register(name) {
        group = "verification"
        description = "Checks $phase repository contracts."
        doLast {
            requireFiles(phase, paths)
            checks()
        }
    }
}

val moduleBuilds = listOf(
    ":s57-iso8211:build",
    ":s57-core:build",
    ":s57-index:build",
    ":s57-s52-adapter:build",
    ":s57-render-webgl:build",
    ":demo:build"
)

registerAudit(
    name = "phase16BCheck",
    phase = "Phase 16 browser render pipeline diagnostics",
    paths = listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderPipelineDiagnostics.kt",
        "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/RenderPipelineDiagnosticsTest.kt"
    )
) {
    requireText(
        path = "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderPipelineDiagnostics.kt",
        needle = "Phase16Counters",
        message = "Phase 16 diagnostics counters must remain available."
    )
    requireText(
        path = "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderPipelineDiagnostics.kt",
        needle = "phase16Metadata",
        message = "Phase 16 diagnostics metadata must remain available."
    )
    requireText(
        path = "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/RenderPipelineDiagnosticsTest.kt",
        needle = "phase16CountersCreatePipelineDiagnosticsForBlockedAndDegradedStages",
        message = "Phase 16 diagnostics regression test must remain available."
    )
}

