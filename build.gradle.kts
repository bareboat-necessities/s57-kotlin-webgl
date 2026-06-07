plugins {
    kotlin("multiplatform") version "2.0.21" apply false
}

allprojects {
    group = "io.github.s57"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }
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

        val adapter = layout.projectDirectory.file("s57-s52-adapter/src/commonMain/kotlin/io/github/s57/adapter/S57ToS52Adapter.kt").asFile.readText()
        check("EncFeature" in adapter) { "Phase 6 adapter must produce s52 EncFeature objects." }
        check("S52PortrayalEngine" in adapter) { "Phase 6 adapter must expose a portrayal helper using s52-kotlin-webgl." }
        check("S52DrawCommandTranscript" in adapter) { "Phase 6 adapter must expose command transcript diagnostics." }

        val ci = layout.projectDirectory.file(".github/workflows/ci.yml").asFile.readText()
        check("s52-kotlin-webgl-release-maven-0.3.0.zip" in ci) { "CI must use the s52-kotlin-webgl v0.3.0 Maven release ZIP." }
        check("ed4ece6664670fec275f1d3d8d2ff52f1dfa54384501ebd97553c670e9687a79" in ci) { "CI must verify the s52 Maven release checksum." }

        val phases = layout.projectDirectory.file("docs/PHASES.md").asFile.readText()
        check("Phase 6 — S-52 adapter" in phases) { "docs/PHASES.md must document Phase 6." }
    }
}

tasks.register("phase6Check") {
    group = "verification"
    description = "Runs Phase 6 S-52 adapter checks and all previous phase checks. Requires the s52 v0.3.0 Maven release repository."
    dependsOn("phase5Check", "phase6Audit", ":s57-s52-adapter:build")
}
