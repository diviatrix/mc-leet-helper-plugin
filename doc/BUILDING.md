# Building from Source

How to compile and package **LeetHelper** from source. This is **optional** — the default way to get the plugins is to download the five prebuilt jars from the [GitHub Releases](https://github.com/diviatrix/mc-leet-helper-plugin/releases) page. Building from source is only needed for contributors or for testing unreleased changes.

## Prerequisites

- **JDK 25+** (the Gradle toolchain targets Java 25); Gradle can auto-provision a matching JDK if you don't have one installed
- **Gradle 9.7** (a wrapper is included — you only need to invoke `./gradlew`)
- **Internet connection** on the first build (downloads the Paper dev bundle)

## Project layout

It's a multi-project Gradle build (`settings.gradle.kts`). Each subproject is an independent Paper plugin that applies the same paperweight toolchain:

```
settings.gradle.kts     # rootProject + include("leet-core", "leet-skills", "leet-crafting", "leet-vanity", "leet-interaction")
build.gradle.kts        # subprojects { version = "..." }; jars routed to build/libs/
leet-core/              # LeetCore plugin
leet-skills/            # LeetSkills plugin
leet-crafting/          # LeetCrafting plugin
leet-vanity/            # LeetVanity plugin
leet-interaction/       # LeetInteraction plugin
```

## Commands

```bash
# Full build (compiles all five plugins and produces the jars)
./gradlew build

# Clean + build
./gradlew clean build

# Just compile one plugin, skip packaging
./gradlew :leet-core:compileJava
```

**Output artifacts** — all five jars land in the shared root `build/libs/`:

```
build/libs/leet-core-<version>.jar
build/libs/leet-skills-<version>.jar
leet-crafting-<version>.jar
leet-vanity-<version>.jar
leet-interaction-<version>.jar
```

Deploy **all five** into the server `plugins/` folder together — LeetSkills, LeetCrafting, LeetVanity, and LeetInteraction soft-depend on LeetCore, so LeetCore must be present and load first.

> **Version is single-sourced:** the release version lives in the root **`build.gradle.kts`** `subprojects { version = ... }` block. It drives both each jar filename and the `version` injected into the packaged `plugin.yml` at build time — bump it in exactly one place.

> **First build note:** the paperweight plugin downloads and runs a Paper server JAR to produce the remapped API (~40s). Subsequent builds are cached and faster.

## What the build does

The root `build.gradle.kts` `subprojects` block applies `io.papermc.paperweight.userdev` (v2.0.0-beta.21) with `paperDevBundle("26.2.build.+")` to every subproject. Each subproject also pulls The Vault API as a `compileOnly` dependency (JitPack `com.github.MilkBowl:VaultAPI:1.7.1`); `leet-skills`, `leet-crafting`, and `leet-vanity` add a `compileOnly` dependency on `:leet-core` so they can compile against the shared `CoreApi`.

The version is injected into each `plugin.yml` at build time via `tasks.processResources` (a `filesMatching("plugin.yml")` + `expand`), so `project.version` is the single source of truth for both the jar filename and the runtime version.

## Testing

There is no unit-test suite or test plugin wired into the Gradle build (`gradle.properties` enables Gradle configuration-cache only). Verification is manual on a Paper 26.2 server with all five jars installed — see the canonical feature documents.

## Related docs

- [Architecture](ARCHITECTURE.md) — how the five plugins are structured internally and how they cooperate
- [README](../README.md) — configuration, permissions, commands, and operational usage
