---
title: Retiring a broken release
description: Removing a confirmed-broken image version, marking the release, and deleting its tag.
---

When a published version is confirmed broken, take it out of circulation so nobody picks it up
by accident. There are three parts: remove the image, mark the release, delete the tag.

:::danger[Deletion is permanent]
None of this is reversible. Be certain the version is genuinely broken, not merely suspected,
before you start.
:::

## 1. Remove the broken image

### Create a token

You need a Personal Access Token with the **Packages → Delete** scope.

### Find the version ID

```bash
curl -s -H "Authorization: Bearer YOUR_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/orgs/ELIXIR-NO/packages/container/fega-norway/versions \
  | jq '.[] | select(.metadata.container.tags[]? == "COMPONENT_NAME-VERSION")'
```

Replace `YOUR_PAT` with your token and `COMPONENT_NAME-VERSION` with the image tag. Copy the
`version id` from the output.

### Delete it

```bash
curl -X DELETE -H "Authorization: Bearer YOUR_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/orgs/ELIXIR-NO/packages/container/fega-norway/versions/VERSION_ID
```

## 2. Mark the release as broken

Find the version on the **Releases** page and append this to the release title:

```
-verified-broken
```

The deleted image no longer pulls, but the release entry remains visible. Marking it means
anyone scanning the release list sees immediately why that version is missing, instead of
assuming an accident.

## 3. Delete the tag

GitHub exposes tags as Git references under `refs/tags`. This needs a **separate token** with
the `repo` scope, since the packages token above cannot touch refs.

```bash
curl -X DELETE \
  -H "Authorization: Bearer YOUR_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/ELIXIR-NO/fega-norway/git/refs/tags/TAG_NAME
```

:::note[The tag may linger locally]
Deleting the remote ref does not remove tags already fetched onto other machines. Anyone who
still sees it should run:

```bash
git fetch --prune --tags
```
:::

## Checklist

- [ ] Image version deleted from the container registry
- [ ] Release title suffixed with `-verified-broken`
- [ ] Git tag deleted via the API
- [ ] Team told which version to avoid and what to use instead
