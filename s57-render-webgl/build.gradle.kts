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

val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
val phase21NoaaEncFile = providers.gradleProperty("phase21.noaaEncFile")
val phase21SnapshotSvg = providers.gradleProperty("phase21.snapshotSvg")
    .orElse(layout.buildDirectory.file("phase21/noaa-enc-smoke.svg").map { it.asFile.absolutePath })

tasks.register<JavaExec>("phase21NoaaVisualSmoke") {
    group = "verification"
    description = "Runs the Phase 21 real NOAA ENC visual smoke harness. Use -Pphase21.noaaEncFile=/path/to/cell.000"
    dependsOn("jvmJar")
    classpath = jvmMainCompilation.output.allOutputs + jvmMainCompilation.runtimeDependencyFiles
    mainClass.set("io.github.s57.render.NoaaEncVisualSmokeMainKt")
    doFirst {
        require(phase21NoaaEncFile.isPresent) {
            "Set -Pphase21.noaaEncFile=/path/to/NOAA_CELL.000 to run Phase 21 real-ENC smoke."
        }
        args(phase21NoaaEncFile.get(), phase21SnapshotSvg.get())
    }
}
