---
title: Troubleshooting
description: Known failure modes when bringing up the local stack, and how to clear them.
---

Work down this page roughly in order; the cheap fixes are first.

## Conflicting container names

If startup complains that a container name is already in use, remove the leftovers by hand:

```bash
docker rm tsd db mq proxy interceptor postgres ingest verify finalize \
  mapper intercept doa cegamq cegaauth heartbeat-pub heartbeat-sub \
  redis file-orchestrator e2e-tests
```

## The stack will not build or start at all

Restart the Docker daemon:

```bash
./dev.sh restart_docker_daemon
```

This resolves more problems than it has any right to, particularly anything that smells like a
networking failure.

## A build fails resolving dependencies

Symptoms look like `dial tcp: lookup proxy.golang.org: i/o timeout`, or a Gradle build that
cannot reach `repo.maven.apache.org`.

This is almost always the Docker daemon's container-to-internet path having broken, not an
actual outage. Restart the daemon:

```bash
./dev.sh restart_docker_daemon
```

:::tip[Confirming it is the daemon]
A container can reach its gateway but not the internet when this happens. Compare these two:

```bash
docker run --rm alpine ping -c2 172.17.0.1   # gateway: succeeds
docker run --rm alpine ping -c2 8.8.8.8      # internet: 100% loss
```

If the gateway responds and the internet does not while your host has working networking, the
daemon's NAT path is the problem and a restart fixes it. If the host itself cannot resolve
names, fix that first.
:::

## Port conflicts

Free the [ports the stack needs](../dev-script/#ports-that-must-be-free), or remap them in the
compose template.

## My changes to a service are not in the running container

Stale images. Force them out:

```bash
docker rmi tsd-proxy:latest tsd-api-mock:latest mq-interceptor:latest --force
```

Then rebuild that service with the matching `./dev.sh rebuild_and_deploy_*` command.

## I have `docker-compose` but not `docker compose`

On older Ubuntu releases you may have the standalone binary without the CLI plugin. Link it in:

```bash
mkdir -p ~/.docker/cli-plugins
ln -sfn $(which docker-compose) ~/.docker/cli-plugins/docker-compose
```

Docker's [migration guide](https://docs.docker.com/compose/migrate/) covers the broader move,
and [docker/compose#8630](https://github.com/docker/compose/issues/8630) has the background
discussion.

## Start completely over

When the environment is beyond saving:

```bash
rm -rf docker-compose.yml && ./gradlew clean
./dev.sh cleanup_environment
./dev.sh restart_docker_daemon
```

`cleanup_environment` removes every project container, volume and image, so the next `start`
rebuilds from nothing. Expect it to take a while.
