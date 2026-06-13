pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val configuredS52SourceDir = providers.gradleProperty("s52SourceDir").orNull
    ?: System.getenv("S52_SOURCE_DIR")

if (!configuredS52SourceDir.isNullOrBlank()) {
    val s52SourceDir = file(configuredS52SourceDir)
    if (s52SourceDir.isDirectory) {
        includeBuild(s52SourceDir) {
            dependencySubstitution {
                substitute(module("io.github.s52:s52-api")).using(project(":s52-api"))
                substitute(module("io.github.s52:s52-core")).using(project(":s52-core"))
                substitute(module("io.github.s52:s52-catalog")).using(project(":s52-catalog"))
                substitute(module("io.github.s52:s52-preslib")).using(project(":s52-preslib"))
                substitute(module("io.github.s52:s52-csp")).using(project(":s52-csp"))
                substitute(module("io.github.s52:s52-render-webgl")).using(project(":s52-render-webgl"))
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        val configuredS52Repo = providers.gradleProperty("s52MavenRepo").orNull
            ?: System.getenv("S52_MAVEN_REPO")
            ?: "build/s52-maven"
        maven { url = uri(configuredS52Repo) }
        maven { url = uri("$configuredS52Repo/maven") }
        maven { url = uri("$configuredS52Repo/s52-kotlin-webgl-release-maven-0.5.4") }
        maven { url = uri("$configuredS52Repo/s52-kotlin-webgl-release-maven-0.5.3") }
        maven { url = uri("$configuredS52Repo/s52-kotlin-webgl-release-maven-0.5.0") }
        maven { url = uri("$configuredS52Repo/s52-kotlin-webgl-release-maven-0.3.0") }
        mavenCentral()
        google()
    }
}

rootProject.name = "s57-kotlin-webgl"

include(
    ":s57-iso8211",
    ":s57-core",
    ":s57-index",
    ":s57-s52-adapter",
    ":s57-render-webgl",
    ":demo"
)
