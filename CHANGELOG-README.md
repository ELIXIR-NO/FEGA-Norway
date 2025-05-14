# Changelog Generation for FEGA-Norway

This document explains how to use the changelog generation tools built into the Gradle build system.

## Overview

The FEGA-Norway monorepo includes a custom Gradle plugin that can generate changelogs for each component based on git commit history. The changelog will list commits made to the specific component directory, organized by date.

Changelogs are generated during the release process and attached to GitHub releases, rather than being stored in the repository itself.

## Generating Changelogs Locally

### Generate Changelog for a Single Component

To generate a changelog for a specific component locally:

```bash
./gradlew :component-path:generateChangelog
```

For example:

```bash
# Generate changelog for the crypt4gh library
./gradlew :lib:crypt4gh:generateChangelog

# Generate changelog for the lega-commander CLI
./gradlew :cli:lega-commander:generateChangelog
```

### Generate Changelogs for All Components

To generate changelogs for all components at once:

```bash
./gradlew generateAllChangelogs
```

By default, this will create or update CHANGELOG.md files in each component's directory for local viewing.

### External Changelog Generation

To generate changelogs to an external directory instead of the component directories:

```bash
# Generate changelog to a custom directory
./gradlew :lib:crypt4gh:generateChangelog --outputDir=/path/to/output

# Example: Generate to the /tmp directory
./gradlew :lib:crypt4gh:generateChangelog --outputDir=/tmp
```

The generated file will be named after the component (e.g., `crypt4gh.md`).

## Customizing Changelog Generation

The changelog generator supports several options:

### Specifying Version Range

By default, the changelog will include all commits made to a component since its last tag. You can customize this:

```bash
# Generate changelog since a specific tag
./gradlew :lib:crypt4gh:generateChangelog --sinceTag=crypt4gh-1.0.0

# Generate changelog between two points
./gradlew :lib:crypt4gh:generateChangelog --sinceTag=crypt4gh-1.0.0 --toTag=crypt4gh-1.1.0
```

### Controlling Fallback Behavior

If no commits are found in the specified range (which may happen if the most recent tag is newer than the most recent commit), the system will automatically fall back to including the most recent commits. You can control how many commits to include:

```bash
# Specify the number of commits to include when falling back
./gradlew :lib:crypt4gh:generateChangelog --fallbackCommitCount=30
```

The default fallback count is 20 commits.

## How It Works

The changelog generator:

1. Identifies the component's directory in the monorepo
2. Uses `git log` to find commits that modified files in that directory
3. Parses commit messages and formats them with links to GitHub commits
4. Groups commits by date
5. Writes the formatted output either to a CHANGELOG.md file in the component's directory or to an external location

## Changelog Format

The generated changelog follows this format:

```markdown
# Changelog for [component-name]

All notable changes to this component will be documented in this file.

## Version X.Y.Z (YYYY-MM-DD)

### YYYY-MM-DD

- Commit message 1 ([commit-hash](link-to-commit))
- Commit message 2 ([commit-hash](link-to-commit))

### YYYY-MM-DD

- Commit message 3 ([commit-hash](link-to-commit))
```

## Integration with CI/CD

In the CI/CD pipeline, changelogs are automatically generated during the release process and attached directly to GitHub releases. This approach:

1. Keeps the repository clean without unnecessary changelog commits
2. Ensures users have access to changelogs when downloading releases
3. Integrates with Dependabot to show changes when updating dependencies

The release workflow does this by:

- Generating changelogs to a temporary directory
- Attaching the generated file as the release description

Example from our release workflow:

```yaml
- name: Generate changelog
  run: |
    mkdir -p /tmp/changelogs
    ./gradlew :component-path:generateChangelog --outputDir=/tmp/changelogs

- name: Create GitHub release
  uses: softprops/action-gh-release@v2
  with:
    tag_name: "component-name-x.y.z"
    body_path: /tmp/changelogs/component-name.md
```

## Best Practices for Commit Messages

To make your changelogs more useful, follow these guidelines for commit messages:

- Use a consistent format: `type: short description`
- Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Keep the first line under 72 characters
- Use the imperative mood ("Add feature" not "Added feature")
- Reference issue numbers when applicable: `fix: resolve race condition (#123)`

## Viewing Changelogs

Changelogs for released components can be found:

1. On the GitHub Releases page for each component
2. In Dependabot PRs when updating dependencies
