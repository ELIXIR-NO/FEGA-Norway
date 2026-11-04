---
title: Standing up a new environment
description: The requests a FEGA node makes to UiO TSD and Life Science AAI before it can run, in order and with who owns each.
---

Most of standing up a FEGA Norway environment is not code. It is a series of requests to UiO TSD
and to Life Science AAI, made once per environment and then forgotten until the next one. This
page lists them in the order they have to happen, for anyone setting up a new environment or
another node building on TSD. The result is the shape in the
[system overview](../../architecture/system/): the archive inside a TSD project, the proxy and
the MQ interceptor on a host outside it.

## The requests, in order

Each step needs the one before it.

| # | Step | Who does it | What we provide | What we get back |
| --- | --- | --- | --- | --- |
| 1 | A TSD project | TSD | The purpose and the team members | The project: the secure boundary everything else sits inside |
| 2 | A VM inside the project, with the software the stack needs | TSD staff provision it and install the software | The list: a container runtime, a PostgreSQL client and the `crypt4gh` tool | A VM to run the archive on. We have no root on it, so installing anything later is another request |
| 3 | Files API access for the project, with the `elixir` authentication mode | TSD | The project, and that it needs `elixir` authentication | An access key, which the proxy reads as `TSD_ACCESS_KEY`. TSD staff also enable the project for the FEGA flows on their side |
| 4 | A host outside TSD for the proxy and the MQ interceptor, allowed through TSD's firewall to the message broker | The node runs the host; TSD staff open their firewall to it | The host's address | The proxy and interceptor can reach the broker they publish to, `external RMQ` in the [system overview](../../architecture/system/) |
| 5 | RabbitMQ and PostgreSQL for the project | TSD staff, as TSD-provided services they configure | Our broker definitions and the database schema | A broker and a database the archive connects to. Only TSD staff can change their configuration |
| 6 | An OIDC client for the proxy | Life Science AAI | The proxy's redirect URI, by default `/oidc-protected` under its base URL | A client id and secret (`CLIENT_ID`, `CLIENT_SECRET`). The proxy requests the scopes `openid` and `ga4gh_passport_v1` |
| 7 | The Crypt4GH archive key (the master key) | The node, inside the project | A passphrase, chosen and kept by the node | The key pair every archived file is encrypted to. The private half never leaves the project; the public half is published so submitters can encrypt to it |
| 8 | The service images inside the project | The node, through TSD's import route | Image archives, pulled from GHCR and saved outside TSD | Images loaded on the VM, which has no internet access and cannot pull them itself |

:::danger[Losing the archive key loses the archive]
Every archived file is encrypted to the archive key. If its private half or its passphrase is
lost, every archived file is lost with it, and nothing can recover them. Generate it inside the
project so the private half never exists outside the secure boundary.
:::

:::caution[Unverified since 2024]
The order and the owners come from the old wiki, last edited in 2024, checked against the code
where code can show them: the `elixir` mode, the access key and the OIDC settings. Two parts
nobody has walked since. One is the import route that brings images and files into a project.
The other is exactly who owns and runs the broker and the database, including how the stock SDA
schema reaches a TSD-provided database. Confirm both with TSD before you rely on them.
:::

Where each secret lives once the environment runs is still an open gap: see
[known gaps](../../reference/gaps/).
