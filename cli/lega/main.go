// Command lega uploads Crypt4GH-encrypted files to FEGA Norway and downloads
// the files exported to the user.
package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/cli"
)

// GoReleaser's default ldflags set these.
var (
	version = "dev"
	commit  = "none"
	date    = "unknown"
)

func main() {
	cli.Build = cli.BuildInfo{Version: version, Commit: commit, Date: date}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	code := cli.Run(ctx, os.Args[1:], os.Getenv, os.Stdout, os.Stderr)
	stop()
	os.Exit(code)
}
