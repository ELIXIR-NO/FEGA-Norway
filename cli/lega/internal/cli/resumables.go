package cli

import (
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

type resumablesCommand struct {
	List   resumablesListCommand   `command:"ls" description:"List your interrupted uploads"`
	Remove resumablesRemoveCommand `command:"rm" description:"Discard interrupted uploads"`
}

func newResumablesCommand(e *env) command {
	return command{
		name:  "resumables",
		short: "List or remove interrupted uploads",
		long: `Lists or discards uploads that were interrupted. Running the same upload
command again resumes one; discard it to start that file over.`,
		data: &resumablesCommand{List: resumablesListCommand{env: e}, Remove: resumablesRemoveCommand{env: e}},
	}
}

type resumablesListCommand struct{ env *env }

// Execute lists every interrupted upload with the bytes already received.
func (c *resumablesListCommand) Execute(args []string) error {
	if err := noArguments("ls", args); err != nil {
		return err
	}
	cfg, err := c.env.loadConfig(config.Token | config.CEGA)
	if err != nil {
		return err
	}
	rs, err := c.env.proxyClient(cfg).ListResumables(c.env.ctx)
	if err != nil {
		return err
	}
	return c.env.renderResumables(uiResumables(rs))
}

func (e *env) renderResumables(rs []ui.Resumable) error {
	if e.json {
		return e.renderJSON(rs)
	}
	return e.styles.Resumables(e.stdout, rs, "You have no interrupted uploads.")
}

func uiResumables(rs []plan.Resumable) []ui.Resumable {
	out := make([]ui.Resumable, len(rs))
	for i, r := range rs {
		out[i] = ui.Resumable{ID: r.ID, Name: r.Name, Received: r.NextOffset}
	}
	return out
}

type resumablesRemoveCommand struct {
	env  *env
	Args struct {
		IDs []string `positional-arg-name:"ID" required:"1"`
	} `positional-args:"yes"`
}

// Execute discards every named interrupted upload.
func (c *resumablesRemoveCommand) Execute(args []string) error {
	if err := noArguments("rm", args); err != nil {
		return err
	}
	cfg, err := c.env.loadConfig(config.Token | config.CEGA)
	if err != nil {
		return err
	}
	return c.env.removeEach(c.Args.IDs, c.env.proxyClient(cfg).DeleteResumable)
}
