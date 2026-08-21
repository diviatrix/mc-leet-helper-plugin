pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    // Auto-provisions the JDK 25 toolchain (declared in each subproject) when it is
    // not already installed on the build machine. Requires network access on first build.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mc-leet-helper-plugin"

include("leet-core")
include("leet-skills")
include("leet-crafting")
include("leet-vanity")
