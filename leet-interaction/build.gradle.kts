plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

base {
    archivesName = "leet-interaction"
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
    compileOnly(project(":leet-core"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
}
