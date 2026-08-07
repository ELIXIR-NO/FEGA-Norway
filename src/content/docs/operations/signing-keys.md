---
title: JAR signing keys
description: Creating, exporting, rotating and publishing the GPG key used to sign Maven Central artifacts.
---

Maven Central requires every published artifact to be **signed with a GPG key**. This runbook
covers creating a key, getting it into GitHub Actions, and publishing the public half.

## Creating a key

```bash
gpg --gen-key
```

:::tip
If this fails with `gpg: agent_genkey failed: No pinentry`, add `--pinentry-mode loopback`.
:::

Use these values in the interactive prompts, and choose a strong password to protect the private
key:

```
Real name :  Federated EGA Norway
Email     :  fega-norway-support@elixir.no
```

## Finding the fingerprint

```bash
gpg --list-keys
```

```
pub   ed25519 2025-04-09 [SC] [expires: 2027-04-09]
      A9CD638727AE6815FB12EB8FF97FCD66B6BD0F8D
uid           [ultimate] FEGA Norway Team <fega-norway-support@elixir.no>
sub   cv25519 2025-04-09 [E] [expires: 2027-04-09]
```

The hex string on the second line is the **fingerprint**, the key's unique identifier. You need
it for the commands below.

## Exporting for GitHub Actions

Gradle cannot read variables containing newlines, and an ASCII-armoured key is inherently
multi-line. So the key is base64-encoded to a single line:

```bash
gpg --armor --export-secret-keys <fingerprint> | base64 -w 0
```

The result is roughly 1220 characters on one line, like `LS0tLS1CRUdJTiBQ.....xPQ0stLS0tLQo=`.

:::note[The output differs every time, and that is fine]
Running the export twice produces different output. The private key is encrypted with your
password using a random salt, and the salt is regenerated per export. The decrypted key is
identical in both cases.
:::

## Rotating the key in GitHub

1. Go to the [FEGA-Norway repository](https://github.com/ELIXIR-NO/FEGA-Norway) → **Settings**
2. **Secrets and Variables** → **Actions**
3. Edit the `SIGNING_KEY_BASE64` secret and paste the single-line key
4. Update `SIGNING_PASSWORD` to match the new key's password

Both secrets must be updated together. A mismatched pair fails the publish step with a signing
error rather than anything more descriptive.

## Publishing the public key

The public half lets others verify our signatures. It has to be uploaded to a public keyserver:

```bash
gpg --keyserver <keyserver> --send-keys <fingerprint>
```

The key is currently published on:

- `keyserver.ubuntu.com`
- `keys.openpgp.org`

:::caution[keys.openpgp.org requires email verification]
It sends a confirmation link to `fega-norway-support@elixir.no` that must be clicked before the
key becomes searchable. You can upload multiple versions of the "same key" (same owner and
email); all of them are listed when searching by email address, which can be confusing later.
:::
