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
