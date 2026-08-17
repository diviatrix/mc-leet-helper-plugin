plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.leet"
version = "1.2.1"

base {
    archivesName = "leet-helper"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
}

// Single source of truth for the version: rely on the project `version`
// property, then inject it into plugin.yml at build time. Bumping once in
// build.gradle.kts updates both the jar filename and the runtime version.
tasks.processResources {
    val versionTokens = mapOf("version" to project.version.toString())
    filesMatching("plugin.yml") {
        expand(versionTokens)
    }
}
