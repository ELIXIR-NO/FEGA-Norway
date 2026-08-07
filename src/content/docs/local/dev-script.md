---
title: The dev.sh workflow
description: Starting, stopping and selectively rebuilding the local FEGA-Norway stack.
---

`dev.sh` lives in the repository root and automates the whole local environment. **You must run
it from the repository root**, since it resolves paths relative to its own location.

Run it with no arguments for an interactive menu, or pass a subcommand directly.

## Starting and stopping

```bash
./dev.sh start   # build images, template configs, bring the stack up
./dev.sh stop    # tear down, removing volumes and networks
```

`start` does more than `docker compose up`. It renders the compose template, generates
certificates and keys, and waits for a bootstrap container to signal that shared volumes are
staged before the dependent services start.

## Rebuilding one thing

After changing a single module you rarely want a full rebuild. Each of these rebuilds and
redeploys just that piece:

```bash
# libraries
./dev.sh rebuild_clearinghouse
./dev.sh rebuild_tsd_file_api_client
./dev.sh rebuild_crypt4gh

# services
./dev.sh rebuild_and_deploy_proxy
./dev.sh rebuild_and_deploy_tsd
./dev.sh rebuild_and_deploy_mq_interceptor
./dev.sh rebuild_and_deploy_heartbeat_sub
./dev.sh rebuild_and_deploy_heartbeat_pub
```

## Everything else

| Command | What it does |
| --- | --- |
| `reexecute_tests_in_container` | Rebuild and re-run the end-to-end suite in its container |
| `apply_all_spotless_checks` | Format all Java and Kotlin sources |
| `restart_docker_daemon` | Restart the Docker daemon |
| `replace_root_ca` | Re-install the generated root CA into a container |
| `cleanup_environment` | Deep clean: remove all project containers, volumes and images |

## Formatting before you push

Formatting is enforced in CI by Spotless, so a badly formatted file fails the build. Either use
the `dev.sh` wrapper above or call Gradle directly:

```bash
./gradlew spotlessApply   # fix formatting
./gradlew spotlessCheck   # verify only
```

## Ports that must be free

The stack binds a number of host ports. If something else is already listening on one of them,
startup fails:

```
80    5005   5006   5432   5671   5673
6379  8088   8443   10443  15671  15672  25672
```

If you need different ports, remap them in the compose template rather than editing generated
files.

:::tip[Corrected from the old wiki]
The wiki listed `5432 5672 5433 80 5673 15672 25672`. That list both included ports the stack
does not publish (`5672`, `5433`) and omitted several it does (`8443`, `6379`, `10443`, `15671`,
`5005`, `5006`, `8088`). The list above was read from the compose template on `main`.
:::

:::caution[Changing in PR #833]
The compose template currently lives at `e2eTests/docker-compose.template.yml`. Pull request
[#833](https://github.com/ELIXIR-NO/FEGA-Norway/pull/833) moves it to `e2e/docker-compose.template.yml`
along with the rest of the test module. The `dev.sh` commands themselves are unchanged.
:::

## When it goes wrong

See [troubleshooting](../troubleshooting/) for the failure modes you are most likely to hit.
