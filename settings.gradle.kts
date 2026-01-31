pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MultiGPT"
include(":app")
include(":localinference")

// Use local llama-kotlin-android project for development/testing
includeBuild("/Users/kumpraso/personal-workspace/llama-kotlin-android") {
    dependencySubstitution {
        substitute(module("org.codeshipping:llama-kotlin-android")).using(project(":app"))
    }
}
