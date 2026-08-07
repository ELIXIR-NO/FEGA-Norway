---
title: The build system
description: How the Gradle multi-project build is organised, and the commands you will actually use.
---

The monorepo is a Gradle multi-project build driven by the wrapper. Use `./gradlew`, never a
locally installed `gradle`, so everyone builds with the same version.

## Commands you will actually use

```bash
./gradlew build                    # build and test everything
./gradlew test                     # all tests
./gradlew spotlessApply            # format Java and Kotlin
./gradlew spotlessCheck            # verify formatting only

# a single module
./gradlew :services:localega-tsd-proxy:build -x test

# a single test
./gradlew :e2eTests:test --tests <fully.qualified.ClassName>
```

## How it is wired

Modules are registered in `settings.gradle.kts`. Two pieces of shared machinery live in
`buildSrc` as convention plugins, which is why individual module build files stay short:

- **`formatting-conventions`** applies Spotless everywhere, using Google Java Format for Java
  and diktat for Kotlin. This is what `spotlessCheck` enforces in CI.
- **`changelog`** generates per-module changelogs from Conventional Commit subjects. It is the
  reason commit message format is load-bearing rather than cosmetic.

The Go modules are wrapped as Gradle tasks too, so `./gradlew build` covers the Go components
as well as the JVM ones. Those tasks shell out to `go build` and `go test`.

:::note[A project directory does not need a build file]
Gradle treats a directory included in `settings.gradle.kts` with no `build.gradle.kts` as a
valid project with no build script. The service Dockerfiles rely on this: they copy only the
build files they need and `mkdir` the rest, which keeps Docker layer caching effective without
copying the whole repository.
:::

## Why Gradle

The relevant properties for a repository shaped like this one are incremental builds (only
changed tasks rerun), first-class multi-project support, and the ability to write custom tasks
in Kotlin, which is what makes the convention plugins above possible. Gradle also wraps the Go
modules under the same entry point, so a single `./gradlew build` covers a codebase in two
languages.

If you are new to Gradle itself, the [Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html)
is the reference. This page deliberately does not restate it.

:::tip[Trimmed from the old wiki]
The wiki's Gradle page was largely a general Gradle-versus-Maven comparison. That material is
better maintained by Gradle's own documentation and dated quickly, so this page keeps only what
is specific to this repository.
:::
