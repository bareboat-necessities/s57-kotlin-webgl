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
            implementation(project(":s57-iso8211"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
