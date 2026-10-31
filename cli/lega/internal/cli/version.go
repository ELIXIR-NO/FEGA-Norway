package cli

import (
	"fmt"
)

// BuildInfo is the identity of this binary.
type BuildInfo struct {
	Version, Commit, Date string
}

// Build is what lega version prints. main sets it from the variables
// GoReleaser fills at link time; a plain go build keeps these defaults.
var Build = BuildInfo{Version: "dev", Commit: "none", Date: "unknown"}

type versionCommand struct{ env *env }

func newVersionCommand(e *env) command {
	return command{
		name:  "version",
		short: "Print version information",
		long:  "Prints the version, commit and build date of this lega binary.",
		data:  &versionCommand{env: e},
	}
}

// Execute prints the build identity.
func (c *versionCommand) Execute(args []string) error {
	if len(args) > 0 {
		return usageError{msg: "version takes no arguments"}
	}
	return c.env.writeVersion()
}

func (e *env) writeVersion() error {
	_, err := fmt.Fprintln(e.stdout, versionLine(e.styles.Header.Render("lega"), Build))
	return err
}

func versionLine(name string, b BuildInfo) string {
	return fmt.Sprintf("%s %s (commit %s, built %s)", name, b.Version, b.Commit, b.Date)
}
