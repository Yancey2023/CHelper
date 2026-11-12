pluginManagement {
    includeBuild("build-logic")
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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://redempt.dev")
        maven("https://jitpack.io")
    }
}

rootProject.name = "CHelper-Compose"

include(
    ":apps:android",
    ":apps:desktop",
    ":shared:domain",
    ":shared:data",
    ":shared:native",
    ":shared:ui",
)
