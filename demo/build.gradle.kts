plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":s57-iso8211"))
            implementation(project(":s57-core"))
            implementation(project(":s57-index"))
            implementation(project(":s57-s52-adapter"))
            implementation(project(":s57-render-webgl"))
        }
    }
}
