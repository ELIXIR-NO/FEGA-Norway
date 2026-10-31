package cli

import "github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"

type inboxCommand struct {
	List   inboxListCommand   `command:"ls" description:"List the files in your inbox"`
	Remove inboxRemoveCommand `command:"rm" description:"Remove files from your inbox"`
}

func newInboxCommand(e *env) command {
	return command{
		name:  "inbox",
		short: "List or remove files in your inbox",
		long:  "Lists or removes the files you have uploaded and that are not yet ingested.",
		data:  &inboxCommand{List: inboxListCommand{env: e}, Remove: inboxRemoveCommand{env: e}},
	}
}

type inboxListCommand struct {
	env *env
	PageOptions
}

// Execute lists one page of the inbox, or all of it with --all.
func (c *inboxListCommand) Execute(args []string) error {
	return c.env.listFiles(args, true, c.PageOptions)
}

type inboxRemoveCommand struct {
	env  *env
	Args struct {
		Names []string `positional-arg-name:"NAME" required:"1"`
	} `positional-args:"yes"`
}

// Execute removes every named file from the inbox.
func (c *inboxRemoveCommand) Execute(args []string) error {
	if err := noArguments("rm", args); err != nil {
		return err
	}
	cfg, err := c.env.loadConfig(config.Token | config.CEGA)
	if err != nil {
		return err
	}
	return c.env.removeEach(c.Args.Names, c.env.proxyClient(cfg).DeleteFile)
}
