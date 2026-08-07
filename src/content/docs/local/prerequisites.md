---
title: Prerequisites
description: The tools you need installed before you can build or run the FEGA-Norway stack locally.
---

## What you need

| Requirement | Version | Why |
| --- | --- | --- |
| **Java** | 21 | Primary language for the libraries and the proxy |
| **Go** | 1.26 | `lega-commander`, `mq-interceptor`, `cega-mock` |
| **Docker + Compose v2** | current | Orchestrating the local stack |
| **Free disk space** | at least 8 GiB | Images, volumes and test fixtures |

:::tip[Corrected from the old wiki]
The wiki said Go 1.21. Every Go module in the repository declares `go 1.26.0`, so 1.21 will not
build the project. This page reflects the code.
:::

Docker is not required to *build* the monorepo, only to run the services. You can compile and
test everything without it.

Always use `docker compose` (the v2 plugin), never the standalone `docker-compose` v1 binary.

## Recommended, not required

- **Gradle CLI**: the wrapper (`./gradlew`) works without it, but a local CLI is convenient.
- **An IDE.** IntelliJ is the common choice for the Java side, but VS Code, Emacs and Neovim all
  work. The Gradle wrapper makes the build identical regardless of editor, so this is purely
  personal preference.

## Installing Java

**Linux.** The project uses the Temurin distribution, though any vendor's JDK 21 should behave
identically. Follow the [Temurin installation guide](https://adoptium.net/installation/linux/).

**macOS.** Most vendors ship an installer. With Homebrew:

```bash
brew install --cask temurin
```

MacPorts or a direct download from a JDK vendor also work.

**Windows.** Download an installer from a vendor site. If you use WSL, follow the Linux
instructions inside your WSL distribution instead.

:::caution
Make sure `JAVA_HOME` points at the JDK 21 installation. A stale `JAVA_HOME` pointing at an
older JDK is the most common cause of a build that fails for no apparent reason.
:::

If you juggle multiple Java versions, [asdf](https://asdf-vm.com/) and
[SDKMAN!](https://sdkman.io/) both handle this well.

## Installing Go

Follow the [official Go installation guide](https://go.dev/learn/), which covers every operating
system.

## Installing Docker

Installation guides for every OS and architecture are in the
[Docker documentation](https://docs.docker.com/get-docker/).

:::note[Platform support]
The local orchestration supports **Linux and macOS only**. There is no supported Windows path
for running the stack, though WSL works in practice.
:::

## Next

With the toolchain in place, move on to [the dev.sh workflow](../dev-script/).
