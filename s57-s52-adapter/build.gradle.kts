plugins {
    kotlin("multiplatform")
}

val s52Version = providers.gradleProperty("s52.version").orElse("0.3.0").get()

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
            api("io.github.s52:s52-catalog:$s52Version")
            api("io.github.s52:s52-core:$s52Version")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
