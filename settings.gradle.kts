pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
