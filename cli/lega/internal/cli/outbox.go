package cli

type outboxCommand struct {
	List outboxListCommand `command:"ls" description:"List the files exported to you"`
}

func newOutboxCommand(e *env) command {
	return command{
		name:  "outbox",
		short: "List the files exported to you",
		long:  "Lists the files exported to you, which lega download fetches.",
		data:  &outboxCommand{List: outboxListCommand{env: e}},
	}
}

type outboxListCommand struct {
	env *env
	PageOptions
}

// Execute lists one page of the outbox, or all of it with --all.
func (c *outboxListCommand) Execute(args []string) error {
	return c.env.listFiles(args, false, c.PageOptions)
}
