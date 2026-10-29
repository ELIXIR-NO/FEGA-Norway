---
title: CI workflows
description: Every GitHub Actions workflow in the FEGA-Norway repository, what it does and what triggers it.
---

All workflow definitions live in `.github/workflows/`. This table is the map; read the YAML when
you need the detail.

| Workflow | What it does | Trigger |
| --- | --- | --- |
| `action-linter.yml` | Lints YAML, JSON and GitHub Actions files with a schema validator and Actionlint | PRs touching `*.yaml`, `*.yml` or `*.json` |
| `check-commit-message.yml` | Validates version-bump markers against the known component list, failing on typos | PRs targeting `main` |
| `codeql.yml` | CodeQL security analysis of the Java and Go code | Pushes to `main` |
| `detect-changed-components.yml` | Works out which components changed and emits a matrix for other workflows | Called via `workflow_call` |
| `pre-release-build-check.yml` | Builds the JARs of the changed libraries and the Docker images of the changed services and the e2e runner, without pushing anything, to prove the release will build. `lega-commander` is first built by the publish check | PRs targeting `main` |
| `pre-release-publish-check.yml` | Rehearses the publish: dry-run JAR publishing, a snapshot `lega-commander` build, a real push of the changed Docker images tagged with the PR number, and changelog generation | A PR entering the merge queue for `main` (`merge_group`) |
| `publish-and-release.yml` | Publishes changed components, creates GitHub Releases, tags them and generates changelogs | Merged PRs to `main` |
| `remove-old-images.yml` | Deletes old and untagged Docker images from the container registry | Cron, Mondays 02:10 UTC, or manual dispatch |
| `scan-images.yml` | Trivy vulnerability scans on updated images, uploading results to GitHub Security | Merged PRs to `main` touching `services/localega-tsd-proxy/**` or `services/mq-interceptor/**` |
| `spotless-check.yml` | Verifies formatting via Gradle Spotless | PRs targeting `main` that touch `lib/**`, `services/**` or `buildSrc/**` |
| `unit-and-e2e-test.yml` | Builds with Gradle, runs unit tests, then brings up the full stack and runs the end-to-end suite | Any push |

## The ones that will block your PR

Three of these gate a merge, and they are worth knowing by name:

- **`unit-and-e2e-test`** is the heavyweight. It does not just compile: it boots the entire
  docker-compose stack and runs the end-to-end pipeline, failing on the test container's exit
  code. When it fails, read the container logs in the job output rather than the Gradle output.
- **`check-commit-message`** fails on a mistyped component name in a bump marker. See
  [versioning and releases](../versioning/).
- **`spotless-check`** fails on formatting. `./gradlew spotlessApply` fixes it.

## Release-related workflows

A pull request meets three of them in turn: `pre-release-build-check` while it is open,
`pre-release-publish-check` once it enters the merge queue, and `publish-and-release` after it
merges, which pulls the image the publish check pushed and retags it with the release version.

`detect-changed-components` is shared machinery rather than something you trigger. All three
call it to build their component matrix, and each also names the components it knows how to
build or publish. With `detect-changed-components` itself and the `COMPONENTS` list in
`check-commit-message.yml`, that makes five places a component must be registered consistently.
Registering a component in one and not the others produces a confusing failure at release time
rather than a clear error.
