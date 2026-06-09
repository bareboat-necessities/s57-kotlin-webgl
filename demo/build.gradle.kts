plugins {
    kotlin("multiplatform")
}

val s52Source = providers.gradleProperty("s52SourceDir").orNull
    ?: System.getenv("S52_SOURCE_DIR")
    ?: "../s52-kotlin-webgl"

val s52ResourcesDir = file(s52Source).resolve("s52-render-webgl/src/jsMain/resources")
val s52AtlasDir = s52ResourcesDir.resolve("s52/opencpn")

val checkExternalS52Resources by tasks.registering {
    group = "verification"
    description = "Checks external S-52 browser resources."
    doLast {
        val missing = listOf(
            "rastersymbols-day.png",
            "rastersymbols-dusk.png",
            "rastersymbols-dark.png"
        ).filterNot { s52AtlasDir.resolve(it).isFile }
        check(missing.isEmpty()) { "Missing external S-52 atlas resources: $missing" }
    }
}

val phase12_13_14BCheck by tasks.registering {
    group = "verification"
    description = "Checks corrected Phase 12/13/14 external S-52 asset boundary."
    dependsOn(checkExternalS52Resources)
    doLast {
        val committedAtlasDir = project.layout.projectDirectory.dir("src/jsMain/resources/s52/opencpn").asFile
        check(!committedAtlasDir.exists()) { "S-52 atlas resources must stay external." }
    }
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            resources.srcDir(s52ResourcesDir)
            dependencies {
                implementation(project(":s57-iso8211"))
                implementation(project(":s57-core"))
                implementation(project(":s57-index"))
                implementation(project(":s57-s52-adapter"))
                implementation(project(":s57-render-webgl"))
            }
        }
    }
}
