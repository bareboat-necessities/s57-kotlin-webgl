plugins {
    kotlin("multiplatform")
}

/*
 * S-52 v0.5.4 supplies the symbol/color catalogue used by this project through
 * Kotlin/Maven/composite-build artifacts.  The browser demo must not download
 * or require the old OpenCPN rastersymbols-*.png sprite atlas.
 */
val checkExternalS52Resources by tasks.registering {
    group = "verification"
    description = "Checks that S-52 v0.5.4 is used without legacy raster atlas runtime downloads."
    doLast {
        logger.lifecycle("S-52 ${project.property("s52.version")} symbology is supplied by Kotlin artifacts; no runtime raster atlas download is required.")
    }
}

val phase12_13_14BCheck by tasks.registering {
    group = "verification"
    description = "Verifies the demo uses S-52 Kotlin symbology instead of legacy browser raster atlas resources."
    dependsOn(checkExternalS52Resources)
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":s57-iso8211"))
                implementation(project(":s57-core"))
                implementation(project(":s57-index"))
                implementation(project(":s57-s52-adapter"))
                implementation(project(":s57-render-webgl"))
                implementation(npm("jszip", "3.10.1"))
            }
        }
    }
}
