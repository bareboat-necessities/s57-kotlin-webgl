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
