plugins {
    kotlin("multiplatform") version "2.0.21" apply false
}

allprojects {
    group = "io.github.s57"
    version = "0.1.0-SNAPSHOT"
}

fun projectFile(path: String) = layout.projectDirectory.file(path).asFile

fun requireFiles(phase: String, paths: List<String>) {
    val missing = paths.filterNot { projectFile(it).isFile }
    check(missing.isEmpty()) { "Missing $phase files: $missing" }
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
        "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211Reader.kt",
        "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211RecordDump.kt",
        "s57-iso8211/src/jvmMain/kotlin/io/github/s57/iso8211/Iso8211DumpMain.kt"
    )
)

tasks.register("phase2Check") { dependsOn("phase1Check", "phase2Audit", ":s57-iso8211:build") }

registerAudit(
    "phase3Audit",
    "Phase 3",
    listOf(
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawModels.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDecoder.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt",
        "s57-core/src/commonTest/kotlin/io/github/s57/core/raw/S57RawDecoderTest.kt"
    )
)

tasks.register("phase3Check") { dependsOn("phase2Check", "phase3Audit", ":s57-core:build") }

registerAudit(
    "phase4Audit",
    "Phase 4",
    listOf(
        "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt",
        "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57FeatureBuilder.kt"
    )
)

tasks.register("phase4Check") { dependsOn("phase3Check", "phase4Audit", ":s57-core:build") }

registerAudit(
    "phase5Audit",
    "Phase 5",
    listOf(
        "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexModels.kt",
        "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexStore.kt",
        "s57-index/src/jsMain/kotlin/io/github/s57/index/BrowserIndexedDbS57IndexStore.kt"
    )
)

tasks.register("phase5Check") { dependsOn("phase4Check", "phase5Audit", ":s57-index:build") }

registerAudit(
    "phase6Audit",
    "Phase 6",
    listOf(
        "s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt",
        "s57-s52-adapter/src/commonTest/kotlin/io/github/s57/adapter/S57ToS52AdapterTest.kt"
    )
) {
    val ci = projectFile(".github/workflows/ci.yml").readText()
    val version = providers.gradleProperty("s52.version").orElse("0.5.0").get()
    check("S52_VERSION: \"$version\"" in ci) { "CI should use the configured S-52 version $version." }
    check("s52-kotlin-webgl-release-maven-$version.zip" in ci) { "CI should download the configured S-52 Maven release ZIP." }
    check("s52-kotlin-webgl-$version-phase22-source.zip" in ci) { "CI should download the configured S-52 source ZIP." }
    check("S52_RELEASE_MAVEN_SHA256" in ci && "S52_RELEASE_SOURCE_SHA256" in ci) { "CI must verify S-52 release checksums." }
}

tasks.register("phase6Check") { dependsOn("phase5Check", "phase6Audit", ":s57-s52-adapter:build") }

registerAudit(
    "phase7Audit",
    "Phase 7",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ChartProjection.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/StaticChartFrame.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57StaticChartRenderer.kt"
    )
)

tasks.register("phase7Check") { dependsOn("phase6Check", "phase7Audit", ":s57-render-webgl:build") }

registerAudit(
    "phase8Audit",
    "Phase 8",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactDiagnostics.kt",
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactSvgSnapshot.kt"
    )
)

tasks.register("phase8Check") { dependsOn("phase7Check", "phase8Audit", ":s57-render-webgl:build") }

registerAudit(
    "phase9Audit",
    "Phase 9",
    listOf(
        "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57WebGlEngine.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57FileImporter.kt"
    )
)

tasks.register("phase9Check") { dependsOn("phase8Check", "phase9Audit", ":s57-render-webgl:build") }

registerAudit(
    "phase10Audit",
    "Phase 10",
    listOf(
        "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57FileImporter.kt"
    )
)

tasks.register("phase10Check") { dependsOn("phase9Check", "phase10Audit", ":s57-core:build", ":s57-render-webgl:build") }

registerAudit(
    "phase11Audit",
    "Phase 11",
    listOf(
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52Bridge.kt",
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt"
    )
)

tasks.register("phase11Check") { dependsOn("phase10Check", "phase11Audit", ":s57-render-webgl:build", ":demo:build") }

registerAudit(
    "phase16BAudit",
    "Phase 16B",
    listOf(
        "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52StructuredRender.kt",
        "demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt"
    )
)

tasks.register("phase16BCheck") { dependsOn("phase11Check", "phase16BAudit", ":s57-render-webgl:build", ":demo:build") }
