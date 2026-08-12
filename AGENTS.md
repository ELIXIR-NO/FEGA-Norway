# AGENTS.md

Instructions for an AI coding agent working in this repository. Read this before editing
anything.

`CLAUDE.md` is a symlink to this file, so Claude Code, OpenCode and Cursor all read the same
content. Edit `AGENTS.md`; never replace the symlink with a copy.

Both are tracked on this branch but never reach the built site: Astro only emits
`src/content/docs/`, so nothing here lands in `dist/` or on the published pages.

## What this is

An **Astro + Starlight documentation site** for the FEGA Norway project. It is the **single
source of truth**, replacing the GitHub wiki, which is now superseded.

The site's job is to be *correct*. Every factual claim here is supposed to be checkable against
the source code of the systems it describes. A wrong page is worse than a missing one, because
people act on it.

## Where everything is

This repo is checked out alongside its siblings in a workspace directory. You will need the
neighbours to verify content, so clone them next to this one.

| Path | What it is |
| --- | --- |
| `fega-docs/` | **This repo.** The published documentation site. |
| `FEGA-Norway/` | The monorepo this documents: proxy, libraries, CLI, mq-interceptor, e2e suite. |
| `sensitive-data-archive/` | Upstream NeIC SDA: `ingest`, `verify`, `finalize`, `mapper`, Data-Out. |
| `tsd-file-api/` | UiO TSD file API, the real upload target. |
| `FEGA-Norway.wiki/` | Clone of the **old** wiki. Historical source only. Do not treat as truth. |
| `docs/` | Internal engineering records (migration logs, audits). **Not** this site, and not published. |

Do not confuse `docs/` with `fega-docs/`. `docs/` is the team's working record of past
workstreams; `fega-docs/` is the public documentation product.

### Where to verify claims

When a page makes a claim, check it against code, in this order of authority:

