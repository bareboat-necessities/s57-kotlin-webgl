plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)
    jvm()
    js(IR) {
        browser()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":s57-core"))
            api(project(":s57-index"))
            api(project(":s57-s52-adapter"))
        }
        jsMain.dependencies {
            implementation("io.github.s52:s52-api:${property("s52.version")}")
            implementation("io.github.s52:s52-core:${property("s52.version")}")
            implementation("io.github.s52:s52-catalog:${property("s52.version")}")
            implementation("io.github.s52:s52-preslib:${property("s52.version")}")
            implementation("io.github.s52:s52-csp:${property("s52.version")}")
            implementation("io.github.s52:s52-render-webgl:${property("s52.version")}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
