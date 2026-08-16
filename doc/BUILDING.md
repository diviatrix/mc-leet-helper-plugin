# Building from Source

How to compile and package LeetHelper from source. This is **optional** — the default way to get the plugin is to download a prebuilt jar from the [GitHub Releases](https://github.com/diviatrix/mc-leet-helper-plugin/releases) page. Building from source is only needed for contributors or for testing unreleased changes.

## Prerequisites

- **JDK 25+** (the Gradle toolchain targets Java 25); Gradle can auto-provision a matching JDK if you don't have one installed
- **Gradle 9.7** (a wrapper is included — you only need to invoke `./gradlew`)
- **Internet connection** on the first build (downloads the Paper dev bundle)

## Commands

```bash
# Full build (compiles and produces the jar)
./gradlew build

# Clean + build
./gradlew clean build

# Just compile, skip packaging
./gradlew compileJava
```

**Output artifact:** `build/libs/leet-helper-<version>.jar`

> **Version is single-sourced:** the release version lives in **`build.gradle.kts`** (the `version` property). It drives both the jar filename and the `version` injected into the packaged `plugin.yml` at build time — bump it in exactly one place.

> **First build note:** the paperweight plugin downloads and runs a Paper server JAR to produce the remapped API (~40s). Subsequent builds are cached and faster.

## What the build does

`build.gradle.kts` uses `io.papermc.paperweight.userdev` (v2.0.0-beta.21) with `paperDevBundle("26.2.build.+")`. The Vault API is included as a `compileOnly` dependency (JitPack `com.github.MilkBowl:VaultAPI:1.7.1`).

The version is injected into `plugin.yml` at build time via `tasks.processResources` (a `filesMatching("plugin.yml")` + `expand`), so `project.version` is the single source of truth for both the jar filename and the runtime version.

## Testing

There is no unit-test suite or test plugin wired into the Gradle build (`gradle.properties` enables Gradle configuration-cache only). Verification is manual on a Paper 26.2 server — see the per-feature behavior notes and troubleshooting in [Admin.md](Admin.md#troubleshooting).

## Related docs

- [Architecture](ARCHITECTURE.md) — how the plugin is structured internally
- [README](../README.md) — configuration, permissions, commands, and operational usage