# Craftory for Hytale

Craftory for Hytale is a Kotlin server-plugin workspace remaking the old Craftory Tech Minecraft plugin for Hytale. This milestone establishes the build, module boundaries, and local development workflow; gameplay features will be added separately.

## Prerequisites

- JDK 25. Gradle uses the Foojay toolchain resolver to provision it automatically when needed.
- JetBrains Runtime (JBR) is recommended for class hot swap.
- A Hytale account for the one-time development asset authorization.
- IntelliJ IDEA or Visual Studio Code (optional).

## Quick start

```bash
./gradlew setupHytaleDev
./gradlew runAllMods
```

The first setup or server run prints a Hytale device-code link; open it and approve once to authorize asset downloads. `setupHytaleDev` can take several minutes the first time while it prepares the development environment. Once the server starts, connect the Hytale client to `localhost`.

## Debugging and hot swap

Start the workspace server with JDWP debugging and hot swap enabled:

```bash
./gradlew runAllMods -Ddebug=true -Dhotswap=true
```

The debugger listens on port `5005`. Use the shared IntelliJ configuration **Debug Dev Server** and attach with **Attach Debugger**, or use the Java attach configuration in `.vscode/launch.json`. JBR is recommended for the best hot-swap support.

Check the selected Java runtime and hot-swap capabilities with:

```bash
./gradlew hytaleJvmDoctor
```

## Modules

| Module | Purpose |
| --- | --- |
| `craftory-engine` | Pure Kotlin/JVM foundation. It must not depend on Hytale APIs. |
| `craftory-core` | Hytale plugin and shared public API; bundles the engine classes. |
| `craftory-tech` | Hytale plugin depending on Craftory Core's public API. |

The workspace's `runAllMods` task launches one development server with Core and Tech loaded together.

## Common tasks

| Task | Purpose |
| --- | --- |
| `./gradlew setupHytaleDev` | Authorize and prepare the local Hytale development environment. |
| `./gradlew runAllMods` | Run Core and Tech together on one local server. |
| `./gradlew :craftory-core:runServer` | Run the Core plugin by itself. |
| `./gradlew prepareDecompiledSourcesForIde` | Generate readable server sources for IDE browsing. |
| `./gradlew hytaleDoctor` | Diagnose Hytale development environment setup. |
| `./gradlew hytaleJvmDoctor` | Diagnose the server JVM and hot-swap support. |
| `./gradlew check` | Run tests, ktlint formatting checks, and detekt analysis. |
| `./gradlew build` | Build all modules and run the complete check pipeline. |
| `./gradlew spotlessApply` | Format Kotlin and Gradle Kotlin DSL sources. |

## Troubleshooting

- If setup or server startup cannot locate Hytale assets, run `./gradlew hytaleDoctor` and follow its diagnostics. Run `./gradlew setupHytaleDev` again if the local development data is incomplete.
- If the debugger does not attach or hot swap is unavailable, run `./gradlew hytaleJvmDoctor`, use JBR, and check that port `5005` is not already in use.
- If Gradle cannot find JDK 25, ensure the Foojay toolchain resolver can access the network or install JDK 25 locally.

## Project docs

- [Architecture](docs/ARCHITECTURE.md)
- [Gameplay and art direction](docs/DESIGN.md)
- [Contributing](CONTRIBUTING.md)
