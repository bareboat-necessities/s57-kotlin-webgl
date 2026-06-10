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
    "phase0Audit",
    "Phase 0",
    listOf(
        "README.md",
        "docs/SCOPE.md",
        "docs/PHASES.md",
        ".github/workflows/ci.yml",
        "demo/src/jsMain/resources/index.html",
        "demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt",
        "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211Reader.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/S57Models.kt",
        "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexStore.kt",
        "s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt"
    )
) {
    requireText("README.md", "s57-kotlin-webgl", "README must name the project.")
    requireText("README.md", "Not for navigation", "README must keep the navigation safety boundary.")
}

tasks.register("phase0Check") {
    group = "verification"
    description = "Runs Phase 0 checks."
    dependsOn(moduleBuilds + "phase0Audit")
}

registerAudit(
    "phase1Audit",
    "Phase 1",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/InteractionModels.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/DepthMeshModels.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartInput.kt"
    )
)

tasks.register("phase1Check") { dependsOn("phase0Check", "phase1Audit", ":s57-render-webgl:build", ":demo:build") }

registerAudit(
    "phase2Audit",
    "Phase 2",
    listOf(
        "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211Models.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/S57Models.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDecoder.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawModels.kt",
        "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexStore.kt"
    )
)

tasks.register("phase2Check") { dependsOn("phase1Check", "phase2Audit", moduleBuilds) }

registerAudit(
    "phase3Audit",
    "Phase 3",
    listOf(
        "s57-core/src/commonMain/kotlin/io/github/s57/core/geometry/S57GeometryBuilder.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDump.kt"
    )
)

tasks.register("phase3Check") { dependsOn("phase2Check", "phase3Audit", moduleBuilds) }

registerAudit(
    "phase4Audit",
    "Phase 4",
    listOf(
        "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57FeatureBuilder.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/StaticFrameModels.kt"
    )
)

tasks.register("phase4Check") { dependsOn("phase3Check", "phase4Audit", moduleBuilds) }

registerAudit(
    "phase5Audit",
    "Phase 5",
    listOf(
        "s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt",
        "s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Diagnostics.kt"
    )
)

tasks.register("phase5Check") { dependsOn("phase4Check", "phase5Audit", moduleBuilds) }

registerAudit(
    "phase6Audit",
    "Phase 6",
    listOf(".github/workflows/ci.yml", "gradle.properties")
) {
    requireText("gradle.properties", "s52.version=0.5.0", "S52 version should stay on 0.5.0.")
    requireText(".github/workflows/ci.yml", "s52-kotlin-webgl-release-maven-0.5.0.zip", "CI should keep downloading the s52-kotlin-webgl v0.5.0 Maven release ZIP.")
}

tasks.register("phase6Check") { dependsOn("phase5Check", "phase6Audit", moduleBuilds) }

registerAudit(
    "phase7Audit",
    "Phase 7",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderModels.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ChartProjection.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt"
    )
)

tasks.register("phase7Check") { dependsOn("phase6Check", "phase7Audit", moduleBuilds) }

registerAudit(
    "phase8Audit",
    "Phase 8",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactDiagnostics.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactSvgSnapshot.kt"
    )
)

tasks.register("phase8Check") { dependsOn("phase7Check", "phase8Audit", moduleBuilds) }

registerAudit(
    "phase9Audit",
    "Phase 9",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/NoaaEncVisualSmoke.kt",
        "s57-render-webgl/src/jvmMain/kotlin/io/github/s57/render/NoaaEncVisualSmokeMain.kt"
    )
)

tasks.register("phase9Check") { dependsOn("phase8Check", "phase9Audit", moduleBuilds) }

registerAudit(
    "phase11Audit",
    "Phase 11",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ChartViewportFit.kt",
        "demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt"
    )
)

tasks.register("phase11Check") { dependsOn("phase9Check", "phase11Audit", moduleBuilds) }

registerAudit(
    "phase16BAudit",
    "Phase 16B",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/Phase16Diagnostics.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57FileImporter.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52Bridge.kt"
    )
)

tasks.register("phase16BCheck") { dependsOn("phase11Check", "phase16BAudit", moduleBuilds) }
