# Repository guide

## Build and verification

- `./gradlew build` builds all modules and runs tests, Spotless/ktlint, and detekt.
- `./gradlew check` runs the test and quality checks without assembling artifacts.
- `./gradlew spotlessApply` formats Kotlin and Gradle Kotlin DSL sources.
- `./gradlew setupHytaleDev` prepares the authenticated local Hytale development environment.
- `./gradlew runAllMods` launches both Hytale plugins in one server.

## Module boundaries

- `craftory-engine` is pure Kotlin/JVM and must not import or depend on Hytale APIs.
- `craftory-core` is the Hytale plugin and owns shared public APIs used by other Craftory plugins.
- `craftory-tech` must use only `craftory-core`'s public API and declare its Core dependency in its manifest.
- Keep Hytale-specific code out of the engine, and keep module-to-module dependencies explicit.

## Documentation

- `README.md` covers setup and common development commands.
- `docs/ARCHITECTURE.md` describes the project's intended architecture.
- `docs/DESIGN.md` describes gameplay and art direction.
- `CONTRIBUTING.md` documents branching, commit, and pre-push conventions.
