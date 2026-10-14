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
        google()
        mavenCentral()
        // Required by vendored BlackBox core (FreeReflection). See docs/ENGINE.md.
        maven("https://www.jitpack.io")
    }
}
rootProject.name = "Kuro"
include(":app")
include(":engine")
include(":engine-blackbox")
include(":blackbox")
include(":blackbox-reflection")
include(":blackbox-compiler")
include(":termux-terminal-emulator")
include(":termux-terminal-view")

project(":blackbox").projectDir = file("third_party/NewBlackbox/Bcore")
project(":blackbox-reflection").projectDir = file("third_party/NewBlackbox/black-reflection")
project(":blackbox-compiler").projectDir = file("third_party/NewBlackbox/compiler")
project(":termux-terminal-emulator").projectDir = file("third_party/termux-app/terminal-emulator")
project(":termux-terminal-view").projectDir = file("third_party/termux-app/terminal-view")
