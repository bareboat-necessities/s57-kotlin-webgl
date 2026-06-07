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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
