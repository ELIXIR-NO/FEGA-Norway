---
title: Commits and pull requests
description: Branch naming, commit message format and the merge policy for the FEGA-Norway monorepo.
---

## Commit messages

Commit subjects follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<scope>): <description>
```

Common types are `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `chore`, `ci` and `style`.
Keep the subject in the imperative mood and lowercase, with no trailing period. The body should
explain *why*, since the diff already shows *what*.

```text
feat(clearinghouse): add visa expiry grace period
fix(proxy): reject tokens with a missing audience claim
```

This is not merely stylistic. The changelog generation reads these prefixes, so a
non-conforming subject produces a wrong or empty changelog entry for the affected component.

## Pull request titles

PR titles follow the same Conventional Commits format as commit subjects.

## Merging

**Rebase first if necessary, then use a merge commit.** Do not squash. The individual commits
carry the version-bump markers described in [versioning and releases](../versioning/), and
squashing can lose or merge them in ways that change what gets released.

## Version bumps

If your change should produce anything other than a patch release for the component you touched,
you must say so in a commit message. That mechanism is documented in full on the
[versioning and releases](../versioning/) page, and it is the single most important thing to
understand before merging to `main`.

## Formatting

Spotless enforces formatting in CI and will fail the build on a violation. Run it before you
push:

```bash
./gradlew spotlessApply
```
