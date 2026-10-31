// Package cli is the lega command layer: it parses flags, loads the
// configuration each command needs, calls the core packages and hands every
// result to the renderer. It is the only package that writes to the
// terminal, and it does so only through the writers Run receives.
package cli

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/jessevdk/go-flags"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

// Exit codes returned by Run.
const (
	ExitOK      = 0 // everything asked for was done; skipped files count as done
	ExitError   = 1 // a request or a file failed, upload refused to start, or Ctrl-C
	ExitUsage   = 2 // bad command, flag or environment variable
	ExitPartial = 3 // a batch where some files succeeded and some failed
)

const description = `Upload Crypt4GH-encrypted files to FEGA Norway and download the files
exported to you.`

// environmentHelp follows the generated root help, since go-flags only knows
// about flags and the commands read most of their settings from the
// environment.
const environmentHelp = `
Environment:
  ELIXIR_AAI_TOKEN                every command
  CENTRAL_EGA_USERNAME, _PASSWORD inbox, resumables, upload
  LOCAL_EGA_INSTANCE_URL          proxy address (default https://ega.elixir.no)
  TSD_PROXY_URL, TSD_PROJ_NAME    upload --direct (defaults https://ega.elixir.no, p969)
  LEGA_COMMANDER_CHUNK_SIZE       upload chunk size in MiB, 1 to 256 (default 50)
  LEGA_COMMANDER_TLS_SKIP_VERIFY  "true" disables TLS verification, for test stacks only

Run 'lega <command> --help' for a command's own options.
`

// env is what every command gets from the process. Commands reach the
// outside world only through it, which is what lets Run be tested end to end.
// styles carry the colour decision for stdout; progressTTY says whether
// stderr is a terminal, which is where progress goes.
type env struct {
	ctx         context.Context
	getenv      func(string) string
	stdout      io.Writer
	stderr      io.Writer
	json        bool
	styles      ui.Styles
	progressTTY bool
}

func newEnv(ctx context.Context, getenv func(string) string, stdout, stderr io.Writer) *env {
	color := isTerminal(stdout) && !ui.NoColor(getenv)
	return &env{
		ctx:         ctx,
		getenv:      getenv,
		stdout:      stdout,
		stderr:      stderr,
		styles:      ui.NewStyles(color),
		progressTTY: isTerminal(stderr),
	}
}

// isTerminal treats any writer that is not a file, such as a test buffer,
// as not a terminal.
func isTerminal(w io.Writer) bool {
	f, ok := w.(*os.File)
	return ok && ui.IsTTY(f)
}

// rootOptions are the flags accepted before the command name.
type rootOptions struct {
	JSON    bool `long:"json" description:"Print JSON on stdout instead of tables"`
	Version bool `short:"v" long:"version" description:"Print the version and exit"`
}

// command is one top-level subcommand. data is the go-flags command struct;
// it implements flags.Commander, or holds nested subcommands that do.
type command struct {
	name, short, long string
	data              any
}

// commands lists every top-level subcommand. go-flags sorts them for the
// help screen, so the order here does not matter.
func commands(e *env) []command {
	return []command{
		newDownloadCommand(e),
		newInboxCommand(e),
		newOutboxCommand(e),
		newResumablesCommand(e),
		newUploadCommand(e),
		newVersionCommand(e),
	}
}

// usageError marks an error the user fixes on the command line or in the
// environment, which exits with ExitUsage instead of ExitError.
type usageError struct{ msg string }

func (u usageError) Error() string { return u.msg }

// Run parses args, runs the command they name and returns the exit code.
func Run(ctx context.Context, args []string, getenv func(string) string, stdout, stderr io.Writer) int {
	e := newEnv(ctx, getenv, stdout, stderr)
	opts := &rootOptions{}
	p, err := newParser(e, opts)
	if err != nil {
		return e.fail(err)
	}
	_, err = p.ParseArgs(args)
	if opts.Version && isCommandRequired(err) {
		return e.fail(e.writeVersion())
	}
	if text, ok := helpText(err); ok {
		fmt.Fprint(e.stdout, withEnvironmentHelp(text, p.Active == nil))
		return ExitOK
	}
	return e.fail(err)
}

// newParser builds the go-flags tree. flags.Default is avoided on purpose:
// its PrintErrors would write to os.Stderr behind Run's back.
func newParser(e *env, opts *rootOptions) (*flags.Parser, error) {
	p := flags.NewNamedParser("lega", flags.HelpFlag|flags.PassDoubleDash)
	p.LongDescription = description
	if _, err := p.AddGroup("Application Options", "", opts); err != nil {
		return nil, err
	}
	for _, c := range commands(e) {
		if _, err := p.AddCommand(c.name, c.short, c.long, c.data); err != nil {
			return nil, err
		}
	}
	p.CommandHandler = func(cmd flags.Commander, args []string) error {
		e.json = opts.JSON
		if opts.Version {
			return e.writeVersion()
		}
		return cmd.Execute(args)
	}
	return p, nil
}

// fail reports err on stderr, if there is one, and returns its exit code.
func (e *env) fail(err error) int {
	if err == nil {
		return ExitOK
	}
	ui.Errorf(e.stderr, "%s", err)
	return exitCode(err)
}

// exitCode maps an error from parsing or from a command to the exit code
// table in the help screen.
func exitCode(err error) int {
	var ferr *flags.Error
	var uerr usageError
	var cerr *config.Error
	var berr batchError
	switch {
	case err == nil:
		return ExitOK
	case errors.As(err, &ferr), errors.As(err, &uerr), errors.As(err, &cerr):
		return ExitUsage
	case errors.As(err, &berr) && berr.partial():
		return ExitPartial
	default:
		return ExitError
	}
}

// helpText returns the help screen go-flags put in err, if err is a help
// request.
func helpText(err error) (string, bool) {
	var ferr *flags.Error
	if errors.As(err, &ferr) && ferr.Type == flags.ErrHelp {
		return ferr.Message, true
	}
	return "", false
}

// withEnvironmentHelp appends the environment table to the root help screen
// only; a command's own help stays about its flags.
func withEnvironmentHelp(help string, root bool) string {
	help = strings.TrimRight(help, "\n") + "\n"
	if !root {
		return help
	}
	return help + environmentHelp
}

func isCommandRequired(err error) bool {
	var ferr *flags.Error
	return errors.As(err, &ferr) && ferr.Type == flags.ErrCommandRequired
}
