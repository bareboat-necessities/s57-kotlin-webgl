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
            "s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDumper.kt",
            "s57-core/src/commonTest/kotlin/io/github/s57/core/raw/S57RawDecoderTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 3 files: $missing" }

        val decoder = layout.projectDirectory.file("s57-core/src/commonMain/kotlin/io/github/s57/core/raw/S57RawDecoder.kt").asFile.readText()
        check("DSID" in decoder && "FRID" in decoder && "VRID" in decoder) { "Phase 3 decoder must parse DSID/FRID/VRID records." }
        check("SG2D" in decoder && "SG3D" in decoder) { "Phase 3 decoder must parse SG2D/SG3D geometry." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 3 — S-57 raw decode" in phases) { "docs/PHASES.md must document Phase 3." }
    }
}

 tasks.register("phase3Check") {
    group = "verification"
    description = "Runs Phase 3 S-57 raw decoder checks and all previous phase checks."
    dependsOn("phase2Check", "phase3Audit", ":s57-core:build")
}

 tasks.register("phase4Audit") {
    group = "verification"
    description = "Checks Phase 4 normalized S-57 import pipeline."

    doLast {
        val requiredFiles = listOf(
            "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt",
            "s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57FeatureBuilder.kt",
            "s57-core/src/commonTest/kotlin/io/github/s57/core/import/S57ImportPipelineTest.kt"
        )
        val missing = requiredFiles.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing Phase 4 files: $missing" }

        val pipeline = layout.projectDirectory.file("s57-core/src/commonMain/kotlin/io/github/s57/core/import/S57ImportPipeline.kt").asFile.readText()
        check("coordinateMultiplier" in pipeline) { "Phase 4 import must use S-57 coordinate multiplier." }
        check("soundingMultiplier" in pipeline) { "Phase 4 import must use S-57 sounding multiplier." }
        check("toDataset" in pipeline) { "Phase 4 import must normalize raw cells to S57Dataset." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 4 — S-57 feature model" in phases) { "docs/PHASES.md must document Phase 4." }
    }
}

 tasks.register("phase4Check") {
    group = "verification"
    description = "Runs Phase 4 normalized import checks and all previous phase checks."
    dependsOn("phase3Check", "phase4Audit", ":s57-core:build")
}

 tasks.register("phase5Audit") {
    group = "verification"
    description = "Checks Phase 5 browser index store boundaries."

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
        val version = providers.gradleProperty("s52.version").orElse("0.5.0").get()
        check("S52_VERSION: \"$version\"" in ci) { "CI should use the configured S-52 version $version." }
        check("s52-kotlin-webgl-release-maven-$version.zip" in ci) { "CI should download the configured S-52 Maven release ZIP." }
        check("S52_RELEASE_MAVEN_SHA256" in ci) { "CI must verify the S-52 Maven release checksum." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 6 — S-52 adapter" in phases) { "docs/PHASES.md must document Phase 6." }
    }
}

 tasks.register("phase6Check") {
    group = "verification"
    description = "Runs Phase 6 real S-52 adapter checks and all previous phase checks. Requires the configured S-52 Maven release repository."
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
    }
}
