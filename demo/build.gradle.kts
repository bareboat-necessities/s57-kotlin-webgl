import java.net.URI
import org.gradle.api.GradleException

plugins {
    kotlin("multiplatform")
}

val s52Source = providers.gradleProperty("s52SourceDir").orNull
    ?: System.getenv("S52_SOURCE_DIR")
    ?: "../s52-kotlin-webgl"

val s52ResourcesDir = file(s52Source).resolve("s52-render-webgl/src/jsMain/resources")
val s52AtlasDir = s52ResourcesDir.resolve("s52/opencpn")
val bundledS52AtlasDir = project.layout.projectDirectory.dir("src/jsMain/s52RuntimeResources/s52/opencpn").asFile
val s52AtlasFiles = listOf(
    "rastersymbols-day.png",
    "rastersymbols-dusk.png",
    "rastersymbols-dark.png"
)
val generatedS52RuntimeResources = layout.buildDirectory.dir("generated/s52-runtime-resources")
val generatedS52AtlasDir = generatedS52RuntimeResources.map { it.dir("s52/opencpn") }
val s52VersionForAssets = providers.gradleProperty("s52.version").orElse("0.5.2")
val allowS52RuntimeResourceDownload = providers.gradleProperty("s52.downloadRuntimeResources")
    .map { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) }
    .orElse(true)

fun copyAtlasFiles(from: File, to: File): Boolean {
    if (!from.isDirectory) return false
    val missing = s52AtlasFiles.filterNot { from.resolve(it).isFile }
    if (missing.isNotEmpty()) return false
    to.mkdirs()
    s52AtlasFiles.forEach { name -> from.resolve(name).copyTo(to.resolve(name), overwrite = true) }
    return true
}

fun findAtlasDirUnder(root: File): File? {
    if (!root.isDirectory) return null
    return root.walkTopDown()
        .filter { it.isDirectory && it.name == "opencpn" && it.resolve("rastersymbols-day.png").isFile }
        .firstOrNull()
}

val prepareS52OpenCpnRuntimeResources by tasks.registering {
    group = "build setup"
    description = "Copies or downloads the OpenCPN raster symbol atlases required by the browser S-52 renderer."
    outputs.dir(generatedS52RuntimeResources)
    doLast {
        val target = generatedS52AtlasDir.get().asFile
        target.mkdirs()

        val candidateAtlasDirs = listOfNotNull(
            bundledS52AtlasDir,
            s52AtlasDir,
            findAtlasDirUnder(rootProject.layout.buildDirectory.dir("s52-source-unpacked").get().asFile),
            findAtlasDirUnder(rootProject.layout.buildDirectory.dir("s52-maven").get().asFile),
            findAtlasDirUnder(rootProject.layout.buildDirectory.dir("s52-images").get().asFile)
        ).distinctBy { it.absoluteFile.normalize().path }

        val copiedFrom = candidateAtlasDirs.firstOrNull { copyAtlasFiles(it, target) }
        if (copiedFrom != null) {
            logger.lifecycle("Prepared S-52/OpenCPN browser atlas resources from $copiedFrom")
        } else if (allowS52RuntimeResourceDownload.get()) {
            val version = s52VersionForAssets.get().removePrefix("v")
            s52AtlasFiles.forEach { name ->
                val destination = target.resolve(name)
                if (!destination.isFile || destination.length() <= 0L) {
                    val urls = listOf(
                        "https://raw.githubusercontent.com/bareboat-necessities/s52-kotlin-webgl/v$version/s52-render-webgl/src/jsMain/resources/s52/opencpn/$name",
                        "https://raw.githubusercontent.com/bareboat-necessities/s52-kotlin-webgl/main/s52-render-webgl/src/jsMain/resources/s52/opencpn/$name"
                    )
                    var downloaded = false
                    var lastError: Throwable? = null
                    for (url in urls) {
                        try {
                            logger.lifecycle("Downloading S-52/OpenCPN atlas resource $name from $url")
                            URI(url).toURL().openStream().use { input ->
                                destination.outputStream().use { output -> input.copyTo(output) }
                            }
                            downloaded = destination.isFile && destination.length() > 0L
                            if (downloaded) break
                        } catch (t: Throwable) {
                            lastError = t
                            destination.delete()
                        }
                    }
                    if (!downloaded) {
                        throw GradleException("Could not prepare S-52/OpenCPN atlas resource $name: ${lastError?.message ?: "download failed"}")
                    }
                }
            }
        }

        val missing = s52AtlasFiles.filterNot { target.resolve(it).isFile && target.resolve(it).length() > 0L }
        check(missing.isEmpty()) {
            "Missing S-52/OpenCPN raster atlas resources: $missing. " +
                "The repo should include these under demo/src/jsMain/s52RuntimeResources; " +
                "otherwise set -Ps52SourceDir=/path/to/s52-kotlin-webgl, set S52_SOURCE_DIR, " +
                "or allow the default -Ps52.downloadRuntimeResources=true download."
        }
    }
}

val checkExternalS52Resources by tasks.registering {
    group = "verification"
    description = "Checks external S-52 browser resources."
    dependsOn(prepareS52OpenCpnRuntimeResources)
    doLast {
        val atlasDir = generatedS52AtlasDir.get().asFile
        val missing = s52AtlasFiles.filterNot { atlasDir.resolve(it).isFile && atlasDir.resolve(it).length() > 0L }
        check(missing.isEmpty()) { "Missing prepared S-52/OpenCPN atlas resources: $missing" }
    }
}

val phase12_13_14BCheck by tasks.registering {
    group = "verification"
    description = "Checks corrected Phase 12/13/14 external S-52 asset boundary."
    dependsOn(checkExternalS52Resources)
    doLast {
        val committedAtlasDir = project.layout.projectDirectory.dir("src/jsMain/resources/s52/opencpn").asFile
        check(!committedAtlasDir.exists()) { "S-52 atlas resources must stay external/generated, not committed under demo/src/jsMain/resources." }
    }
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain {
            resources.srcDir(generatedS52RuntimeResources)
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

tasks.matching { task ->
    task.name == "jsProcessResources" || task.name == "processJsMainResources" || task.name.endsWith("ProcessResources")
}.configureEach {
    dependsOn(prepareS52OpenCpnRuntimeResources)
}
