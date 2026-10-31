# lega

Command-line client for FEGA Norway: upload Crypt4GH-encrypted files to your
inbox and download the files exported to you. It replaces `lega-commander`
(`cli/lega-commander`), which the e2e suite and the release keep using until
the switch.

```
lega [--json] <command>

  inbox ls        [--all | --page N] [-p, --per-page N]
  inbox rm        NAME...
  outbox ls       [--all | --page N] [-p, --per-page N]
  upload          PATH... [--direct] [--force] [--no-resume] [--retries N]
  download        [NAME...] [-o, --output DIR] [--retries N]
  resumables ls
  resumables rm   ID...
  version
```

`lega --help` prints this with the environment table; `lega <command> --help`
prints a command's own options.

## Environment

The variable names are the ones `lega-commander` used.

| Variable | Needed by | Default |
|---|---|---|
| `ELIXIR_AAI_TOKEN` | every command | |
| `CENTRAL_EGA_USERNAME`, `CENTRAL_EGA_PASSWORD` | `inbox`, `resumables`, `upload` | |
| `LOCAL_EGA_INSTANCE_URL` | every command | `https://ega.elixir.no` |
| `TSD_PROXY_URL`, `TSD_PROJ_NAME` | `upload --direct` | `https://ega.elixir.no`, `p969` |
| `LEGA_COMMANDER_CHUNK_SIZE` | `upload`, MiB from 1 to 256 | `50` |
| `LEGA_COMMANDER_TLS_SKIP_VERIFY` | `true` disables TLS verification, for test stacks only | |

Every missing or invalid variable is reported in one message, with its name
and never its value. A URL must be absolute `http` or `https`; a plain
`http` one gets a warning that credentials travel in clear.

## Exit codes

0 done (a skipped file counts as done), 1 error or Ctrl-C, 2 usage or a bad
environment variable, 3 partial batch (some files done, some failed).

Tables, batch summaries and JSON go to stdout. Progress, warnings and the
error line (`lega: ...`) go to stderr, so a script can read stdout alone.
`--json` replaces every table and summary with one JSON document; it goes
before or after the command. Colour is used only on a terminal and never when
`NO_COLOR` is set.

## Upload

Every file is checked before the first byte is sent: it must be Crypt4GH, two
files in the batch may not share a base name (the inbox is flat), and a file
already in the inbox is skipped. One rejected file stops the whole batch,
with the reason per file. A folder is walked recursively; a symlink to a
folder met inside the walk is not followed, and says so.

An interrupted upload resumes when the same command runs again. Before it
does, the bytes TSD holds are checked against the local file (size and the
md5 of the last chunk), so a different file with the same name is refused
rather than spliced. `--no-resume` starts over. `--force` skips the inbox
check and lets TSD overwrite.

`--retries N` (0 to 5, default 0) retries a chunk after a 429, 502, 503, 504,
a stall or a transport error, never after a 500. After a failed chunk it asks
TSD where the upload stands and never resends blindly.

`--direct` sends the chunks to TSD itself instead of through the proxy, as
before.

## Download

With no names the whole outbox is fetched. Files go to `--output` (default
`.`), written to a temporary name and renamed only when every byte has
arrived, so a failed or interrupted download leaves nothing behind. Files are
created mode 0600. A local file with the outbox file's name and size is
skipped as already downloaded, unless the outbox copy was exported after it;
then, like a file of another size, it is left alone and the step is rejected.

## Coming from lega-commander

| lega-commander | lega |
|---|---|
| `inbox -l`, `inbox -d NAME` | `inbox ls`, `inbox rm NAME...` |
| `outbox -l` | `outbox ls` |
| `resumables -l`, `resumables -d ID` | `resumables ls`, `resumables rm ID...` |
| `upload -f FILE` | `upload PATH...` |
| `upload -r` | on by default; `--no-resume` turns it off |
| `upload --no-duplicate-check` | `upload --force` |
| `upload --direct` (`-b` in older releases) | `upload --direct` |
| `download -f NAME` | `download NAME...` |
| `download --direct` | gone; it never used the token it fetched |
| `-p`, `--page`, `--all` | unchanged |

Other differences a script may notice: a single file already in the inbox is
skipped with exit 0 where it was an error; `--all` always lists from the
first page; `--page 0` and `--per-page 0` are refused; listing tables have
new column names and human-readable sizes, so parse `--json` instead; the
resumables table's `File size` column is now `RECEIVED`, the bytes TSD
holds; errors are not timestamped.

## Development

```sh
go test ./...
go build -o build/lega . && ./build/lega --help
```
