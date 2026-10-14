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

rootProject.name = "Kuro"
include(":app")
include(":clone-engine")
include(":blackbox")
include(":blackbox-reflection")
include(":blackbox-compiler")
include(":termux-x11")
include(":terminal-emulator")
include(":stub")

project(":blackbox").projectDir = file("third_party/NewBlackbox/Bcore")
project(":blackbox-reflection").projectDir = file("third_party/NewBlackbox/black-reflection")
project(":blackbox-compiler").projectDir = file("third_party/NewBlackbox/compiler")
