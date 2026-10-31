# lega

Command-line client for FEGA Norway: upload Crypt4GH-encrypted files to your
inbox and download the files exported to you. It replaces `lega-commander`
(`cli/old-lega`), which the e2e suite and the release keep using until the
switch.

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

Exit codes: 0 done (a skipped file counts as done), 1 error or Ctrl-C,
2 usage or a bad environment variable, 3 partial batch.

An interrupted upload resumes when the same command runs again;
`--no-resume` starts over. `--retries` is off by default: after a failed
chunk it asks TSD where the upload stands and never resends blindly.

```sh
go test ./...
go build -o build/lega . && ./build/lega --help
```
