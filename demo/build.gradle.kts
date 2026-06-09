plugins {
    kotlin("multiplatform")
}

val s52Source = providers.gradleProperty("s52SourceDir").orNull
    ?: System.getenv("S52_SOURCE_DIR")
    ?: "../s52-kotlin-webgl"

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            resources.srcDir(file(s52Source).resolve("s52-render-webgl/src/jsMain/resources"))
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
