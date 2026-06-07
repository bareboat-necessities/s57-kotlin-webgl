plugins {
    kotlin("multiplatform") version "2.0.21" apply false
}

allprojects {
    group = "io.github.s57"
    version = "0.1.0-SNAPSHOT"
}


tasks.register("phase0Audit") {
    group = "verification"
    description = "Checks Phase 0 repository skeleton, scope documents, modules, and browser demo entry point."

    doLast {
        val requiredFiles = listOf(
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
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 0 files: $missing" }

        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        check("s57-kotlin-webgl" in readme) { "README must name the project." }
        check("Not for navigation" in readme) { "README must keep the navigation safety boundary." }
        check("out of scope" in readme.lowercase()) { "README must document out-of-scope chartplotter features." }
    }
}

tasks.register("phase0Check") {
    group = "verification"
    description = "Runs Phase 0 build and repository skeleton audit."
    dependsOn(
        ":s57-iso8211:build",
        ":s57-core:build",
        ":s57-index:build",
        ":s57-s52-adapter:build",
        ":s57-render-webgl:build",
        ":demo:build",
        "phase0Audit"
    )
}


tasks.register("phase1Audit") {
    group = "verification"
    description = "Checks Phase 1 interaction, camera, tilt, crosshair, and depth-mesh contracts."

    doLast {
        val requiredFiles = listOf(
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/InteractionModels.kt",
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/DepthMeshModels.kt",
            "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartInput.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/InteractionModelsTest.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/DepthMeshModelsTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 1 files: $missing" }

        val scope = layout.projectDirectory.file("docs/SCOPE.md").asFile.readText()
        check("center crosshair" in scope.lowercase()) { "Scope must document center-crosshair queries." }
        check("depth-based 3d mesh" in scope.lowercase() || "depth mesh" in scope.lowercase()) { "Scope must document depth mesh rendering contracts." }
        check("AIS" in scope && "out of scope" in scope.lowercase()) { "Scope must keep AIS out of this project." }
    }
}

tasks.register("phase1Check") {
    group = "verification"
    description = "Runs Phase 1 interaction/render-contract checks and all Phase 0 checks."
    dependsOn("phase0Check", "phase1Audit", ":s57-render-webgl:build", ":demo:build")
}


tasks.register("phase2Audit") {
    group = "verification"
    description = "Checks Phase 2 ISO8211 parser and diagnostic tooling."

    doLast {
        val requiredFiles = listOf(
            "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211Reader.kt",
            "s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211RecordDump.kt",
            "s57-iso8211/src/jvmMain/kotlin/io/github/s57/iso8211/Iso8211DumpMain.kt",
            "s57-iso8211/src/commonTest/kotlin/io/github/s57/iso8211/Iso8211ReaderTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 2 files: $missing" }

        val reader = layout.projectDirectory.file("s57-iso8211/src/commonMain/kotlin/io/github/s57/iso8211/Iso8211Reader.kt").asFile.readText()
        check("Iso8211DirectoryEntry" in reader) { "Phase 2 reader must expose directory entries." }
        check("Iso8211Subfield" in reader) { "Phase 2 reader must expose subfield chunks." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 2 — ISO8211 parser" in phases) { "docs/PHASES.md must document Phase 2." }
    }
}

tasks.register("phase2Check") {
    group = "verification"
    description = "Runs Phase 2 ISO8211 parser checks and all previous phase checks."
    dependsOn("phase1Check", "phase2Audit", ":s57-iso8211:build")
}

tasks.register("phase3Audit") {
    group = "verification"
    description = "Checks Phase 3 S-57 raw decoder, metadata, feature/vector records, and diagnostics."

    doLast {
        val requiredFiles = listOf(
            "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawModels.kt",
            "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDecoder.kt",
            "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57CatalogLookups.kt",
            "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDump.kt",
            "s57-core/src/jvmMain/kotlin/io/github/s57/core/raw/S57RawDumpMain.kt",
            "s57-core/src/commonTest/kotlin/io/github/s57/core/raw/S57RawDecoderTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 3 files: $missing" }

        val decoder = layout.projectDirectory.file("s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDecoder.kt").asFile.readText()
        check("FRID" in decoder) { "Phase 3 decoder must decode S-57 FRID feature records." }
        check("VRID" in decoder) { "Phase 3 decoder must decode S-57 VRID vector records." }
        check("ATTF" in decoder) { "Phase 3 decoder must decode S-57 ATTF attributes." }
        check("FSPT" in decoder) { "Phase 3 decoder must preserve S-57 feature-to-spatial references." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 3 — S-57 raw decoder" in phases) { "docs/PHASES.md must document Phase 3." }
    }
}

tasks.register("phase3Check") {
    group = "verification"
    description = "Runs Phase 3 S-57 raw decoder checks and all previous phase checks."
    dependsOn("phase2Check", "phase3Audit", ":s57-core:build")
}


tasks.register("phase4Audit") {
    group = "verification"
    description = "Checks Phase 4 S-57 geometry reconstruction, bounds, and diagnostics."

    doLast {
        val requiredFiles = listOf(
            "s57-core/src/commonMain/kotlin/io/github/s57/core/geometry/S57GeometryBuilder.kt",
            "s57-core/src/commonTest/kotlin/io/github/s57/core/geometry/S57GeometryBuilderTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 4 files: $missing" }

        val builder = layout.projectDirectory.file("s57-core/src/commonMain/kotlin/io/github/s57/core/geometry/S57GeometryBuilder.kt").asFile.readText()
        check("S57GeometryBuilder" in builder) { "Phase 4 must include S57GeometryBuilder." }
        check("S57Geometry.Polygon" in builder) { "Phase 4 must reconstruct area polygons." }
        check("S57Geometry.LineString" in builder) { "Phase 4 must reconstruct line strings." }
        check("S57GeometryDiagnostic" in builder) { "Phase 4 must expose geometry diagnostics." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 4 — geometry reconstruction" in phases) { "docs/PHASES.md must document Phase 4." }
    }
}

tasks.register("phase4Check") {
    group = "verification"
    description = "Runs Phase 4 geometry reconstruction checks and all previous phase checks."
    dependsOn("phase3Check", "phase4Audit", ":s57-core:build")
}

tasks.register("phase5Audit") {
    group = "verification"
    description = "Checks Phase 5 IndexedDB-oriented chart cache, spatial bins, and viewport query contracts."

    doLast {
        val requiredFiles = listOf(
            "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexModels.kt",
            "s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexStore.kt",
            "s57-index/src/jsMain/kotlin/io/github/s57/index/BrowserIndexedDbS57IndexStore.kt",
            "s57-index/src/commonTest/kotlin/io/github/s57/index/S57IndexStoreTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 5 files: $missing" }

        val store = layout.projectDirectory.file("s57-index/src/commonMain/kotlin/io/github/s57/index/S57IndexStore.kt").asFile.readText()
        check("importDataset" in store) { "Phase 5 store must import decoded datasets." }
        check("S57FeatureQuery" in store) { "Phase 5 store must support structured feature queries." }

        val browser = layout.projectDirectory.file("s57-index/src/jsMain/kotlin/io/github/s57/index/BrowserIndexedDbS57IndexStore.kt").asFile.readText()
        check("IndexedDB" in browser) { "Phase 5 must include a browser IndexedDB boundary." }
        check("spatialBins" in browser) { "Phase 5 IndexedDB schema must include spatialBins." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 5 — browser indexing" in phases) { "docs/PHASES.md must document Phase 5." }
    }
}

tasks.register("phase5Check") {
    group = "verification"
    description = "Runs Phase 5 browser indexing checks and all previous phase checks."
    dependsOn("phase4Check", "phase5Audit", ":s57-index:build")
}



tasks.register("phase6Audit") {
    group = "verification"
    description = "Checks Phase 6 S-57 to S-52 adapter and release Maven dependency wiring."

    doLast {
        val requiredFiles = listOf(
            "s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt",
            "s57-s52-adapter/src/commonTest/kotlin/io/github/s57/adapter/S57ToS52AdapterTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 6 files: $missing" }

        val adapterBuild = layout.projectDirectory.file("s57-s52-adapter/build.gradle.kts").asFile.readText()
        check("api(project(\":s57-core\"))" in adapterBuild) { "s57-s52-adapter common module must stay JS-safe and depend on s57-core." }
        check("io.github.s52" !in adapterBuild) { "S-52 artifacts must not be compiled from s57-s52-adapter common/JS source sets." }

        val adapter = layout.projectDirectory.file("s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt").asFile.readText()
        check("S57PortrayalFeature" in adapter) { "Phase 6 adapter must produce S-52-shaped local portrayal feature objects." }
        check("S57PortrayalPrimitive" in adapter) { "Phase 6 adapter must preserve S-52 primitive shape." }
        check("S57PortrayalGeometry" in adapter) { "Phase 6 adapter must preserve S-52 geometry shape." }
        check("transcript" in adapter) { "Phase 6 adapter must expose transcript diagnostics." }

        val ci = layout.projectDirectory.file(".github/workflows/ci.yml").asFile.readText()
        check("s52-kotlin-webgl-release-maven-0.3.0.zip" in ci) { "CI should keep downloading the s52-kotlin-webgl v0.3.0 Maven release ZIP." }
        check("ed4ece6664670fec275f1d3d8d2ff52f1dfa54384501ebd97553c670e9687a79" in ci) { "CI must verify the s52 Maven release checksum." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 6 — S-52 adapter" in phases) { "docs/PHASES.md must document Phase 6." }
    }
}

tasks.register("phase6Check") {
    group = "verification"
    description = "Runs Phase 6 real S-52 adapter checks and all previous phase checks. Requires the s52 v0.3.0 Maven release repository."
    dependsOn("phase5Check", "phase6Audit", ":s57-s52-adapter:build")
}

tasks.register("phase7Audit") {
    group = "verification"
    description = "Checks Phase 7 static chart frame projection, hit testing, and WebGL draw-shell integration."

    doLast {
        val requiredFiles = listOf(
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/ChartProjection.kt",
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/StaticChartFrame.kt",
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57StaticChartRenderer.kt",
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/DepthSamples.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/ChartProjectionTest.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/S57StaticChartRendererTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 7 files: $missing" }

        val build = layout.projectDirectory.file("build.gradle.kts").asFile.readText()
        check("subprojects {\n    repositories" !in build) { "Repositories must be declared in settings.gradle.kts, not in project build.gradle.kts." }

        val renderer = layout.projectDirectory.file("s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57StaticChartRenderer.kt").asFile.readText()
        check("S57StaticChartRenderer" in renderer) { "Phase 7 must include the static chart renderer." }
        check("S57FeatureQuery" in renderer) { "Phase 7 renderer must query the Phase 5 index." }

        val browser = layout.projectDirectory.file("s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt").asFile.readText()
        check("renderFrame" in browser) { "Browser renderer must draw a Phase 7 StaticChartFrame." }
        check("TRIANGLE_FAN" in browser) { "Browser renderer must include basic polygon drawing." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 7 — static WebGL chart render" in phases) { "docs/PHASES.md must document Phase 7." }
    }
}

tasks.register("phase7Check") {
    group = "verification"
    description = "Runs Phase 7 static WebGL chart render checks and all previous phase checks."
    dependsOn("phase6Check", "phase7Audit", ":s57-render-webgl:build", ":demo:build")
}


tasks.register("phase8Audit") {
    group = "verification"
    description = "Checks Phase 8 rendered artifact diagnostics and Gradle repository-mode compatibility with Kotlin/JS Node setup."

    doLast {
        val requiredFiles = listOf(
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactDiagnostics.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/RenderedArtifactDiagnosticsTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 8 files: $missing" }

        val settings = layout.projectDirectory.file("settings.gradle.kts").asFile.readText()
        check("RepositoriesMode.PREFER_PROJECT" in settings) { "settings.gradle.kts must allow Kotlin/JS Node setup to add its Node distribution repository." }

        val diagnostics = layout.projectDirectory.file("s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/RenderedArtifactDiagnostics.kt").asFile.readText()
        check("RenderedArtifactReport" in diagnostics) { "Phase 8 must expose RenderedArtifactReport." }
        check("toSvgSnapshot" in diagnostics) { "Phase 8 must expose SVG snapshot generation." }
        check("validateMinimum" in diagnostics) { "Phase 8 diagnostics must validate empty/fallback-heavy render artifacts." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 8 — rendered artifact diagnostics" in phases) { "docs/PHASES.md must document Phase 8." }
    }
}

tasks.register("phase8Check") {
    group = "verification"
    description = "Runs Phase 8 rendered artifact diagnostics checks and all previous phase checks."
    dependsOn("phase7Check", "phase8Audit", ":s57-render-webgl:build")
}

tasks.register("phase9Audit") {
    group = "verification"
    description = "Checks Phase 9 high-level engine facade and S-52 adapter boundary."

    doLast {
        val requiredFiles = listOf(
            "s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57WebGlEngine.kt",
            "s57-render-webgl/src/commonTest/kotlin/io/github/s57/render/S57WebGlEngineTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 9 files: $missing" }

        val engine = layout.projectDirectory.file("s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57WebGlEngine.kt").asFile.readText()
        check("class S57WebGlEngine" in engine) { "Phase 9 must expose S57WebGlEngine." }
        check("importDataset" in engine) { "Phase 9 engine must expose dataset import." }
        check("centerCrosshairHits" in engine) { "Phase 9 engine must expose center-crosshair query." }
        check("RenderedArtifactDiagnostics" in engine) { "Phase 9 engine must expose render diagnostics." }

        val adapter = layout.projectDirectory.file("s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt").asFile.readText()
        check("io.github.s52" !in adapter) { "Common adapter must remain JS-safe and avoid direct S-52 imports." }
        check("S57PortrayalFeature" in adapter) { "Common adapter must keep the S-52-shaped local portrayal boundary." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 9 — high-level engine facade" in phases) { "docs/PHASES.md must document Phase 9." }
    }
}

tasks.register("phase9Check") {
    group = "verification"
    description = "Runs Phase 9 engine facade checks and all previous phase checks."
    dependsOn("phase8Check", "phase9Audit", ":s57-render-webgl:build", ":demo:build")
}


tasks.register("phase10Audit") {
    group = "verification"
    description = "Checks Phase 10 end-to-end S-57 import pipeline and browser file-import boundary."

    doLast {
        val requiredFiles = listOf(
            "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt",
            "s57-core/src/commonTest/kotlin/io/github/s57/core/import/S57ImportPipelineTest.kt",
            "s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57FileImporter.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 10 files: $missing" }

        val input = layout.projectDirectory.file("s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserChartInput.kt").asFile.readText()
        check("fun dynamic." !in input) { "Kotlin/JS input code must not use dynamic extension receivers." }

        val browserRenderer = layout.projectDirectory.file("s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt").asFile.readText()
        check("Float32Array(raw)" in browserRenderer) { "Browser renderer must fill Float32Array through a Kotlin Array to satisfy Kotlin/JS typed-array APIs." }

        val engine = layout.projectDirectory.file("s57-render-webgl/src/commonMain/kotlin/io/github/s57/render/S57WebGlEngine.kt").asFile.readText()
        check("importS57Bytes" in engine) { "Phase 10 engine must expose importS57Bytes for browser file payloads." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 10 — end-to-end import pipeline" in phases) { "docs/PHASES.md must document Phase 10." }
    }
}

tasks.register("phase10Check") {
    group = "verification"
    description = "Runs Phase 10 import-pipeline checks and all previous phase checks."
    dependsOn("phase9Check", "phase10Audit", ":s57-core:build", ":s57-render-webgl:build", ":demo:build")
}

tasks.register("phase11Audit") {
    group = "verification"
    description = "Checks real s52-kotlin-webgl integration and S-52 WebGL render wiring."

    doLast {
        val adapterBuild = layout.projectDirectory.file("s57-s52-adapter/build.gradle.kts").asFile.readText()
        check("io.github.s52" !in adapterBuild) { "s57-s52-adapter must remain JS-safe; real S-52 integration belongs in the browser render module." }

        val rendererBuild = layout.projectDirectory.file("s57-render-webgl/build.gradle.kts").asFile.readText()
        check("io.github.s52:s52-api" in rendererBuild) { "s57-render-webgl JS must consume the S-52 API artifact." }
        check("io.github.s52:s52-core" in rendererBuild) { "s57-render-webgl JS must consume the S-52 core artifact." }
        check("io.github.s52:s52-render-webgl" in rendererBuild) { "s57-render-webgl must consume the S-52 WebGL renderer artifact." }

        val browserBridge = layout.projectDirectory.file("s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS52Bridge.kt").asFile.readText()
        check("io.github.s52.core.model.EncFeature" in browserBridge) { "Browser S-52 bridge must create real S-52 EncFeature values." }
        check("S52PortrayalSession" in browserBridge) { "Browser S-52 bridge must call the real S-52 portrayal session." }
        check("S52DrawCommand" in browserBridge) { "Browser S-52 bridge must expose real S-52 draw commands." }

        val browserRenderer = layout.projectDirectory.file("s57-render-webgl/src/jsMain/kotlin/io/github/s57/render/BrowserS57WebGlRenderer.kt").asFile.readText()
        check("WebGlS52Renderer" in browserRenderer) { "Browser renderer must call the real S-52 WebGL renderer." }
        check("renderS52Frame" in browserRenderer) { "Browser renderer must expose an S-52 rendering entry point." }

        val demo = layout.projectDirectory.file("demo/src/jsMain/kotlin/io/github/s57/demo/Main.kt").asFile.readText()
        check("renderS52Frame" in demo) { "Demo must use the real S-52 rendering path." }
    }
}

tasks.register("phase11Check") {
    group = "verification"
    description = "Runs Phase 11 real S-52 integration checks and all previous phase checks."
    dependsOn("phase10Check", "phase11Audit", ":s57-s52-adapter:build", ":s57-render-webgl:build", ":demo:build")
}
