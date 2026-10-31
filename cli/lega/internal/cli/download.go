package cli

import (
	"context"
	"fmt"
	"os"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/transfer"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

type downloadCommand struct {
	env     *env
	Output  string `short:"o" long:"output" default:"." value-name:"DIR" description:"Directory to save the files in"`
	Retries int    `long:"retries" default:"0" value-name:"N" description:"Retry a failed request up to N times, 0 to 5"`
	Args    struct {
		Names []string `positional-arg-name:"NAME"`
	} `positional-args:"yes"`
}

func newDownloadCommand(e *env) command {
	return command{
		name:  "download",
		short: "Download files from your outbox",
		long: `Downloads the named files from your outbox, or every file in it when no
name is given. A file that is already there with the same size is skipped.`,
		data: &downloadCommand{env: e},
	}
}

// Execute plans the download against the whole outbox and fetches every
// file it can; one failed file does not stop the others.
func (c *downloadCommand) Execute(args []string) error {
	if err := c.validate(args); err != nil {
		return err
	}
	cfg, err := c.env.loadConfig(config.Token)
	if err != nil {
		return err
	}
	client := c.env.proxyClient(cfg)
	runner := c.env.runner(cfg, c.Retries)
	outbox, err := listOutbox(c.env.ctx, runner, client)
	if err != nil {
		return err
	}
	if len(outbox) == 0 && len(c.Args.Names) == 0 {
		return ui.Infof(c.env.stdout, emptyMessage(false))
	}
	local, err := localSizes(c.Output)
	if err != nil {
		return err
	}
	steps := plan.PlanDownload(c.Args.Names, outbox, local, c.Output)
	return c.finish(runner.Download(c.env.ctx, client, steps))
}

func (c *downloadCommand) validate(args []string) error {
	if err := noArguments("download", args); err != nil {
		return err
	}
	if err := validateRetries(c.Retries); err != nil {
		return err
	}
	return checkDir(c.Output)
}

func checkDir(dir string) error {
	info, err := os.Stat(dir)
	if err != nil {
		return usageError{msg: fmt.Sprintf("--output: %v", err)}
	}
	if !info.IsDir() {
		return usageError{msg: fmt.Sprintf("--output: %s is not a directory", dir)}
	}
	return nil
}

func listOutbox(ctx context.Context, r transfer.Runner, client *proxy.Client) ([]plan.RemoteFile, error) {
	var files []plan.RemoteFile
	err := r.Retry(ctx, func(ctx context.Context) (err error) {
		files, err = client.ListAllFiles(ctx, false)
		return err
	})
	if err != nil {
		return nil, fmt.Errorf("list the outbox: %w", err)
	}
	return files, nil
}

// localSizes maps the regular files already in dir to their sizes.
func localSizes(dir string) (map[string]int64, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	sizes := make(map[string]int64, len(entries))
	for _, e := range entries {
		if info, err := e.Info(); err == nil && info.Mode().IsRegular() {
			sizes[e.Name()] = info.Size()
		}
	}
	return sizes, nil
}

func (c *downloadCommand) finish(rep transfer.Report) error {
	if err := c.env.renderBatch(downloadBatch(rep)); err != nil {
		return err
	}
	return interrupted(c.env.ctx, reportError(rep), "no partial file was left behind")
}

func downloadBatch(rep transfer.Report) ui.Batch {
	lines := make([]ui.Line, len(rep.Results))
	for i, res := range rep.Results {
		lines[i] = ui.Line{Outcome: res.Outcome.String(), Name: res.Name, Detail: downloadDetail(res)}
	}
	return ui.Batch{Lines: lines, Tallies: []ui.Tally{
		{Word: "downloaded", N: rep.Count(transfer.Downloaded)},
		{Word: "skipped", N: rep.Count(transfer.Skipped)},
		{Word: "failed", N: rep.Failed()},
	}}
}

func downloadDetail(res transfer.Result) string {
	switch res.Outcome {
	case transfer.Downloaded:
		return fmt.Sprintf("%s, to %s", ui.HumanSize(res.Size), res.Path)
	case transfer.Failed:
		return res.Err.Error()
	default:
		return res.Reason
	}
}
