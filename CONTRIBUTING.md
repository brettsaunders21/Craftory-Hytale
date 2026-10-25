# Contributing

## Branching

Create a focused branch from `main` for each change. Use descriptive names such as `feature/core-command` or `fix/build-cache`.

## Commits

Use Conventional Commits, with the scope naming the affected area:

```text
feat(core): add module registration API
fix(build): correct plugin dependency staging
docs(dev): clarify local server setup
```

## Before pushing

Run the complete local check pipeline:

```bash
./gradlew check
```

Format Kotlin and Gradle Kotlin DSL sources with `./gradlew spotlessApply`, then run `./gradlew check` again if formatting changed files.
