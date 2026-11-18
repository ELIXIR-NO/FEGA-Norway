# .deploy

The files here are the desired state of the environments that deploy from this
repository. **Merging a change to this directory into `main` is a deploy.**

| File | Deploys |
|------|---------|
| `fega-egadev.yml` | the staging stack on `egadev.uio.no`: the proxy, the mq-interceptor and their postgres |

## How it works

bifrost, the deployment system in the private `ELIXIR-NO/bifrost` repository,
runs on the egadev VM and reads this directory on `main` once a minute, as a
GitHub App installed on this repository.
When a `<target>.yml` differs from what the target last deployed, it pins every
image to a digest, brings the new stack up, checks that it is healthy and puts
the previous stack back if it is not. The result shows up as the commit status
`bifrost/<target>` on the merge commit.

What is compared is the content of the file, so a merge that leaves it
untouched deploys nothing, and so does a restart of bifrost.

## Changing what staging runs

Open a pull request that edits `fega-egadev.yml`. The usual change is one line
under `images:`, for instance a newer `localega-tsd-proxy` tag. The image has
to exist before the merge: a tag that the merge's own release job has not
pushed yet is tried three times in all, five minutes apart, and then fails.

A file has two parts. `images` names every image the stack may run, by
variable; `compose` is the compose file, in which each service gives its image
as one of those variables and nothing else. `${STACK_PATH}` and
`${SHARED_PATH}` are directories on the host that bifrost fills in. Secrets are
not in this file and must never be: they live in the env files on the host
that `env_file` points at.

A deploy made by hand on the VM is replaced on the next poll, since `main` is
the desired state. To pin staging to something else for a while, change the
file here.

## If a deploy fails

The commit status turns red and carries the reason. Staging is back on the
previous stack at that point, and stays there: the failed file is not tried
again until it changes. Fix it with a new pull request.
