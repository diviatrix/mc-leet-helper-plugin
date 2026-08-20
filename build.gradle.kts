// Root build: applies shared Java/toolchain/repository configuration to every
// subproject. Each subproject (leet-core, leet-skills, leet-crafting) is its own
// Paper plugin and applies the paperweight userdev plugin itself so it can be
// packaged independently.

subprojects {
    apply(plugin = "java")

    group = "com.leet"
    version = "1.5.2"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }

    // Route every subproject's jar into the single shared root build/libs/ so a
    // single `./gradlew build` colates all three plugin jars in one folder, ready
    // to drop into the server's plugins/ directory together.
    tasks.withType<Jar>().configureEach {
        val rootLibs = rootProject.layout.buildDirectory.dir("libs")
        destinationDirectory.set(rootLibs)
    }

    // Single source of truth for the version: rely on the project `version`
    // property, then inject it into each plugin.yml at build time.
    tasks.withType<ProcessResources>().configureEach {
        val versionTokens = mapOf("version" to project.version.toString())
        filesMatching("plugin.yml") {
            expand(versionTokens)
        }
    }
}
