pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
