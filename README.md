# FEGA Norway documentation

The engineering documentation for [ELIXIR-NO/FEGA-Norway](https://github.com/ELIXIR-NO/FEGA-Norway):
architecture, local development, contribution rules and operational runbooks.

Built with [Astro](https://astro.build) and [Starlight](https://starlight.astro.build). This
replaces the project's GitHub wiki as the single source of truth.

> This is the `gh-pages` branch, and it shares no history with `main`. It holds only the
> documentation site, which is why the tree looks nothing like the monorepo. Code changes belong on
> `main`; nothing here is built or released with the project.

## Quick start

Requires **Node 22.12 or newer**.

```sh
npm install
npm run dev
```

Then open <http://localhost:4321/FEGA-Norway/> (the path matters: GitHub Pages serves project
sites under the repository name, and the dev server mirrors that).

```sh
npm run build    # production build into dist/
npm run preview  # serve the built output
```

## What is in here

```
src/content/docs/
  start/         what the project is, and the component map
  architecture/  the system overview and its five main flows
  local/         prerequisites, the dev.sh workflow, troubleshooting
  contributing/  commits, versioning and releases, CI, the build system
  operations/    signing keys, retiring broken releases, the public web pages
  reference/     team links, and an honest list of what these docs are missing
```

Start at **Architecture → System overview** if you are new to the project. It is the only page
that shows how FEGA Norway, the NeIC Sensitive Data Archive and UiO TSD fit together.

## Contributing

Pages are Markdown with `title` and `description` frontmatter. Two things catch people out:

**Navigation is manual.** Adding a file does not add it to the sidebar; add a matching entry to
the `sidebar` array in `astro.config.mjs`. This is deliberate, so page order stays editorial.

**Diagrams are [Mermaid](https://mermaid.js.org)**, written inline in fenced ` ```mermaid ` blocks
and rendered in the browser. After changing one, load the page and check it rendered: a syntax
error shows an error box rather than failing the build.

`AGENTS.md` carries the full conventions, including the configuration decisions that look
harmless to change but are not. Worth reading before a non-trivial change, whether or not you are
an AI agent.

## Accuracy

This documentation is meant to be checkable, not merely plausible. Content was migrated from the
wiki and then verified against the source code of the systems it describes. Where the two
disagreed, the code won.

Corrections are recorded rather than applied silently: **Reference → Known gaps** carries a table
of every place the documentation now differs from what was previously written, plus the topics
that are missing or could not be verified from the available repositories.

If you find a page that contradicts the code, the page is the bug.

## Deployment

The site is published at **<https://elixir-no.github.io/FEGA-Norway/>**. Pushing to `gh-pages`
builds and deploys it through GitHub Actions (`.github/workflows/deploy.yml`).

The branch holds this source rather than generated HTML, which is the inverse of the usual
`gh-pages` convention, so the repository's Pages source must be set to **GitHub Actions** under
Settings → Pages. Under "Deploy from a branch", GitHub additionally runs its legacy Jekyll build
over this tree, which fails on the `.astro` frontmatter and leaves a red `pages-build-deployment`
run beside every green `Deploy docs` one.

## Working on this with an AI agent

See [`AGENTS.md`](AGENTS.md), which carries the full working instructions: where the neighbouring
repositories are, how to verify a claim against code, the configuration decisions that look
harmless to change but are not, and the accuracy rules. `CLAUDE.md` is a symlink to it, so Claude
Code, OpenCode and Cursor all read the same file.