1. **`FEGA-Norway/e2e/`**: the end-to-end suite drives the real pipeline and asserts each stage.
   `internal/pipeline/pipeline.go` holds the authoritative stage order;
   `internal/stages/*.go` shows what each stage actually does.

   Caveat: `e2e/` exists only on the unmerged `feat/e2e-go-rewrite` branch (PR #833). On `main`
   the equivalent is the Java `e2eTests/`. Check which branch is out before citing a path, and
   read the other with `git show origin/main:<path>`.
2. **`sensitive-data-archive/sda/cmd/<service>/`**: the archive pipeline services.
3. **`sensitive-data-archive/sda-doa/`**: the Java Data-Out API (export and download).
4. **`FEGA-Norway/services/localega-tsd-proxy/`**: authentication, upload streaming, export requests.
5. **`FEGA-Norway/cli/lega-commander/`**: the client. Note it does **not** encrypt or decrypt.
6. **`FEGA-Norway/.github/workflows/`**: for anything about CI, versioning or releases.

## Running it

**Node 22.12 or newer is mandatory.** Astro's `engines` requires `>=22.12.0`, and `astro.mjs`
hard-exits with `Node.js v<x> is not supported by Astro!` below that. The default node on this
machine is **v18.20.8**, and there is no `.nvmrc` to catch it.

> The trap is the sequencing: **`npm install` succeeds** on Node 18, emitting only an
> `EBADENGINE` warning. The wall is hit at the first `astro` invocation, so the project looks
> installed and then "inexplicably" fails to build. Never respond by downgrading Astro.

```sh
source ~/.nvm/nvm.sh && nvm use 22          # or:
export PATH="$HOME/.nvm/versions/node/v22.19.0/bin:$PATH"
```

```sh
npm install
npm run dev      # http://localhost:4321/FEGA-Norway/
npm run build    # production build into dist/
npm run preview  # serve the built output
```

The `/FEGA-Norway/` path is not optional. GitHub Pages serves a project site under the repository
name, so `base` is set in `astro.config.mjs` and the dev server mirrors it.

## Editing content

Pages are Markdown in `src/content/docs/<section>/`, with `title` and `description` frontmatter.

> **Adding a page takes two steps.** The sidebar is a manual array in `astro.config.mjs`,
> deliberately, so ordering stays editorial rather than alphabetical. Creating the file alone will
> not make it appear in navigation. Add the `slug` entry too.

Sections: `start/`, `architecture/`, `local/`, `contributing/`, `operations/`, `reference/`.

Conventions the existing 22 pages all follow:

- Frontmatter is exactly `title` and `description`. Body starts at `##`; Starlight renders the
  title as the H1.
- **Internal links are relative with a trailing slash** (`../ingestion/`,
  `../../contributing/versioning/`). Never a leading slash: `/architecture/system/` resolves to
  the domain root and 404s under `base`. This bites hardest in `index.mdx`, whose hero actions and
  card `href`s are emitted verbatim and unprefixed.
- Asides use Starlight's `:::type[Title]` directive.
- Only `bash`, `mermaid` and `text` code fences appear.

The sidebar `label` and the page `title` are independent and deliberately differ in places.

## Diagrams

Diagrams are **Mermaid** in fenced ` ```mermaid ` blocks, rendered by `astro-mermaid`. Never
embed an external image: the wiki did that, and the URLs are outside the project's control.

- The palette is pinned centrally in `astro.config.mjs` under `themeVariables`. Do not style
  individual diagrams.
- **Never put a semicolon in a `Note` line.** Mermaid's sequence parser treats `;` as a statement
  separator and the diagram fails to render. Use a comma. This has bitten this repo already.
- Keep flowcharts vertical (`flowchart TB`) where possible. Wide `LR` charts scale down to
  unreadable inside the prose column.

After changing any diagram, **verify it renders**. A Mermaid syntax error produces an error box
on the page, not a build failure, so `npm run build` will pass with a broken diagram.

## Accuracy rules

These are the point of the site. Follow them literally.

1. **Verify before you assert.** If you cannot find it in code, do not state it as fact.
2. **Mark what you could not verify.** "Unverifiable from these repositories" is a good answer.
   `reference/gaps.md` exists to hold exactly this, along with the running correction table.
3. **Never silently fix a claim.** When a page contradicts what the code says, correct it *and*
   record the correction in `reference/gaps.md`. Readers need to know the docs changed under them.
4. **Flag pending changes rather than pre-writing them.** If an open pull request will change
   something, document the current state and add a callout naming the PR. Do not write the site
   as though unmerged work has landed.
5. **Do not ship empty headings.** If a topic has no content, list it in `reference/gaps.md`
   instead of leaving a hollow section.

## Configuration traps

Things that look harmless to change but are not:

| Setting | Why it is like that |
| --- | --- |
| `base: '/FEGA-Norway'` | Removing it 404s every asset on GitHub Pages. |
| `mermaid({ theme: 'base', autoTheme: false })` | See below. Do not flip either value. |
| `components.ThemeSelect` | Overrides Starlight's `<select>` with a segmented control. See below. |
| `sidebar` | Manual navigation. See above. |

### Why `autoTheme` stays off

The precise mechanism, because the naive reading ("it discards `themeVariables`") is wrong and a
future agent may flip it, see light mode look fine, and ship broken dark mode:

- `autoTheme: true` maps the page theme onto Mermaid's built-in `default`/`dark`. The ~33 keys
  named in `themeVariables` **do** survive. It is every key *not* named that changes.
- `base` is the only theme whose `updateColors()` derives unlisted variables from your overrides
  (each assignment is `||`-guarded). `default` and `dark` **hard-assign** from their own
  constants, so `nodeBorder` becomes stock lavender `#9370DB`, `clusterBorder` olive `#aaaa33`,
  and so on, leaking into anything unlisted.
- `theme: 'base'` also becomes dead config the moment `autoTheme` is true, because the theme map
  always wins.

There is a second consequence. The MutationObserver that re-renders diagrams on a theme toggle
**only exists when `autoTheme` is true**. With it off, diagrams render exactly once. That is why
the palette must be theme-neutral: nodes carry a light fill with dark text so a single rendering
is legible on both page backgrounds. Check any palette change in **both** themes in a browser.

### The theme switcher contract

`src/components/ThemeSelect.astro` must keep three things or theming breaks:

1. **The tag name `starlight-theme-select`.** Starlight's pre-paint script queries it. Rename it
   and the element never upgrades, so clicking does nothing.
2. **The localStorage key `starlight-theme`.** That pre-paint script is the entire no-flash
   mechanism and reads the key directly. Change it on one side only and every load flashes.
3. **Auto is stored as an empty string, not the literal `'auto'`.** This is the highest-risk line
   in the file and it looks like a bug. The empty string is *falsy*, which is what makes auto fall
   through to the system preference. "Tidying" it to `'auto'` makes it truthy, and Starlight then
   resolves anything truthy that is not `'light'` to **dark**, so every auto-mode user on a light
   system gets a dark flash.
4. **The `<script is:inline>` calling `updatePickers()` must stay inline.** It depends on a global
   defined by Starlight's inline script and must run before the module script.

Omitting the `<select>` is safe: `updatePickers()` guards on finding one and no-ops.

The thumb transition stays disabled until the stored theme is applied. Remove that guard and the
indicator visibly slides in from the wrong segment on every page load.

## Styling

Fonts are self-hosted through `@fontsource-variable`, **never a CDN**. This site documents a
sensitive-data platform and may be read from inside TSD or offline, so it must not depend on a
third-party request, and must not make readers' browsers call out to Google. A CDN swap looks
identical locally and breaks silently there. Verify after any font change:

```sh
grep -rlE "googleapis|gstatic" dist/_astro/*.css   # must return nothing
```

Three more things in `src/styles/theme.css` that are load-bearing:

- **The landing grid is a hardcoded 2x2** because there are exactly four cards. It was chosen over
  `auto-fit`, which wraps to 3+1 and orphans the last one. A fifth card breaks the assumption.
- **`astro-mermaid` injects its own stylesheet at runtime**, appended to `document.head`, so it
  wins ties against the bundled CSS on `pre.mermaid` margin, padding, overflow and background. If
  you edit the `.mermaid` rules and see no effect, that is why. The file is loading.
- **The accent `#1f7a8c` is duplicated by hand** in `theme.css` and in the mermaid
  `themeVariables` in `astro.config.mjs`. There is no shared token, so a palette change means
  editing both.

## Deployment

This tree is the working copy of `gh-pages` on `ELIXIR-NO/FEGA-Norway`, and pushing there triggers
`.github/workflows/deploy.yml`, which builds and publishes to
<https://elixir-no.github.io/FEGA-Norway/>.

The branch holds **this source**, not generated HTML, which is the inverse of the usual
convention. That requires the repository's Pages source to be set to **GitHub Actions**
(Settings → Pages → Build and deployment → Source). With "Deploy from a branch", GitHub also runs
its legacy Jekyll build over this tree, which fails on the `.astro` files' frontmatter and leaves a
red `pages-build-deployment` run beside every green `Deploy docs` one.

If the branch ever moves, `deploy.yml` and `editLink.baseUrl` in `astro.config.mjs` must move
together; the latter hardcodes `/edit/gh-pages/`.

**There is no manual re-trigger.** `deploy.yml` declares `workflow_dispatch`, but GitHub only
offers the Run-workflow button for workflows that exist on the repository's *default* branch,
which is `main`. While the file lives only on `gh-pages`, the declaration does nothing, so
republishing means pushing another commit or hitting **Re-run jobs** on a past run in the Actions
UI. It becomes live if `deploy.yml` ever also lands on `main`.

`lastUpdated: true` shells out to `git log` for each page at build time. In CI it is accurate only
because the checkout step sets
`fetch-depth: 0`: under checkout's default shallow fetch the clone holds a single commit, so
every page would carry that same date. Do not remove that line.

## House rules

On a workspace checkout that carries a `../CLAUDE.md`, Claude Code inherits those rules
automatically. Cursor and OpenCode read this file literally and get no such inheritance, and a
standalone clone has no parent file at all, so the essentials are restated here.

- **No em or en dashes** (`—`, `–`) anywhere: prose, code comments, commit messages. Use a comma,
  colon, parentheses, or split the sentence. Hyphens and arrows are fine.
- **No AI attribution, ever.** No `Co-Authored-By: Claude`, no "Generated with" footer, no mention
  of Claude, AI or an assistant in any commit, PR, or pushed artifact.
- **Conventional Commits** for every commit and PR title: `<type>(<scope>): <description>`,
  imperative, lowercase, no trailing period. The body explains *why*.
- **No git writes without asking.** Do not commit, push, force-push, open or edit PRs without
  explicit approval. This workspace has upstream work in flight.
- **Comments explain why, not what.** Applies to the CSS and Astro config here too.

## Verify before claiming done

```sh
npm run build                       # must be clean
grep -rlE "googleapis|gstatic" dist/_astro/*.css   # must be empty
```

Then load the affected pages and confirm any diagram you touched actually rendered. Build success
does not prove a diagram is valid.
