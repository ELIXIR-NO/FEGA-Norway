---
title: What this is
description: What the FEGA-Norway project covers, why it is a monorepo, and how to read this documentation.
---

FEGA Norway is the Norwegian node of the **Federated European Genome-phenome Archive**: the
infrastructure that lets researchers submit sensitive human genomic data, have it encrypted and
archived under controlled access, and hand it back out to people who have been granted a right
to see it.

This repository is a monorepo. It consolidates what used to be several separate repositories
into one place, which centralises version control, removes duplicated libraries between
components, and gives a new contributor a single thing to clone.

## What the project actually does

Three concerns run through everything here:

- **Getting data in.** A submitter encrypts a file client-side and uploads it. It lands in an
  inbox inside UiO's Tjeneste for Sensitive Data (TSD) and is then ingested, verified and
  archived.
- **Keeping it controlled.** Nothing is stored unencrypted. Access is decided by GA4GH Passport
  visas issued against a user's identity from Life Science AAI or CEGA.
- **Getting data out.** An authorised user requests a dataset and receives it, re-encrypted to
  a key only they hold.

Files are encrypted with **Crypt4GH** before they ever leave the submitter's machine, and they
stay encrypted at rest. No component in this system ever holds a plaintext copy of a submitted
file on disk.

## How to read these docs

| If you want to… | Go to |
| --- | --- |
| Understand how the pieces fit together | [System overview](../../architecture/system/) |
| Get the stack running on your laptop | [Prerequisites](../../local/prerequisites/) |
| Land a change and have it released | [Versioning and releases](../../contributing/versioning/) |
| Rotate a key or pull a bad release | [Operations](../../operations/signing-keys/) |
| Know what this documentation is missing | [Known gaps](../../reference/gaps/) |

## Status of this site

This site replaces the GitHub wiki as the single source of truth. It was migrated from the wiki
and checked against the code as it stands on `main`, so some details differ from the old wiki
where the wiki had drifted. Those corrections are called out where they matter.

Two things to be aware of when you read anything here:

- Pages that describe something a pending pull request will change carry an explicit note.
  Nothing is silently written as though unmerged work has landed.
- The architecture diagrams have been redrawn from the original whiteboard sketches. They are
  an interpretation, and the [known gaps](../../reference/gaps/) page records which parts were
  already marked unverified in the source material.

## Licence

The project is licensed under the **Apache 2.0 License**.
