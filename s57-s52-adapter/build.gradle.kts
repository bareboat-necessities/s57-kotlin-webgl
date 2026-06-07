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
            api("io.github.s52:s52-api:${property("s52.version")}")
            api("io.github.s52:s52-core:${property("s52.version")}")
            api("io.github.s52:s52-catalog:${property("s52.version")}")
            api("io.github.s52:s52-preslib:${property("s52.version")}")
            api("io.github.s52:s52-csp:${property("s52.version")}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
