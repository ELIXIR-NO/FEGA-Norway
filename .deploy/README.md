# .deploy

The files here are the desired state of the environments that deploy from this
repository. **Merging a change to this directory into `main` is a deploy.**

| File | Deploys |
|------|---------|
| `fega-egadev.yml` | the staging stack on `egadev.uio.no`: the proxy, the mq-interceptor and their postgres |
| `fega-egadev-e2e.yml` | nothing lasting: a preview of that stack with the end-to-end tests run against it, see below |

## How it works

bifrost, the deployment system in the private `ELIXIR-NO/bifrost` repository,
runs on the egadev VM and reads this directory on `main` once a minute, as a
GitHub App installed on this repository.
When a `<target>.yml` differs from what the target last deployed, it pins every
image to a digest, brings the new stack up, checks that it is healthy and puts
the previous stack back if it is not. The result shows up as the commit status
`bifrost/<target>` on the commit that changed the file, which for a merged
pull request is its head commit, and as a comment on that pull request.

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

## Testing a change before it merges

`fega-egadev-e2e.yml` is the preview of staging: the same three services, a
mock Central EGA broker beside them, and the e2e runner from `e2e/` run once
against the lot. A pull request that changes this file gets a preview for each
commit pushed to it. bifrost takes staging down, brings the preview up, runs
the tests and puts staging back, whatever the outcome. Expect staging to be
away for two to three minutes.

The result comes back as the commit status `bifrost/fega-egadev/preview` on the
head commit and as one comment on the pull request, which is updated when a
later commit is previewed.

To test a new image, bump its tag in both files in the same pull request: the
preview shows it passing, the merge deploys it. A pull request that edits only
`fega-egadev.yml` gets no preview. Merging a change to the e2e file deploys
nothing by itself.

Two things can refuse a preview before staging is touched. The runner logs in
with a real LS-AAI token that lives on the host and is renewed by hand; when
it has expired, the status says so, and the next commit to the pull request is
the retry once the token is fresh. A pull request from a fork is never
previewed.

## If a deploy fails

The commit status turns red and carries the reason. Staging is back on the
previous stack at that point, and stays there: the failed file is not tried
again until it changes. Fix it with a new pull request.
