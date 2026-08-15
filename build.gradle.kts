plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "com.leet"
version = "1.1.1"

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
